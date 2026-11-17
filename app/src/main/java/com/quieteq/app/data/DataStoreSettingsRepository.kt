package com.quieteq.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quieteq.app.core.*
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray

private val Context.settingsStore by preferencesDataStore(name = "quiet_eq")
private val SETTINGS_KEY = stringPreferencesKey("settings_v1")
private val codec = Json { encodeDefaults = true; ignoreUnknownKeys = false }
private const val MAX_BACKUP_BYTES = 1024 * 1024
private const val MAX_PROFILES = 200

private fun preset(id: String, name: String, gains: List<Float>) = Profile(id, name, AudioSettings(gains = gains))
val defaultProfiles = listOf(
    preset("flat", "Flat", List(10) { 0f }),
    preset("bass-boost", "Bass Boost", listOf(6f, 5f, 4f, 2f, 1f, 0f, 0f, 0f, 0f, 0f)),
    preset("treble-boost", "Treble Boost", listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f)),
    preset("vocal", "Vocal", listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f)),
    preset("balanced", "Balanced", listOf(3f, 2f, 1f, 0f, -1f, 0f, 1f, 2f, 2f, 3f)),
)
private fun defaults() = AppSettings(profiles = defaultProfiles)

@Serializable
private data class Backup(val version: Int, val settings: AppSettings)

/** All read-modify-write operations run within DataStore's serialized transaction. */
class DataStoreSettingsRepository(context: Context, private val scope: CoroutineScope) : SettingsRepository {
    private val store = context.applicationContext.settingsStore
    val settingsChanges = store.data.map { prefs -> decodeStored(prefs[SETTINGS_KEY]) }
    private val mutableSettings = MutableStateFlow(defaults())
    override val settings = mutableSettings.asStateFlow()
    private val mutableLoaded = MutableStateFlow(false)
    val isLoaded = mutableLoaded.asStateFlow()
    private val mutableLoadError = MutableStateFlow<String?>(null)
    val loadError = mutableLoadError.asStateFlow()
    private var loadJob: Job? = null
    init { retryLoading() }
    fun retryLoading() {
        loadJob?.cancel()
        mutableLoadError.value = null
        loadJob = scope.launch {
            try { settingsChanges.collect { mutableSettings.value = it; mutableLoaded.value = true; mutableLoadError.value = null } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableLoaded.value = false; mutableLoadError.value = e.message ?: "Unable to load settings" }
        }
    }

    private suspend fun mutate(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val updated = transform(decodeStored(prefs[SETTINGS_KEY]))
            validateSettings(updated)
            prefs[SETTINGS_KEY] = codec.encodeToString(AppSettings.serializer(), updated)
        }
    }
    suspend fun modifyPreferences(transform: (AppSettings) -> AppSettings) = mutate { current ->
        val changed = transform(current)
        current.copy(theme = changed.theme, showVolume = changed.showVolume, showReverb = changed.showReverb)
    }
    override suspend fun updatePreferences(theme: ThemeMode, showVolume: Boolean, showReverb: Boolean) = mutate {
        it.copy(theme = theme, showVolume = showVolume, showReverb = showReverb)
    }
    suspend fun modifyAudio(transform: (AudioSettings) -> AudioSettings) = mutate {
        it.copy(audio = transform(it.audio).normalized(), selectedProfileId = null)
    }
    override suspend fun updateAudio(settings: AudioSettings) = mutate {
        it.copy(audio = settings.normalized(), selectedProfileId = null)
    }
    override suspend fun saveProfile(profile: Profile) = mutate { current ->
        val index = current.profiles.indexOfFirst { it.id == profile.id }
        val profiles = current.profiles.toMutableList()
        if (index < 0) profiles.add(profile) else profiles[index] = profile
        current.copy(profiles = profiles, selectedProfileId = profile.id, audio = profile.settings)
    }
    override suspend fun deleteProfile(id: String) = mutate { current ->
        deleteProfileFrom(current, id)
    }
    override suspend fun selectProfile(id: String?) = mutate { current ->
        if (id == null) current.copy(selectedProfileId = null) else {
            val profile = requireNotNull(current.profiles.find { it.id == id }) { "Profile does not exist" }
            current.copy(selectedProfileId = id, audio = profile.settings)
        }
    }
    override suspend fun assignProfile(deviceKey: String, profileId: String?) = mutate { current ->
        require(validDeviceKey(deviceKey)) { "Invalid device address" }
        require(profileId == null || current.profiles.any { it.id == profileId }) { "Profile does not exist" }
        current.copy(assignments = if (profileId == null) current.assignments - deviceKey else current.assignments + (deviceKey to profileId))
    }
    suspend fun renameProfile(id: String, name: String) = mutate { current ->
        renameProfileIn(current, id, name)
    }
    suspend fun duplicateProfile(id: String, name: String): String {
        val newId = UUID.randomUUID().toString()
        mutate { current ->
            duplicateProfileIn(current, id, newId, name)
        }
        return newId
    }
    suspend fun applyDeviceProfile(deviceKey: String) = mutate { current ->
        val profile = current.profiles.find { it.id == current.assignments[deviceKey] }
        if (profile == null) current else current.copy(selectedProfileId = profile.id, audio = profile.settings)
    }
    suspend fun loadSettings(): AppSettings = decodeStored(store.data.first()[SETTINGS_KEY])
    suspend fun exportBackup(): String {
        // Read from the store rather than the asynchronously updated StateFlow.
        val current = store.data.first()
        return encodeBackup(decodeStored(current[SETTINGS_KEY]))
    }
    suspend fun importBackup(json: String) {
        validatedImport(json) { imported ->
            store.edit { it[SETTINGS_KEY] = codec.encodeToString(AppSettings.serializer(), imported) }
        }
        if (loadError.value != null) retryLoading()
    }
}

private fun decodeStored(raw: String?): AppSettings = if (raw == null) defaults() else codec.decodeFromString(AppSettings.serializer(), raw).also(::validateSettings)
private fun validDeviceKey(key: String) = key.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}"))
private fun validId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{1,64}"))
private fun validName(name: String) = name.isNotBlank() && name.length <= 80 && name.none { it.isISOControl() }
private fun validateAudio(a: AudioSettings) {
    require(a.gains.size == BAND_COUNT && a.gains.all { it.isFinite() && it in -12f..12f }) { "Invalid EQ gains" }
    require(a.bass in 0..100 && a.loudness in 0..100 && a.virtualizer in 0..100) { "Invalid effect strength" }
    require(a.balance.isFinite() && a.balance in -1f..1f && a.frameDurationMs.isFinite() && a.frameDurationMs in 5f..100f) { "Invalid audio settings" }
}
internal fun validateSettings(s: AppSettings) {
    validateAudio(s.audio)
    require(s.profiles.size <= MAX_PROFILES) { "Too many profiles" }
    require(s.profiles.map { it.id }.toSet().size == s.profiles.size) { "Duplicate profile ID" }
    s.profiles.forEach { require(validId(it.id) && validName(it.name)) { "Invalid profile ID or name" }; validateAudio(it.settings) }
    val ids = s.profiles.map { it.id }.toSet()
    require(s.selectedProfileId == null || s.selectedProfileId in ids) { "Selected profile does not exist" }
    require(s.assignments.size <= 500 && s.assignments.all { validDeviceKey(it.key) && it.value in ids }) { "Invalid device assignment" }
}
private fun boundedDepth(text: String, max: Int): Boolean {
    var depth = 0; var quoted = false; var escaped = false
    for (c in text) {
        if (quoted) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false }
        else when (c) { '"' -> quoted = true; '{', '[' -> { depth++; if (depth > max) return false }; '}', ']' -> depth-- }
    }
    return true
}



internal fun parseBackup(json: String): AppSettings {
    require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup is too large" }
    require(boundedDepth(json, 32)) { "Backup nesting is too deep" }
    validateBackupShape(codec.parseToJsonElement(json))
    val backup = codec.decodeFromString(Backup.serializer(), json)
    require(backup.version == 1) { "Unsupported backup version" }
    validateSettings(backup.settings)
    return backup.settings
}





internal fun encodeBackup(settings: AppSettings): String {
    validateSettings(settings)
    return codec.encodeToString(Backup.serializer(), Backup(version = 1, settings = settings))
}



internal suspend fun validatedImport(json: String, write: suspend (AppSettings) -> Unit) {
    val imported = parseBackup(json)
    write(imported)
}


internal fun deleteProfileFrom(current: AppSettings, id: String): AppSettings =
    current.copy(profiles = current.profiles.filterNot { it.id == id },
        selectedProfileId = current.selectedProfileId.takeUnless { it == id },
        assignments = current.assignments.filterValues { it != id })
internal fun renameProfileIn(current: AppSettings, id: String, name: String): AppSettings {
    require(current.profiles.any { it.id == id }) { "Profile does not exist" }
    return current.copy(profiles = current.profiles.map { if (it.id == id) it.copy(name = name) else it })
}
internal fun duplicateProfileIn(current: AppSettings, id: String, newId: String, name: String): AppSettings {
    val source = requireNotNull(current.profiles.find { it.id == id }) { "Profile does not exist" }
    return current.copy(profiles = current.profiles + source.copy(id = newId, name = name))
}

private val settingsKeys = setOf("audio", "profiles", "selectedProfileId", "assignments", "theme", "showVolume", "showReverb")
private val audioKeys = setOf("gains", "eqEnabled", "bass", "loudness", "balance", "autoHeadroom", "preferLegacy", "virtualizer", "attachmentMode", "frameDurationMs")
private fun requireObjectKeys(value: kotlinx.serialization.json.JsonElement?, keys: Set<String>): JsonObject {
    require(value is JsonObject && value.keys == keys) { "Backup is incomplete or has unknown fields" }
    return value
}
private fun validateBackupShape(value: kotlinx.serialization.json.JsonElement) {
    val root = requireObjectKeys(value, setOf("version", "settings"))
    val settings = requireObjectKeys(root["settings"], settingsKeys)
    requireObjectKeys(settings["audio"], audioKeys)
    val profiles = settings["profiles"]
    require(profiles is JsonArray && profiles.size <= MAX_PROFILES) { "Invalid profiles" }
    profiles.forEach {
        val profile = requireObjectKeys(it, setOf("id", "name", "settings"))
        requireObjectKeys(profile["settings"], audioKeys)
    }
}
