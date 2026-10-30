package me.rerere.mediagen.provider.providers.openai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.mediagen.model.MediaGenerationInput
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaGenerationOutput
import me.rerere.mediagen.model.MediaGenerationRequest
import me.rerere.mediagen.model.MediaGenerationStatus
import me.rerere.mediagen.model.MediaGenerationTask
import me.rerere.mediagen.model.MediaGenerationUsage
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.model.RemoteMediaModel
import me.rerere.mediagen.provider.MediaGenerationProvider
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.mediagen.provider.providers.combineImageSize
import me.rerere.mediagen.provider.providers.executeJson
import me.rerere.mediagen.provider.providers.imageMimeType
import me.rerere.mediagen.provider.providers.isPixelSize
import me.rerere.mediagen.provider.providers.long
import me.rerere.mediagen.provider.providers.obj
import me.rerere.mediagen.provider.providers.postJson
import me.rerere.mediagen.provider.providers.string
import me.rerere.mediagen.provider.providers.tierEdge
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/**
 * 目前只实现了图像：接口同步返回结果，[create] 直接得到终态任务。请求里带图片输入时走编辑接口，否则走生成接口。
 */
class OpenAIMediaGenerationProvider(
    private val client: OkHttpClient,
) : MediaGenerationProvider<MediaGenerationProviderSetting.OpenAI> {
    override val id: String = PROVIDER_ID

    override suspend fun create(
        setting: MediaGenerationProviderSetting.OpenAI,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationTask> = runCatching {
        val baseUrl = setting.baseUrl.trimEnd('/')
        val builder = Request.Builder().addHeader("Authorization", "Bearer ${setting.apiKey}")
        val httpRequest = if (request.inputs.isEmpty()) {
            builder.url("$baseUrl/images/generations").postJson(buildGenerationBody(setting, model, request))
        } else {
            builder.url("$baseUrl/images/edits").post(buildEditBody(setting, model, request))
        }.build()
        parseTask(client.executeJson(httpRequest, id), model.modelId)
    }

    override val supportsModelListing: Boolean get() = true

    // /models 不区分模型类型，官方和中转都会把聊天模型一起列出来，只能按名字猜哪些是图像模型
    override suspend fun listModels(
        setting: MediaGenerationProviderSetting.OpenAI,
    ): Result<List<RemoteMediaModel>> = runCatching {
        val httpRequest = Request.Builder()
            .url("${setting.baseUrl.trimEnd('/')}/models")
            .addHeader("Authorization", "Bearer ${setting.apiKey}")
            .get()
            .build()
        parseModels(client.executeJson(httpRequest, id))
    }

    internal fun parseModels(root: JsonObject): List<RemoteMediaModel> {
        val data = root["data"] as? JsonArray ?: error("OpenAI model list does not contain data")
        return data
            .mapNotNull { (it as? JsonObject)?.string("id")?.takeIf(String::isNotBlank) }
            .distinct()
            .map { modelId ->
                RemoteMediaModel(
                    modelId = modelId,
                    kind = MediaKind.IMAGE,
                    recognized = IMAGE_MODEL_HINT.containsMatchIn(modelId),
                )
            }
            .sortedWith(compareByDescending<RemoteMediaModel> { it.recognized }.thenBy { it.modelId })
    }

    internal fun buildGenerationBody(
        setting: MediaGenerationProviderSetting.OpenAI,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): JsonObject = buildJsonObject {
        validate(request)
        request.extraParameters.forEach { (key, value) -> put(key, value) }
        put("model", model.modelId)
        put("prompt", request.prompt)
        request.count?.let { put("n", it) }
        sizeFields(setting, model, request).forEach { (key, value) -> put(key, value) }
    }

    internal fun buildEditBody(
        setting: MediaGenerationProviderSetting.OpenAI,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): MultipartBody {
        validate(request)
        val fields = linkedMapOf<String, String>()
        request.extraParameters.forEach { (key, value) ->
            fields[key] = (value as? JsonPrimitive)?.contentOrNull ?: value.toString()
        }
        fields["model"] = model.modelId
        fields["prompt"] = request.prompt.orEmpty()
        request.count?.let { fields["n"] = it.toString() }
        fields += sizeFields(setting, model, request)

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (key, value) -> builder.addFormDataPart(key, value) }

        val imageFieldName = if (request.inputs.size == 1) "image" else "image[]"
        request.inputs.forEach { input ->
            require(input is MediaGenerationInput.Image) {
                "OpenAI image generation does not support ${input::class.simpleName} input"
            }
            val file = File(input.url.removePrefix("file://"))
            require(file.exists()) { "Image file does not exist: ${input.url}" }
            val mediaType = EDIT_IMAGE_MEDIA_TYPES[file.extension.lowercase()]
                ?: throw IllegalArgumentException("Unsupported image file type for OpenAI edit: ${file.extension}")
            builder.addFormDataPart(imageFieldName, file.name, file.asRequestBody(mediaType.toMediaType()))
        }
        return builder.build()
    }

    internal fun parseTask(root: JsonObject, fallbackModel: String? = null): MediaGenerationTask {
        val defaultFormat = root.string("output_format")
        val data = root["data"] as? JsonArray ?: error("OpenAI response does not contain data")
        return MediaGenerationTask(
            // 同步接口没有服务端任务 ID
            id = Uuid.random().toString(),
            provider = id,
            model = fallbackModel,
            status = MediaGenerationStatus.SUCCEEDED,
            outputs = data.map { element ->
                val item = element.jsonObject
                // 部分中转同时给 url 和空字符串的 b64_json，空串当作没有
                val base64 = item.string("b64_json")?.takeIf { it.isNotBlank() }
                // dall-e 系列和部分中转不返回 b64_json，而是有时效的 url
                val url = item.string("url")?.takeIf { it.isNotBlank() }
                if (base64 == null && url == null) error("OpenAI image contains neither b64_json nor url")
                MediaGenerationOutput(
                    url = url,
                    data = base64?.let { Base64.decode(it.substringAfter("base64,")) },
                    mimeType = imageMimeType(item.string("output_format") ?: defaultFormat, "image/png"),
                    resolution = root.string("size"),
                )
            },
            createdAtEpochSeconds = root.long("created"),
            usage = root.obj("usage")?.let {
                MediaGenerationUsage(totalTokens = it.long("total_tokens"))
            },
            // data 里是整张图片的 base64，不放进 metadata
            metadata = JsonObject(root - "data"),
        )
    }

    private fun validate(request: MediaGenerationRequest) {
        require(!request.prompt.isNullOrBlank()) { "OpenAI image generation requires a non-empty prompt" }
        require(request.durationSeconds == null) { "OpenAI image generation does not expose duration" }
        require(request.generateAudio == null) { "OpenAI image generation does not expose generateAudio" }
        require(request.watermark == null) { "OpenAI image generation does not expose watermark" }
        require(request.seed == null) { "OpenAI image generation does not expose seed" }
        require(request.promptEnhancement == null) { "OpenAI image generation does not expose prompt enhancement" }
        require(request.callbackUrl == null) { "OpenAI image generation returns results synchronously" }
    }

    /**
     * 尺寸、比例和质量对应的字段。
     *
     * OpenAI 只有 size 字段，不接受档位：比例按档位换算成像素尺寸（gpt-image-2 起接受 16 倍数的任意尺寸），
     * 不给档位时 1:1、3:2、2:3 用所有 GPT 图像模型都接受的三个固定尺寸；"auto" 交给服务端默认值。
     * quality 原样下发，gpt-image 系列是 low / medium / high / auto，dall-e-3 是 standard / hd。
     *
     * Grok 的兼容接口不接受 size，比例和档位分别走 aspect_ratio 和 resolution（1k / 2k），像素尺寸只能丢掉。
     */
    private fun sizeFields(
        setting: MediaGenerationProviderSetting.OpenAI,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        val resolution = request.resolution?.trim()?.takeUnless { it.isEmpty() || it.equals("auto", ignoreCase = true) }
        if (isGrok(setting, model)) {
            resolution?.takeUnless { it.isPixelSize() }?.let { fields["resolution"] = it.lowercase() }
            request.aspectRatio?.let { fields["aspect_ratio"] = it }
        } else {
            val tierEdge = resolution?.tierEdge()
            // size 不接受档位，只给档位时按正方形换算
            val ratio = request.aspectRatio ?: "1:1".takeIf { tierEdge != null }
            val size = ratio?.takeIf { resolution == null || tierEdge == 1024 }?.let { LEGACY_SIZES[it.trim()] }
                ?: combineImageSize(resolution, ratio, defaultEdge = 1024, provider = "OpenAI")
            size?.let { fields["size"] = it }
        }
        request.quality?.takeIf(String::isNotBlank)?.let { fields["quality"] = it }
        return fields
    }

    // 只匹配 x.ai 本身及其子域名，避免 "xxx-max.ai" 之类的中转域名被误判
    private fun isGrok(setting: MediaGenerationProviderSetting.OpenAI, model: MediaGenerationModel): Boolean {
        val host = setting.baseUrl.toHttpUrlOrNull()?.host?.lowercase()
        return host == "x.ai" || host?.endsWith(".x.ai") == true || model.modelId.contains("grok", ignoreCase = true)
    }

    private companion object {
        const val PROVIDER_ID = "openai"
        val IMAGE_MODEL_HINT = Regex(
            "image|dall-?e|imagen|flux|seedream|midjourney|stable-diffusion|sdxl|sd3|kolors|recraft|ideogram|cogview|hidream|nano-banana",
            RegexOption.IGNORE_CASE,
        )
        // gpt-image-1 / 1.5 只接受这三个尺寸
        val LEGACY_SIZES = mapOf(
            "1:1" to "1024x1024",
            "3:2" to "1536x1024",
            "2:3" to "1024x1536",
        )
        val EDIT_IMAGE_MEDIA_TYPES = mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "webp" to "image/webp",
        )
    }
}
