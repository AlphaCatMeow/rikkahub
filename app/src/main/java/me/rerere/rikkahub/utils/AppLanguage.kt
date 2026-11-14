package me.rerere.rikkahub.utils

import java.util.Locale

internal enum class AppLanguage(val languageTag: String, val nativeName: String) {
    SYSTEM("", ""),
    ENGLISH("en", "English"),
    ARABIC("ar", "العربية"),
    GERMAN("de", "Deutsch"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko-KR", "한국어"),
    RUSSIAN("ru", "Русский"),
    SIMPLIFIED_CHINESE("zh-CN", "简体中文"),
    TRADITIONAL_CHINESE("zh-TW", "繁體中文");

    companion object {
        fun fromLocale(locale: Locale?): AppLanguage? {
            if (locale == null) return SYSTEM
            if (locale.language == "zh") {
                // Script takes precedence over region for tags such as zh-Hans-TW.
                val traditional = locale.script == "Hant" ||
                    (locale.script.isEmpty() && locale.country in setOf("TW", "HK", "MO"))
                return if (traditional) TRADITIONAL_CHINESE else SIMPLIFIED_CHINESE
            }
            return entries.firstOrNull {
                it != SYSTEM && Locale.forLanguageTag(it.languageTag).language == locale.language
            }
        }
    }
}
