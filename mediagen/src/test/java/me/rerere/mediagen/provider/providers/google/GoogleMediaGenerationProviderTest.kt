package me.rerere.mediagen.provider.providers.google

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.mediagen.model.MediaGenerationInput
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaGenerationRequest
import me.rerere.mediagen.model.MediaGenerationStatus
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.provider.MediaGenerationManager
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.mediagen.provider.capabilities
import me.rerere.mediagen.provider.providers.bodyAsString
import me.rerere.mediagen.provider.providers.fakeClient
import me.rerere.mediagen.provider.providers.jsonResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class GoogleMediaGenerationProviderTest {
    private val model = MediaGenerationModel(modelId = "gemini-3.1-flash-image", kind = MediaKind.IMAGE)
    private val setting = MediaGenerationProviderSetting.Google(
        baseUrl = "https://relay.example.test/prefix/v1beta/", apiKey = "test-only-key",
        models = listOf(model), customHeaders = mapOf("X-Gateway" to "test"),
    )

    @Test
    fun managerRoutesGoogleToGenerateContentWithKeyHeaderAndParsesInlineImages() = runBlocking {
        val requests = mutableListOf<Request>()
        val manager = MediaGenerationManager(fakeClient(requests) { request ->
            jsonResponse(request, """{"responseId":"reply-1","candidates":[{"content":{"parts":[{"text":"done"},{"inlineData":{"mimeType":"image/png","data":"aW1hZ2U="}}]},"finishReason":"STOP"}],"usageMetadata":{"totalTokenCount":12}}""")
        })
        val task = manager.create(setting, model, MediaGenerationRequest(
            prompt = "cat", resolution = "2K", aspectRatio = "16:9",
        )).getOrThrow()
        val request = requests.single()
        assertEquals("/prefix/v1beta/models/gemini-3.1-flash-image:generateContent", request.url.encodedPath)
        assertEquals("test-only-key", request.header("x-goog-api-key"))
        assertEquals("test", request.header("X-Gateway"))
        assertNull(request.header("Authorization"))
        assertNull(request.url.queryParameter("key"))
        val body = Json.parseToJsonElement(request.bodyAsString()).jsonObject
        val config = body.getValue("generationConfig").jsonObject
        assertEquals(listOf("TEXT", "IMAGE"), config.getValue("responseModalities").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("2K", config.getValue("imageConfig").jsonObject.getValue("imageSize").jsonPrimitive.content)
        assertEquals("16:9", config.getValue("imageConfig").jsonObject.getValue("aspectRatio").jsonPrimitive.content)
        assertEquals(MediaGenerationStatus.SUCCEEDED, task.status)
        assertArrayEquals("image".toByteArray(), task.outputs.single().data)
        assertEquals("image/png", task.outputs.single().mimeType)
        assertEquals(12L, task.usage?.totalTokens)
        assertFalse(task.metadata.containsKey("candidates"))
    }

    @Test
    fun previewKeepsModelPrefixAndAssetPlaceholdersWithoutReadingFilesOrMakingRequests() {
        val manager = MediaGenerationManager(fakeClient { error("Preview must not make network calls") })
        val preview = manager.preview(setting, model.copy(modelId = "models/gemini-test"), MediaGenerationRequest(
            prompt = "edit", inputs = listOf(MediaGenerationInput.Image("/does/not/exist.png")),
        )).getOrThrow()
        assertEquals("https://relay.example.test/prefix/v1beta/models/gemini-test:generateContent", preview.url)
        val part = preview.body.getValue("contents").jsonArray.single().jsonObject
            .getValue("parts").jsonArray[1].jsonObject.getValue("inlineData").jsonObject
        assertEquals("asset-1", part.getValue("data").jsonPrimitive.content)
        assertFalse(preview.toString().contains(setting.apiKey))
    }

    @Test
    fun referenceImageDataAndExtraConfigurationArePreserved() {
        val provider = GoogleMediaGenerationProvider(OkHttpClient())
        val body = provider.buildBody(MediaGenerationRequest(
            prompt = "edit", aspectRatio = "1:1",
            inputs = listOf(MediaGenerationInput.Image("data:image/jpeg;base64,aW1hZ2U=")),
            extraParameters = buildJsonObject {
                put("generationConfig", buildJsonObject {
                    put("temperature", 0.5)
                    put("imageConfig", buildJsonObject { put("aspectRatio", "16:9"); put("imageSize", "2K") })
                })
            },
        ))
        val config = body.getValue("generationConfig").jsonObject
        assertEquals("0.5", config.getValue("temperature").jsonPrimitive.content)
        assertEquals("1:1", config.getValue("imageConfig").jsonObject.getValue("aspectRatio").jsonPrimitive.content)
        assertEquals("2K", config.getValue("imageConfig").jsonObject.getValue("imageSize").jsonPrimitive.content)
        val image = body.getValue("contents").jsonArray[0].jsonObject.getValue("parts").jsonArray[1].jsonObject
        assertEquals("image/jpeg", image.getValue("inlineData").jsonObject.getValue("mimeType").jsonPrimitive.content)
    }

    @Test
    fun blockedOrTextOnlyResponsesAreFailuresAndThoughtImagesAreNotOutputs() {
        val provider = GoogleMediaGenerationProvider(OkHttpClient())
        val blocked = Json.parseToJsonElement("""{"promptFeedback":{"blockReason":"SAFETY"}}""").jsonObject
        val task = provider.parseTask(blocked, model.modelId)
        assertEquals(MediaGenerationStatus.FAILED, task.status)
        assertEquals("SAFETY", task.error?.code)
        val thought = Json.parseToJsonElement("""{"candidates":[{"content":{"parts":[{"thought":true,"inlineData":{"mimeType":"image/png","data":"aW1hZ2U="}}]},"finishReason":"STOP"}]}""").jsonObject
        assertTrue(provider.parseTask(thought, model.modelId).outputs.isEmpty())
    }

    @Test
    fun unsupportedVideoAndMultipleImageCountFailBeforeNetworkRequests() = runBlocking {
        val manager = MediaGenerationManager(fakeClient { error("Unsupported requests must not reach network") })
        assertNull(setting.capabilities(MediaKind.VIDEO))
        assertTrue(manager.create(setting, model.copy(kind = MediaKind.VIDEO), MediaGenerationRequest(prompt = "cat")).isFailure)
        assertTrue(manager.create(setting, model, MediaGenerationRequest(prompt = "cat", count = 2)).isFailure)
    }
}
