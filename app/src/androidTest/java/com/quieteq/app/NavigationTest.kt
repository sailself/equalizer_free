package com.quieteq.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quieteq.app.core.ThemePalette
import com.quieteq.app.data.DataStoreSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun label(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)

    private fun awaitSound() {
        val equalizer = label(R.string.equalizer)
        compose.waitUntil(15_000) { compose.onAllNodes(hasText(equalizer)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun soundProfilesAndSettingsAreReachable() {
        awaitSound()
        compose.onNodeWithText(label(R.string.equalizer)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.nav_profiles)).performClick()
        compose.onNodeWithText(label(R.string.starting_points)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.nav_settings)).performClick()
        compose.onNodeWithText(label(R.string.appearance)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.nav_sound)).performClick()
        compose.onNodeWithText(label(R.string.equalizer)).assertIsDisplayed()
    }

    @Test fun savedProfileRestoresEditedBandAndCanBeDeleted() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = DataStoreSettingsRepository(compose.activity.applicationContext, scope)
        val backup = runBlocking { repo.exportBackup() }
        val name = "UI test ${UUID.randomUUID().toString().take(8)}"
        try {
            awaitSound()
            compose.onNodeWithText(label(R.string.reset_equalizer)).performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().audio.gains.all { it == 0f } } }
            val gain = String.format(compose.activity.resources.configuration.locales[0], "%+.1f", 0.5f)
            compose.onNodeWithText(label(R.string.gain_db, gain)).performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().audio.gains[5] == .5f } }
            compose.onNodeWithText(label(R.string.nav_profiles)).performClick()
            compose.onNodeWithText(label(R.string.save)).performScrollTo().performClick()
            compose.onNodeWithText(label(R.string.profile_name)).performTextInput(name)
            compose.onNode(hasText(label(R.string.save)) and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().profiles.any { it.name == name } } }
            compose.onNodeWithText(label(R.string.nav_sound)).performClick()
            compose.onNodeWithText(label(R.string.reset_equalizer)).performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().audio.gains[5] == 0f } }
            compose.onNodeWithText(label(R.string.nav_profiles)).performClick()
            compose.onNodeWithText(name).performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().audio.gains[5] == .5f } }
            compose.onNodeWithContentDescription(label(R.string.profile_options, name)).performScrollTo().performClick()
            compose.onNodeWithText(label(R.string.delete)).performClick()
            compose.onNode(hasText(label(R.string.delete)) and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().profiles.none { it.name == name } } }
        } finally {
            runBlocking { repo.importBackup(backup) }
            scope.cancel()
        }
    }

    @Test fun volumeVisibilitySettingPersists() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = DataStoreSettingsRepository(compose.activity.applicationContext, scope)
        val backup = runBlocking { repo.exportBackup() }
        val original = runBlocking { repo.loadSettings().showVolume }
        try {
            awaitSound()
            compose.onNodeWithText(label(R.string.nav_settings)).performClick()
            compose.onNodeWithContentDescription(label(R.string.show_media_volume)).performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().showVolume != original } }
        } finally {
            runBlocking { repo.importBackup(backup) }
            scope.cancel()
        }
    }

    @Test fun paletteAndLanguagePersistAcrossRecreation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = DataStoreSettingsRepository(compose.activity.applicationContext, scope)
        val backup = runBlocking { repo.exportBackup() }
        val original = runBlocking { repo.loadSettings() }
        val originalLocales = compose.runOnUiThread { AppCompatDelegate.getApplicationLocales() }
        val palette = if (original.palette == ThemePalette.OCEAN) ThemePalette.FOREST else ThemePalette.OCEAN
        val paletteLabel = if (palette == ThemePalette.FOREST) R.string.palette_forest else R.string.palette_ocean
        val languageOption = if (label(R.string.language) == "Idioma") "日本語" else "Español"
        val translatedLabel = if (languageOption == "Español") "Idioma" else "言語"
        try {
            awaitSound()
            compose.onNodeWithText(label(R.string.nav_settings)).performClick()
            compose.onNodeWithText(label(paletteLabel)).performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().palette == palette } }
            assertEquals(original.theme, runBlocking { repo.loadSettings().theme })

            compose.onNodeWithContentDescription(label(R.string.language)).performScrollTo().performClick()
            compose.onNodeWithText(languageOption).performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasText(translatedLabel)).fetchSemanticsNodes().isNotEmpty() }

            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodes(hasText(translatedLabel)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(label(paletteLabel)).performScrollTo().assertIsSelected()
        } finally {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(originalLocales) }
            runBlocking { repo.importBackup(backup) }
            scope.cancel()
        }
    }
}
