package me.rerere.ai.provider.providers.google

import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class GoogleModelListingTest {
    private fun client(respond: (Request) -> Response): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain -> respond(chain.request()) }
        .build()

    private fun response(request: Request, body: String, code: Int = 200): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code == 200) "OK" else "Failed")
        .body(body.toResponseBody())
        .build()

    @Test
    fun `HTTP failure reports status instead of an empty model list or credentials`() = runBlocking {
        val setting = ProviderSetting.Google(apiKey = "test-secret-key")
        val provider = GoogleProvider(client { request ->
            response(request, """{"error":{"message":"test-secret-key is invalid"}}""", 403)
        })

        try {
            provider.listModels(setting)
            fail("HTTP 403 must be reported as a model listing failure")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("403"))
            assertFalse(error.message.orEmpty().contains(setting.apiKey))
        }
    }

    @Test
    fun `follows page tokens and normalizes a trailing slash`() = runBlocking {
        val requests = mutableListOf<Request>()
        val provider = GoogleProvider(client { request ->
            requests += request
            assertEquals("/v1beta/models", request.url.encodedPath)
            assertEquals("test-key", request.header("x-goog-api-key"))
            assertEquals("100", request.url.queryParameter("pageSize"))
            val body = if (requests.size == 1) {
                """{"models":[{"name":"models/gemini-test","displayName":"Gemini test","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"next/+ page"}"""
            } else {
                assertEquals("next/+ page", request.url.queryParameter("pageToken"))
                """{"models":[{"name":"models/embedding-test","supportedGenerationMethods":["embedContent"]}]}"""
            }
            response(request, body)
        })

        val models = provider.listModels(
            ProviderSetting.Google(baseUrl = "https://example.test/v1beta/", apiKey = "test-key")
        )

        assertEquals(2, requests.size)
        assertEquals(listOf("gemini-test", "embedding-test"), models.map { it.modelId })
        assertEquals(ModelType.EMBEDDING, models.last().type)
        assertEquals("embedding-test", models.last().displayName)
    }

    @Test
    fun `one model without optional metadata does not discard supported models`() = runBlocking {
        val provider = GoogleProvider(client { request ->
            response(request, """{"models":[{"name":"models/incomplete"},{"name":"models/chat-test","supportedGenerationMethods":["generateContent"]},{"name":"models/unsupported","supportedGenerationMethods":["countTokens"]}]}""")
        })

        val models = provider.listModels(ProviderSetting.Google(apiKey = "test-key"))

        assertEquals(listOf("incomplete", "chat-test"), models.map { it.modelId })
        assertEquals("chat-test", models.last().displayName)
    }

    @Test
    fun `malformed success response is an error rather than a misleading empty list`() = runBlocking {
        val provider = GoogleProvider(client { request ->
            response(request, """{"data":[{"id":"gemini-test"}]}""")
        })

        try {
            provider.listModels(ProviderSetting.Google(apiKey = "test-key"))
            fail("An incompatible models endpoint must report an error")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("models"))
        }
    }

    @Test
    fun `repeated pagination token terminates with an error`() = runBlocking {
        var requests = 0
        val provider = GoogleProvider(client { request ->
            requests++
            response(request, """{"models":[],"nextPageToken":"repeated"}""")
        })

        try {
            provider.listModels(ProviderSetting.Google(apiKey = "test-key"))
            fail("Repeated tokens must not loop forever")
        } catch (error: IOException) {
            assertEquals(2, requests)
        }
    }
    @Test
    fun `gateway models without capability metadata preserve bare model ids`() = runBlocking {
        val provider = GoogleProvider(client { request ->
            response(request, """{"models":[{"name":"gemini-image-test","displayName":"Gemini image","supportedGenerationMethods":null},{"name":"gemini-chat-test"},{"name":"models/official-chat","supportedGenerationMethods":["generateContent"]},{"name":"models/unsupported","supportedGenerationMethods":["countTokens"]},{"name":"models/empty-methods","supportedGenerationMethods":[]}],"nextPageToken":""}""")
        })
        val models = provider.listModels(ProviderSetting.Google(apiKey = "test-key"))
        assertEquals(listOf("gemini-image-test", "gemini-chat-test", "official-chat"), models.map { it.modelId })
        assertTrue(models.all { it.type == ModelType.CHAT })
        assertEquals("gemini-chat-test", models[1].displayName)
    }

}
