package me.rerere.mediagen.provider.providers.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.common.http.await
import me.rerere.mediagen.model.ImageRole
import me.rerere.mediagen.model.MediaGenerationError
import me.rerere.mediagen.model.MediaGenerationInput
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaGenerationOutput
import me.rerere.mediagen.model.MediaGenerationRequest
import me.rerere.mediagen.model.MediaGenerationRequestPreview
import me.rerere.mediagen.model.MediaGenerationStatus
import me.rerere.mediagen.model.MediaGenerationTask
import me.rerere.mediagen.model.MediaGenerationUsage
import me.rerere.mediagen.provider.MediaGenerationProvider
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.mediagen.provider.providers.executeJson
import me.rerere.mediagen.provider.providers.isPreviewPlaceholder
import me.rerere.mediagen.provider.providers.long
import me.rerere.mediagen.provider.providers.obj
import me.rerere.mediagen.provider.providers.postJson
import me.rerere.mediagen.provider.providers.string
import me.rerere.mediagen.provider.providers.toUrlOrDataUri
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/** Gemini Developer API and compatible gateways, using generateContent rather than OpenAI images. */
class GoogleMediaGenerationProvider(private val client: OkHttpClient) :
    MediaGenerationProvider<MediaGenerationProviderSetting.Google> {
    override val id: String = "google"

    override suspend fun create(
        setting: MediaGenerationProviderSetting.Google,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationTask> = runCatching {
        // Gemini takes inline image data. Resolve remote references without forwarding the API key.
        val resolved = withContext(Dispatchers.IO) {
            request.copy(inputs = request.inputs.map { input ->
                if (input is MediaGenerationInput.Image &&
                    (input.url.startsWith("https://") || input.url.startsWith("http://"))) {
                    client.newCall(Request.Builder().url(input.url).build()).await().use { response ->
                        check(response.isSuccessful) { "Failed to load Gemini reference image (HTTP ${response.code})" }
                        val mime = response.body.contentType()?.toString()?.substringBefore(';') ?: "image/png"
                        require(mime.startsWith("image/")) { "Gemini reference must be an image" }
                        input.copy(url = "data:$mime;base64,${Base64.encode(response.body.bytes())}")
                    }
                } else input
            })
        }
        val body = withContext(Dispatchers.IO) { buildBody(resolved) }
        val builder = Request.Builder().url(endpoint(setting, model))
        setting.customHeaders.forEach { (name, value) -> builder.header(name, value) }
        builder.header("x-goog-api-key", setting.apiKey)
        parseTask(client.executeJson(builder.postJson(body).build(), id), model.modelId)
    }

    override fun preview(
        setting: MediaGenerationProviderSetting.Google,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): MediaGenerationRequestPreview =
        MediaGenerationRequestPreview("POST", endpoint(setting, model), buildBody(request))

    private fun endpoint(setting: MediaGenerationProviderSetting.Google, model: MediaGenerationModel): String =
        setting.baseUrl.trim().trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegment("models")
            .addPathSegment("${model.modelId.removePrefix("models/")}:generateContent")
            .build().toString()

    internal fun buildBody(request: MediaGenerationRequest): JsonObject {
        validate(request)
        val parts = buildJsonArray {
            add(buildJsonObject { put("text", request.prompt) })
            request.inputs.forEach { input ->
                require(input is MediaGenerationInput.Image && input.role == ImageRole.REFERENCE) {
                    "Gemini image generation accepts reference images only"
                }
                val uri = input.toUrlOrDataUri()
                add(buildJsonObject {
                    if (uri.isPreviewPlaceholder()) {
                        put("inlineData", buildJsonObject { put("mimeType", "image/png"); put("data", uri) })
                    } else {
                        require(uri.startsWith("data:image/") && ";base64," in uri) {
                            "Gemini image references must contain base64 image data"
                        }
                        put("inlineData", buildJsonObject {
                            put("mimeType", uri.substringAfter("data:").substringBefore(';'))
                            put("data", uri.substringAfter(";base64,"))
                        })
                    }
                })
            }
        }
        val extraConfig = request.extraParameters.obj("generationConfig") ?: JsonObject(emptyMap())
        val config = buildJsonObject {
            extraConfig.forEach { (key, value) -> put(key, value) }
            put("responseModalities", JsonArray(listOf(JsonPrimitive("TEXT"), JsonPrimitive("IMAGE"))))
            val imageConfig = buildJsonObject {
                extraConfig.obj("imageConfig")?.forEach { (key, value) -> put(key, value) }
                request.resolution?.let { put("imageSize", it) }
                request.aspectRatio?.let { put("aspectRatio", it) }
            }
            if (imageConfig.isNotEmpty()) put("imageConfig", imageConfig)
        }
        return buildJsonObject {
            request.extraParameters.forEach { (key, value) -> put(key, value) }
            put("contents", buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("parts", parts) })
            })
            put("generationConfig", config)
        }
    }

    internal fun parseTask(root: JsonObject, modelId: String): MediaGenerationTask {
        val candidates = (root["candidates"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val outputs = candidates.flatMap { candidate ->
            (candidate.obj("content")?.get("parts") as? JsonArray).orEmpty()
                .filterIsInstance<JsonObject>().mapNotNull { part ->
                    if ((part["thought"] as? JsonPrimitive)?.content == "true") return@mapNotNull null
                    val inline = part.obj("inlineData") ?: part.obj("inline_data") ?: return@mapNotNull null
                    val data = inline.string("data")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val mime = inline.string("mimeType") ?: inline.string("mime_type") ?: "image/png"
                    if (!mime.startsWith("image/")) return@mapNotNull null
                    MediaGenerationOutput(data = Base64.decode(data), mimeType = mime)
                }
        }
        val reason = root.obj("promptFeedback")?.string("blockReason")
            ?: candidates.firstOrNull()?.string("finishReason")
        return MediaGenerationTask(
            id = root.string("responseId") ?: Uuid.random().toString(),
            provider = id, model = modelId,
            status = if (outputs.isEmpty()) MediaGenerationStatus.FAILED else MediaGenerationStatus.SUCCEEDED,
            outputs = outputs,
            error = if (outputs.isEmpty()) MediaGenerationError(
                code = reason, message = "Gemini returned no image${reason?.let { " ($it)" }.orEmpty()}",
            ) else null,
            usage = root.obj("usageMetadata")?.let { MediaGenerationUsage(totalTokens = it.long("totalTokenCount")) },
            // Never persist base64 image payloads as task metadata.
            metadata = JsonObject(root.filterKeys { it in setOf("responseId", "modelVersion", "usageMetadata", "promptFeedback") }),
        )
    }

    private fun validate(request: MediaGenerationRequest) {
        require(!request.prompt.isNullOrBlank()) { "Gemini image generation requires a non-empty prompt" }
        require(request.count == null || request.count == 1) { "Gemini image generation does not support image count" }
        require(request.quality == null && request.complexity == null && request.outputFormat == null) {
            "Gemini image generation does not expose quality, complexity or output format"
        }
        require(request.durationSeconds == null && request.generateAudio == null && request.watermark == null &&
            request.seed == null && request.promptEnhancement == null && request.callbackUrl == null) {
            "Unsupported Gemini image generation parameter"
        }
    }
}
