package com.quieteq.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.quieteq.app.R
import com.quieteq.app.core.ThemeMode
import com.quieteq.app.core.ThemePalette
import java.util.Locale

@Composable
internal fun ThemeOptions(
    theme: ThemeMode,
    palette: ThemePalette,
    dark: Boolean,
    onThemeChange: (ThemeMode) -> Unit,
    onPaletteChange: (ThemePalette) -> Unit,
) {
    Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { option ->
            val label = when (option) {
                ThemeMode.DARK -> R.string.theme_dark
                ThemeMode.LIGHT -> R.string.theme_light
                ThemeMode.SYSTEM -> R.string.theme_system
            }
            FilterChip(
                selected = theme == option,
                onClick = { onThemeChange(option) },
                label = { Text(stringResource(label)) },
            )
        }
    }

    Text(stringResource(R.string.color_scheme), style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemePalette.entries.forEach { option ->
            val label = when (option) {
                ThemePalette.ORIGINAL -> R.string.palette_original
                ThemePalette.OCEAN -> R.string.palette_ocean
                ThemePalette.FOREST -> R.string.palette_forest
                ThemePalette.LAVENDER -> R.string.palette_lavender
            }
            FilterChip(
                selected = palette == option,
                onClick = { onPaletteChange(option) },
                label = { Text(stringResource(label)) },
                leadingIcon = {
                    Box(Modifier.size(14.dp).background(quietEqColorScheme(option, dark).primary, CircleShape))
                },
            )
        }
    }
}

private enum class AppLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    JAPANESE("ja", "日本語"),
    SIMPLIFIED_CHINESE("zh-Hans", "简体中文"),
    TRADITIONAL_CHINESE("zh-Hant", "繁體中文"),
}

@Composable
internal fun LanguagePicker() {
    val configuration = LocalConfiguration.current
    var appLocales by remember(configuration) { mutableStateOf(AppCompatDelegate.getApplicationLocales()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        appLocales = AppCompatDelegate.getApplicationLocales()
    }
    val locale = appLocales[0]
    val current = locale?.let {
        AppLanguage.entries.firstOrNull { language ->
            LocaleListCompat.matchesLanguageAndScript(Locale.forLanguageTag(language.tag), it)
        }
    }
    val deviceLanguage = stringResource(R.string.language_device)
    var expanded by remember { mutableStateOf(false) }
    fun choose(locales: LocaleListCompat) {
        expanded = false
        AppCompatDelegate.setApplicationLocales(locales)
        appLocales = locales
    }

    val label = stringResource(R.string.language)
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Text(current?.nativeName ?: locale?.getDisplayName(locale) ?: deviceLanguage)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LanguageOption(deviceLanguage, appLocales.isEmpty) {
                choose(LocaleListCompat.getEmptyLocaleList())
            }
            AppLanguage.entries.forEach { language ->
                LanguageOption(language.nativeName, current == language) {
                    choose(LocaleListCompat.forLanguageTags(language.tag))
                }
            }
        }
    }
}

@Composable
private fun LanguageOption(name: String, active: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        modifier = Modifier.semantics { selected = active },
        text = { Text(name) },
        onClick = onClick,
        trailingIcon = if (active) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
    )
}
