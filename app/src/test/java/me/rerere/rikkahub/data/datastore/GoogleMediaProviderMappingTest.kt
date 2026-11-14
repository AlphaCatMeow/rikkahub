package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.mediagen.provider.capabilities
import org.junit.Assert.*
import org.junit.Test

class GoogleMediaProviderMappingTest {
    private val models = listOf(
        "gemini-3-pro-image", "gemini-3-pro-image-preview", "gemini-3.1-flash-image",
        "gemini-3.1-flash-image-preview", "gemini-nano-banana-2.1",
    ).map { Model(modelId = it, type = ModelType.IMAGE) }
    private val google = ProviderSetting.Google(
        name = "Gemini relay", apiKey = "key-one, key-two", baseUrl = "https://relay.example.test/v1beta",
        models = models + Model(modelId = "chat-only") + Model(modelId = "video", type = ModelType.VIDEO),
        customHeaders = listOf(CustomHeader("X-Gateway", "test")),
    )

    @Test
    fun existingGoogleProviderExposesAllFiveImageModelsWithOriginalIds() {
        val settings = Settings(providers = listOf(google))
        val media = settings.mediaGenerationProviders.single() as MediaGenerationProviderSetting.Google
        assertEquals(google.id, media.id)
        assertEquals(google.baseUrl, media.baseUrl)
        assertEquals("key-one", media.apiKey)
        assertEquals(mapOf("X-Gateway" to "test"), media.customHeaders)
        assertEquals(models.map { it.id to it.modelId }, media.models.map { it.id to it.modelId })
        assertTrue(media.models.all { it.kind == MediaKind.IMAGE && media.capabilities(it.kind) != null })
    }

    @Test
    fun disabledNonImageAndUnsupportedVertexProvidersStayOutOfMediaList() {
        assertNull(google.copy(enabled = false).toMediaProvider())
        assertNull(google.copy(models = listOf(Model(modelId = "chat-only"))).toMediaProvider())
        assertNull(google.copy(vertexAI = true).toMediaProvider())
    }

    @Test
    fun googleMediaRoundTripKeepsProtocolIdsAndCustomHeaders() {
        val media = google.toMediaProvider()!!
        val restored = media.toChatProvider() as ProviderSetting.Google
        assertEquals(google.id, restored.id)
        assertEquals(models.map { it.id }, restored.models.map { it.id })
        assertEquals(google.customHeaders, restored.customHeaders)
        assertTrue(restored.models.all { it.type == ModelType.IMAGE })
    }
}
