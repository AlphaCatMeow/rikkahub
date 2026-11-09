package me.rerere.ai.registry

import me.rerere.ai.provider.ModelType
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTypeRegistryTest {
    private fun type(id: String) = ModelRegistry.MODEL_TYPE.getData(id)

    @Test
    fun catalogMatchesIgnoringPrefixAndDate() {
        assertEquals(ModelType.IMAGE, type("gpt-image-1"))
        assertEquals(ModelType.IMAGE, type("openai/GPT-Image-1.5-2025-12-16"))
        assertEquals(ModelType.VIDEO, type("sora-2"))
        assertEquals(ModelType.VIDEO, type("doubao-seedance-2-5-260628"))
    }

    @Test
    fun familyFallback() {
        assertEquals(ModelType.IMAGE, type("doubao-seedream-4-0-250828"))
        assertEquals(ModelType.IMAGE, type("wanx2.1-t2i-turbo"))
        assertEquals(ModelType.VIDEO, type("wan2.5-i2v-preview"))
        assertEquals(ModelType.VIDEO, type("kling-v2-1"))
    }

    @Test
    fun chatModelsStayUnknown() {
        assertEquals(null, type("gpt-4o"))
        assertEquals(null, type("deepseek-chat"))
        assertEquals(null, type("qwen3-max"))
        assertEquals(null, type("claude-sonnet-4-5"))
        assertEquals(null, type("gemini-2.5-flash"))
    }
}
