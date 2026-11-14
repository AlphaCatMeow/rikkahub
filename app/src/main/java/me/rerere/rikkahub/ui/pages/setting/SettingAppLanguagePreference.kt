package me.rerere.rikkahub.ui.pages.setting

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Internet
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.utils.AppLanguage

@Composable
internal fun SettingAppLanguagePreference() {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { AppCompatDelegate.getApplicationLocales()[0] }
    val selectedLanguage = AppLanguage.fromLocale(locale)
    val followSystem = stringResource(R.string.setting_page_language_follow_system)
    var showPicker by remember { mutableStateOf(false) }

    CardGroup {
        item(
            onClick = { showPicker = true },
            leadingContent = { CardGroupIcon(HugeIcons.Internet) },
            headlineContent = { Text(stringResource(R.string.setting_page_app_language)) },
            supportingContent = {
                Text(
                    when (selectedLanguage) {
                        AppLanguage.SYSTEM -> followSystem
                        null -> locale?.getDisplayName(locale).orEmpty()
                        else -> selectedLanguage.nativeName
                    }
                )
            },
        )
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.setting_page_app_language)) },
            text = {
                CardGroup(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                ) {
                    AppLanguage.entries.forEach { language ->
                        val selected = language == selectedLanguage
                        item(
                            modifier = Modifier.selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = {
                                    showPicker = false
                                    AppCompatDelegate.setApplicationLocales(
                                        LocaleListCompat.forLanguageTags(language.languageTag)
                                    )
                                },
                            ),
                            leadingContent = { RadioButton(selected = selected, onClick = null) },
                            headlineContent = {
                                Text(if (language == AppLanguage.SYSTEM) followSystem else language.nativeName)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
