package com.quieteq.app

import androidx.compose.ui.test.assertIsDisplayed
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
import com.quieteq.app.data.DataStoreSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitSound() {
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Equalizer")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun soundProfilesAndSettingsAreReachable() {
        awaitSound()
        compose.onNodeWithText("Equalizer").assertIsDisplayed()
        compose.onNodeWithText("Profiles").performClick()
        compose.onNodeWithText("Starting points").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("Sound").performClick()
        compose.onNodeWithText("Equalizer").assertIsDisplayed()
    }

    @Test fun savedProfileRestoresEditedBandAndCanBeDeleted() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = DataStoreSettingsRepository(compose.activity.applicationContext, scope)
        val backup = runBlocking { repo.exportBackup() }
        val name = "UI test ${UUID.randomUUID().toString().take(8)}"
        try {
            awaitSound()
            compose.onNodeWithText("Reset equalizer").performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().audio.gains.all { it == 0f } } }
            compose.onNodeWithText("+  0.5 dB").performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().audio.gains[5] == .5f } }
            compose.onNodeWithText("Profiles").performClick()
            compose.onNodeWithText("Save").performScrollTo().performClick()
            compose.onNodeWithText("Profile name").performTextInput(name)
            compose.onNode(hasText("Save") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().profiles.any { it.name == name } } }
            compose.onNodeWithText("Sound").performClick()
            compose.onNodeWithText("Reset equalizer").performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().audio.gains[5] == 0f } }
            compose.onNodeWithText("Profiles").performClick()
            compose.onNodeWithText(name).performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().audio.gains[5] == .5f } }
            compose.onNodeWithContentDescription("Options for $name").performScrollTo().performClick()
            compose.onNodeWithText("Delete").performClick()
            compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
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
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithContentDescription("Show media volume").performClick()
            compose.waitUntil(10_000) { runBlocking { repo.loadSettings().showVolume != original } }
        } finally {
            runBlocking { repo.importBackup(backup) }
            scope.cancel()
        }
    }
}
