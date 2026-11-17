package com.quieteq.app.data

import com.quieteq.app.core.*
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BackupValidationTest {
    @Test fun defaultsAndValidReferencesPass() {
        val settings = AppSettings(profiles = defaultProfiles, selectedProfileId = "flat", assignments = mapOf("AA:BB:CC:DD:EE:FF" to "flat"))
        validateSettings(settings)
    }
    @Test fun invalidReferencesAndDuplicatesFail() {
        val profile = defaultProfiles.first()
        listOf(
            AppSettings(profiles = listOf(profile, profile)),
            AppSettings(profiles = listOf(profile), selectedProfileId = "missing"),
            AppSettings(profiles = listOf(profile), assignments = mapOf("AA:BB:CC:DD:EE:FF" to "missing")),
            AppSettings(profiles = listOf(profile), assignments = mapOf("bad" to profile.id)),
            AppSettings(profiles = listOf(profile.copy(name = " "))),
        ).forEach { assertThrows(IllegalArgumentException::class.java) { validateSettings(it) } }
    }
    @Test fun invalidAudioAndOversizedInputFail() {
        assertThrows(IllegalArgumentException::class.java) { validateSettings(AppSettings(audio = AudioSettings(gains = List(10) { Float.NaN }))) }
        assertThrows(IllegalArgumentException::class.java) { validateSettings(AppSettings(audio = AudioSettings(bass = 101))) }
        assertThrows(IllegalArgumentException::class.java) { parseBackup(" ".repeat(1024 * 1024 + 1)) }
        assertThrows(IllegalArgumentException::class.java) { parseBackup("[".repeat(33) + "]".repeat(33)) }
    }
    @Test fun rejectsMissingAndUnknownVersions() {
        assertThrows(Exception::class.java) { parseBackup("""{"settings":{}}""") }
        assertThrows(Exception::class.java) { parseBackup("""{"version":2,"settings":{}}""") }
        assertThrows(Exception::class.java) { parseBackup("""{"version":1,"settings":{}}""") }
    }
    @Test fun roundTripPreservesAllSettingsAndAssignments() {
        val audio = AudioSettings(gains = List(10) { it.toFloat() - 5f }, eqEnabled = false, bass = 22,
            loudness = 37, balance = -0.3f, autoHeadroom = false, preferLegacy = true,
            virtualizer = 62, attachmentMode = AttachmentMode.GLOBAL, frameDurationMs = 25f)
        val p = Profile("my_profile", "My profile", audio)
        val original = AppSettings(audio = audio.copy(bass = 45), profiles = listOf(p), selectedProfileId = p.id,
            assignments = mapOf("AA:BB:CC:DD:EE:FF" to p.id), theme = ThemeMode.LIGHT, showVolume = false, showReverb = true)
        assertEquals(original, parseBackup(encodeBackup(original)))
    }
    @Test fun malformedImportNeverCallsWrite() = runBlocking {
        val initial = AppSettings(profiles = defaultProfiles)
        var saved = initial
        val invalid = encodeBackup(initial).replace("\"bass\":0", "\"bass\":999")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { validatedImport(invalid) { saved = it } } }
        assertEquals(initial, saved)
        val replacement = initial.copy(theme = ThemeMode.LIGHT)
        validatedImport(encodeBackup(replacement)) { saved = it }
        assertEquals(replacement, saved)
    }
    @Test fun profileChangesPreserveSoundAndCleanUpReferences() {
        val p = defaultProfiles[1]
        val start = AppSettings(audio = p.settings, profiles = listOf(p), selectedProfileId = p.id,
            assignments = mapOf("AA:BB:CC:DD:EE:FF" to p.id))
        val renamed = renameProfileIn(start, p.id, "Renamed")
        assertEquals(start.audio, renamed.audio)
        assertEquals(p.id, renamed.selectedProfileId)
        val duplicated = duplicateProfileIn(renamed, p.id, "new-id", "Copy")
        assertEquals(2, duplicated.profiles.map { it.id }.toSet().size)
        assertEquals(p.settings, duplicated.profiles.last().settings)
        val deleted = deleteProfileFrom(duplicated, p.id)
        assertEquals(start.audio, deleted.audio)
        assertNull(deleted.selectedProfileId)
        assertTrue(deleted.assignments.isEmpty())
        assertEquals("new-id", deleted.profiles.single().id)
    }
}
