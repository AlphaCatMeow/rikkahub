package me.rerere.rikkahub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsNormalizationTest {
    @Test
    fun deletedBuiltInAssistantStaysDeleted() {
        val kept = DEFAULT_ASSISTANTS.last()
        val settings = Settings(assistants = listOf(kept)).withBuiltInDefaults()
        assertEquals(listOf(kept.id), settings.assistants.map { it.id })
    }

    @Test
    fun emptyAssistantsFallBackToBuiltIns() {
        val settings = Settings(assistants = emptyList()).withBuiltInDefaults()
        assertEquals(DEFAULT_ASSISTANTS.map { it.id }, settings.assistants.map { it.id })
    }
}
