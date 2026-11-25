package com.quieteq.app.core

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

const val BAND_COUNT = 10
val BAND_FREQUENCIES_HZ = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

@Serializable
enum class AttachmentMode { AUTO, PLAYERS_ONLY, GLOBAL }
@Serializable
enum class ThemeMode { DARK, LIGHT, SYSTEM }
@Serializable
enum class ThemePalette { ORIGINAL, OCEAN, FOREST, LAVENDER }

@Serializable
data class AudioSettings(
    val gains: List<Float> = List(BAND_COUNT) { 0f },
    val eqEnabled: Boolean = true,
    val bass: Int = 0,
    val loudness: Int = 0,
    val balance: Float = 0f,
    val autoHeadroom: Boolean = true,
    val preferLegacy: Boolean = false,
    val virtualizer: Int = 0,
    val attachmentMode: AttachmentMode = AttachmentMode.AUTO,
    val frameDurationMs: Float = 10f,
) {
    fun normalized() = copy(gains = List(BAND_COUNT) { gains.getOrElse(it) { 0f }.takeIf(Float::isFinite)?.coerceIn(-12f, 12f) ?: 0f }, bass = bass.coerceIn(0, 100), loudness = loudness.coerceIn(0, 100), balance = balance.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f, virtualizer = virtualizer.coerceIn(0, 100), frameDurationMs = frameDurationMs.takeIf(Float::isFinite)?.coerceIn(5f, 100f) ?: 10f)
}

@Serializable
data class Profile(val id: String, val name: String, val settings: AudioSettings)
@Serializable
data class AppSettings(
    val audio: AudioSettings = AudioSettings(),
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    val assignments: Map<String, String> = emptyMap(),
    val theme: ThemeMode = ThemeMode.DARK,
    val showVolume: Boolean = true,
    val showReverb: Boolean = false,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val palette: ThemePalette = ThemePalette.ORIGINAL,
)

data class AudioCapabilities(val dynamicsProcessing: Boolean = false, val equalizer: Boolean = false, val virtualizer: Boolean = false, val reverb: Boolean = false, val bass: Boolean = false, val loudness: Boolean = false, val balance: Boolean = false, val virtualizerStrength: Boolean = false, val bandFrequenciesHz: List<Int> = emptyList(), val gainRangeDb: Pair<Float, Float>? = null, val availableEffects: List<String> = emptyList(), val unsupportedReason: String? = null)
enum class EnginePhase { STOPPED, STARTING, WAITING, ACTIVE, BYPASSED, DEGRADED, CONTROL_LOST, ERROR }
enum class AudioBackend(val diagnosticName: String) {
    DYNAMICS_PROCESSING("Dynamics Processing"),
    LEGACY_EQUALIZER("Legacy Equalizer"),
    MIXED("Mixed"),
}
data class AudioStatus(
    val phase: EnginePhase = EnginePhase.STOPPED,
    val sessions: Int = 0,
    val backend: AudioBackend? = null,
    val capabilities: AudioCapabilities = AudioCapabilities(),
    val globalAttachment: Boolean = false,
    val startFailed: Boolean = false,
)
data class AppState(val settings: AppSettings = AppSettings(), val status: AudioStatus = AudioStatus())

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    suspend fun updatePreferences(theme: ThemeMode, showVolume: Boolean, showReverb: Boolean)
    suspend fun updateAudio(settings: AudioSettings)
    suspend fun saveProfile(profile: Profile)
    suspend fun deleteProfile(id: String)
    suspend fun selectProfile(id: String?)
    suspend fun assignProfile(deviceKey: String, profileId: String?)
}

interface AudioEngine {
    val status: StateFlow<AudioStatus>
    fun start()
    fun apply(settings: AudioSettings)
    fun bypass(bypass: Boolean)
    fun stop()
}
