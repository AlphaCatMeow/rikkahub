package me.rerere.mediagen.provider

import kotlinx.coroutines.runBlocking
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.provider.providers.MediaGenerationApiException
import me.rerere.mediagen.provider.providers.fakeClient
import me.rerere.mediagen.provider.providers.jsonResponse
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaGenerationModelListingTest {
    @Test
    fun openAIListsAllModelsAndRanksImageModelsFirst() = runBlocking {
        val requests = mutableListOf<Request>()
        val manager = MediaGenerationManager(fakeClient(requests) { request ->
            jsonResponse(
                request,
                """{"object":"list","data":[{"id":"gpt-5"},{"id":"gpt-image-2"},{"id":"dall-e-3"},{"id":"gpt-image-2"},{"id":""}]}"""
            )
        })
        val setting = MediaGenerationProviderSetting.OpenAI(apiKey = "key", baseUrl = "https://relay.example.com/v1/")

        val models = manager.listModels(setting).getOrThrow()

        assertEquals("https://relay.example.com/v1/models", requests.single().url.toString())
        assertEquals("GET", requests.single().method)
        assertEquals("Bearer key", requests.single().header("Authorization"))
        // 去重、去空，识别出的图像模型排在前面，其余的保留但标记为未识别
        assertEquals(listOf("dall-e-3", "gpt-image-2", "gpt-5"), models.map { it.modelId })
        assertEquals(listOf(true, true, false), models.map { it.recognized })
        assertTrue(models.all { it.kind == MediaKind.IMAGE })
    }

    @Test
    fun openRouterQueriesEachSupportedKindByOutputModality() = runBlocking {
        val requests = mutableListOf<Request>()
        val manager = MediaGenerationManager(fakeClient(requests) { request ->
            when (request.url.queryParameter("output_modalities")) {
                "image" -> jsonResponse(
                    request,
                    """{"data":[{"id":"openai/gpt-image-2","name":"OpenAI: GPT Image 2"},{"id":"google/gemini-image"}]}"""
                )

                "video" -> jsonResponse(request, """{"data":[{"id":"google/veo-3.1","name":"Google: Veo 3.1"}]}""")
                else -> error("unexpected request ${request.url}")
            }
        })
        val setting = MediaGenerationProviderSetting.OpenRouter(apiKey = "key")

        val models = manager.listModels(setting).getOrThrow()

        assertEquals(
            listOf("image", "video"),
            requests.map { it.url.queryParameter("output_modalities") },
        )
        assertTrue(requests.all { it.url.encodedPath == "/api/v1/models" })
        assertEquals(
            listOf(
                "google/gemini-image" to MediaKind.IMAGE,
                "openai/gpt-image-2" to MediaKind.IMAGE,
                "google/veo-3.1" to MediaKind.VIDEO,
            ),
            models.map { it.modelId to it.kind },
        )
        assertEquals("Google: Veo 3.1", models.last().displayName)
        assertTrue(models.all { it.recognized })
    }

    @Test
    fun listingErrorsSurfaceAsApiException() = runBlocking {
        val manager = MediaGenerationManager(fakeClient { request ->
            jsonResponse(request, """{"error":{"code":"invalid_api_key","message":"Incorrect API key"}}""", 401)
        })

        val error = manager.listModels(MediaGenerationProviderSetting.OpenAI(apiKey = "bad")).exceptionOrNull()

        assertTrue(error is MediaGenerationApiException)
        assertEquals(401, (error as MediaGenerationApiException).statusCode)
    }

    @Test
    fun providersWithoutListingFailWithoutNetwork() = runBlocking {
        val manager = MediaGenerationManager(fakeClient { error("listing must not hit the network") })
        val setting = MediaGenerationProviderSetting.MiniMax(apiKey = "key")

        assertFalse(manager.supportsModelListing(setting))
        assertTrue(manager.listModels(setting).exceptionOrNull() is UnsupportedOperationException)
        assertTrue(manager.supportsModelListing(MediaGenerationProviderSetting.OpenAI()))
        assertTrue(manager.supportsModelListing(MediaGenerationProviderSetting.OpenRouter()))
        assertFalse(manager.supportsModelListing(MediaGenerationProviderSetting.Aliyun()))
        assertFalse(manager.supportsModelListing(MediaGenerationProviderSetting.Volcengine()))
    }
}
