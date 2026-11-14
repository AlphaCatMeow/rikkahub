package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun emptyOverrideFollowsSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLocale(null))
    }

    @Test
    fun regionalVariantsMatchTheirLanguage() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocale(Locale.forLanguageTag("en-US")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocale(Locale.forLanguageTag("en-GB")))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromLocale(Locale.forLanguageTag("ko")))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromLocale(Locale.forLanguageTag("ko-KR")))
    }

    @Test
    fun chineseRegionFallbackSelectsTheCorrectTranslation() {
        listOf("zh", "zh-CN", "zh-SG").forEach { tag ->
            assertEquals(tag, AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLocale(Locale.forLanguageTag(tag)))
        }
        listOf("zh-TW", "zh-HK", "zh-MO").forEach { tag ->
            assertEquals(tag, AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromLocale(Locale.forLanguageTag(tag)))
        }
    }

    @Test
    fun chineseScriptTakesPrecedenceOverRegion() {
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromLocale(Locale.forLanguageTag("zh-Hant")))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromLocale(Locale.forLanguageTag("zh-Hant-CN")))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLocale(Locale.forLanguageTag("zh-Hans-TW")))
    }

    @Test
    fun unsupportedOverrideIsNotReportedAsFollowingSystem() {
        assertNull(AppLanguage.fromLocale(Locale.forLanguageTag("fr")))
    }
}
