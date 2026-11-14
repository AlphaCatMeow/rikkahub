package me.rerere.ai.provider.providers.google

import java.io.IOException
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleModelListingFailureTest {
    private val setting = ProviderSetting.Google(
        baseUrl = "https://google-models.invalid/v1beta",
        apiKey = "test-api-key",
    )

    private fun provider(
        code: Int = 200,
        body: ResponseBody,
        onRequest: (Request) -> Unit = {},
    ) = GoogleProvider(
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                onRequest(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Mock response")
                    .body(body)
                    .build()
            }
            .build()
    )

    @Test
    fun `HTTP failures report status without response body and close the body`() {
        for (code in listOf(400, 401, 403, 429, 500, 503)) {
            val message = """{"error":{"message":"Mock failure $code"}}"""
            val body = TrackingBody(message)
            val provider = provider(code = code, body = body)

            val failure = assertThrows(IOException::class.java) {
                runBlocking { provider.listModels(setting) }
            }

            assertTrue(failure.message.orEmpty().contains("HTTP $code"))
            assertTrue("Response body must be closed for HTTP $code", body.closed)
        }
    }

    @Test
    fun `HTTP failure supports plain text and empty response bodies`() {
        for (message in listOf("Mock gateway failure", "")) {
            val body = TrackingBody(message)
            val provider = provider(code = 502, body = body)

            val failure = assertThrows(IOException::class.java) {
                runBlocking { provider.listModels(setting) }
            }

            assertTrue(failure.message.orEmpty().contains("HTTP 502"))
            assertTrue(body.closed)
        }
    }

    @Test
    fun `successful listing preserves request authentication and model filtering`() = runBlocking {
        val body = TrackingBody(
            """
            {"models":[
              {"name":"models/gemini-test","displayName":"Gemini test","supportedGenerationMethods":["generateContent","embedContent"]},
              {"name":"models/embedding-test","displayName":"Embedding test","supportedGenerationMethods":["embedContent"]},
              {"name":"models/unsupported-test","displayName":"Unsupported test","supportedGenerationMethods":["countTokens"]}
            ]}
            """.trimIndent()
        )
        var captured: Request? = null
        val provider = provider(body = body) { captured = it }

        val models = provider.listModels(setting)
        val request = requireNotNull(captured)

        assertEquals(listOf("gemini-test", "embedding-test"), models.map { it.modelId })
        assertEquals(listOf("Gemini test", "Embedding test"), models.map { it.displayName })
        assertEquals(listOf(ModelType.CHAT, ModelType.EMBEDDING), models.map { it.type })
        assertEquals("GET", request.method)
        assertEquals("/v1beta/models", request.url.encodedPath)
        assertEquals("100", request.url.queryParameter("pageSize"))
        assertEquals("test-api-key", request.header("x-goog-api-key"))
        assertTrue(body.closed)
    }

    @Test
    fun `successful empty listing remains an empty list`() = runBlocking {
        for (message in listOf("{}", """{"models":[]}""")) {
            val body = TrackingBody(message)
            assertTrue(provider(body = body).listModels(setting).isEmpty())
            assertTrue(body.closed)
        }
    }

    @Test
    fun `invalid successful response throws and closes the body`() {
        val body = TrackingBody("not json")
        val provider = provider(body = body)

        assertThrows(IOException::class.java) {
            runBlocking { provider.listModels(setting) }
        }

        assertTrue(body.closed)
    }

    @Test
    fun `transport failure propagates instead of returning an empty list`() {
        val failure = IOException("Mock connection failure")
        val provider = GoogleProvider(
            OkHttpClient.Builder()
                .addInterceptor { throw failure }
                .build()
        )

        val actual = assertThrows(IOException::class.java) {
            runBlocking { provider.listModels(setting) }
        }

        assertEquals(failure.message, actual.message)
    }

    private class TrackingBody(content: String) : ResponseBody() {
        private val delegate = content.toResponseBody("application/json".toMediaType())
        var closed = false
            private set
        private val trackedSource = object : ForwardingSource(delegate.source()) {
            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()

        override fun contentType() = delegate.contentType()

        override fun contentLength() = delegate.contentLength()

        override fun source() = trackedSource
    }
}
