package me.rerere.rikkahub.ui.pages.setting

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import java.util.Locale

private data class AppLanguage(val tag: String, val nativeName: String) {
    val locale: Locale = Locale.forLanguageTag(tag)
}

// Keep in sync with the app's translated values directories and androidResources.localeFilters.
private val appLanguages = listOf(
    AppLanguage("en", "English"),
    AppLanguage("ar", "العربية"),
    AppLanguage("de", "Deutsch"),
    AppLanguage("ja", "日本語"),
    AppLanguage("ko-KR", "한국어"),
    AppLanguage("ru", "Русский"),
    AppLanguage("zh", "简体中文"),
    AppLanguage("zh-TW", "繁體中文"),
)

private fun selectedAppLanguage(locale: Locale?): AppLanguage? =
    appLanguages.firstOrNull {
        locale != null && LocaleListCompat.matchesLanguageAndScript(it.locale, locale)
    }

@Composable
internal fun currentAppLanguageName(): String {
    val locale = AppCompatDelegate.getApplicationLocales()[0]
    return selectedAppLanguage(locale)?.nativeName
        ?: locale?.getDisplayName(locale)
        ?: stringResource(R.string.setting_page_language_system)
}

@Composable
internal fun AppLanguagePicker(onDismiss: () -> Unit) {
    val locale = AppCompatDelegate.getApplicationLocales()[0]
    val selectedLanguage = selectedAppLanguage(locale)
    val systemLabel = stringResource(R.string.setting_page_language_system)
    val options = listOf<AppLanguage?>(null) + appLanguages

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_page_language),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            CardGroup(modifier = Modifier.selectableGroup()) {
                options.forEach { language ->
                    val isSelected = if (language == null) locale == null else language == selectedLanguage
                    item(
                        onClick = {
                            onDismiss()
                            if (!isSelected) {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(language?.tag.orEmpty())
                                )
                            }
                        },
                        modifier = Modifier.semantics {
                            selected = isSelected
                            role = Role.RadioButton
                        },
                        trailingContent = {
                            RadioButton(selected = isSelected, onClick = null)
                        },
                        headlineContent = { Text(language?.nativeName ?: systemLabel) },
                    )
                }
            }
        }
    }
}
