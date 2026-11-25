package com.quieteq.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.quieteq.app.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The controller requests foreground start from a visible activity; the service owns effects. */
class AudioController(context: Context) : AudioEngine {
    private val app = context.applicationContext
    override val status: StateFlow<AudioStatus> = AudioRuntime.status
    val bluetoothStatus: StateFlow<com.quieteq.app.data.BluetoothMonitorState> = AudioService.bluetoothState
    override fun start() {
        AudioRuntime.markStarting()
        try {
            app.startForegroundService(Intent(app, AudioService::class.java).setAction(AudioService.START))
        } catch (e: Exception) {
            Log.e("QuietEQ", "Could not start audio service", e)
            AudioRuntime.fail("Could not start audio service: ${e.message ?: e.javaClass.simpleName}")
        }
    }
    override fun apply(settings: AudioSettings) = AudioRuntime.apply(settings)
    override fun bypass(bypass: Boolean) { if (status.value.phase != EnginePhase.STOPPED) app.startService(Intent(app, AudioService::class.java).setAction(AudioService.BYPASS).putExtra("bypass", bypass)) }
    fun refreshBluetooth() { if (status.value.phase != EnginePhase.STOPPED) app.startService(Intent(app, AudioService::class.java).setAction(AudioService.REFRESH)) }
    override fun stop() { app.startService(Intent(app, AudioService::class.java).setAction(AudioService.STOP)) }
}

internal object AudioRuntime : AudioEngine {
    override val status = MutableStateFlow(AudioStatus())
    private var context: Context? = null
    private val sessions = SessionTracker()
    private val effects = mutableMapOf<Int, SessionEffects>()
    private var settings = AudioSettings()
    private var bypassed = false
    private val handler = Handler(HandlerThread("QuietEQ-effects").apply { start() }.looper)
    @Volatile private var pendingSettings = AudioSettings()
    private val applyPending = Runnable { applyOnWorker(pendingSettings) }
    private var advertised = emptyList<String>()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioRuntime.context == null) return
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            if (id <= 0) return
            val owner = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown"
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> sessions.open(id, owner)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> sessions.close(id, owner)
                else -> return
            }
            rebuild()
        }
    }
    fun markStarting() { status.value = status.value.copy(phase = EnginePhase.STARTING, startFailed = false) }
    fun initialize(app: Context, initial: AudioSettings, onError: (Throwable) -> Unit) { handler.post { try { settings = initial.normalized(); initializeOnWorker(app) } catch (e: Exception) { stopOnWorker(); onError(e) } } }
    private fun initializeOnWorker(app: Context) {
        if (context != null) return
        context = app.applicationContext
        advertised = runCatching { AudioEffect.queryEffects().map { "${it.name} (${it.implementor})" } }.getOrDefault(emptyList())
        Log.i("QuietEQ", "Advertised audio effects: $advertised")
        val filter = IntentFilter().apply { addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION); addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION) }
        ContextCompat.registerReceiver(app, receiver, filter, null, handler, ContextCompat.RECEIVER_EXPORTED)
        rebuild()
    }
    override fun start() { handler.post { if (context != null) rebuild() } }
    override fun apply(settings: AudioSettings) { pendingSettings = settings.normalized(); handler.removeCallbacks(applyPending); handler.post(applyPending) }
    private fun applyOnWorker(settings: AudioSettings) {
        val old = this.settings
        this.settings = settings.normalized()
        if (context == null) return
        if (old.preferLegacy != settings.preferLegacy || old.attachmentMode != settings.attachmentMode || old.frameDurationMs != settings.frameDurationMs) rebuild(true) else updateEffects()
    }
    override fun bypass(bypass: Boolean) { handler.post { bypassed = bypass; if (context != null) updateEffects() } }
    override fun stop() { handler.post { stopOnWorker() } }
    fun fail(message: String) {
        handler.post {
            stopOnWorker()
            status.value = AudioStatus(
                phase = EnginePhase.ERROR,
                capabilities = AudioCapabilities(availableEffects = advertised, unsupportedReason = message),
                startFailed = true,
            )
        }
    }
    private fun stopOnWorker() {
        bypassed = false
        effects.values.forEach { it.release() }; effects.clear(); sessions.clear()
        context?.let { runCatching { it.unregisterReceiver(receiver) } }; context = null
        status.value = AudioStatus(capabilities = AudioCapabilities(availableEffects = advertised))
    }
    private fun rebuild(force: Boolean = false) {
        val desired = sessions.desired(settings.attachmentMode)
        // Release obsolete effects before attaching replacements, especially global versus per-player.
        effects.keys.toList().filter { force || it !in desired }.forEach { effects.remove(it)?.release() }
        desired.filter { it !in effects }.forEach { id -> SessionEffects.create(id, settings, advertised)?.let { effect ->
            effects[id] = effect
            effect.onControlChanged { handler.post { if (context != null) updateEffects() } }
        } }
        if (settings.attachmentMode == AttachmentMode.AUTO && desired.isNotEmpty() && 0 !in desired && effects.isEmpty()) SessionEffects.create(0, settings, advertised)?.let { fallback -> effects[0] = fallback; fallback.onControlChanged { handler.post { if (context != null) updateEffects() } } }
        updateEffects()
    }
    private fun combinedCapabilities(message: String): AudioCapabilities {
        val all = effects.values.map { it.capabilities }
        if (all.isEmpty()) return AudioCapabilities(availableEffects = advertised, unsupportedReason = message)
        val representative = all.minBy { it.bandFrequenciesHz.size }
        val ranges = all.mapNotNull { it.gainRangeDb }
        val range = if (ranges.size == all.size) {
            val low = ranges.maxOf { it.first }
            val high = ranges.minOf { it.second }
            if (low <= high) low to high else null
        } else null
        val mixed = effects.values.map { it.backend }.distinct().size > 1
        return representative.copy(
            dynamicsProcessing = all.all { it.dynamicsProcessing }, equalizer = all.all { it.equalizer },
            virtualizer = all.all { it.virtualizer }, reverb = all.all { it.reverb },
            bass = all.all { it.bass }, loudness = all.all { it.loudness }, balance = all.all { it.balance },
            virtualizerStrength = all.all { it.virtualizerStrength }, gainRangeDb = range,
            unsupportedReason = if (mixed) "Mixed audio backends; settings are mapped to each session's bands and some controls may be unavailable" else representative.unsupportedReason,
        )
    }
    private fun updateEffects() {
        var failures = 0
        effects.toMap().forEach { (id, effect) ->
            if (effect.hasControl) {
                val error = runCatching { effect.apply(settings, bypassed) }.exceptionOrNull()
                if (error != null) {
                    Log.w("QuietEQ", "Cannot update session $id using ${effect.backend.diagnosticName}", error)
                    if (effect.backend == AudioBackend.DYNAMICS_PROCESSING && !settings.preferLegacy) {
                        // The advertised processor can construct successfully but reject parameters.
                        // Release it before trying legacy to avoid processing the session twice.
                        effects.remove(id)?.release()
                        val fallback = SessionEffects.create(id, settings.copy(preferLegacy = true), advertised)
                        if (fallback != null) {
                            effects[id] = fallback
                            fallback.onControlChanged { handler.post { if (context != null) updateEffects() } }
                            if (!fallback.hasControl || runCatching { fallback.apply(settings, bypassed) }.isFailure) failures++
                        } else failures++
                    } else failures++
                }
            }
        }
        val first = effects.values.firstOrNull()
        val lost = effects.values.any { !it.hasControl }
        val expected = when (settings.attachmentMode) { AttachmentMode.GLOBAL -> 1; AttachmentMode.PLAYERS_ONLY -> sessions.sessions.size; AttachmentMode.AUTO -> sessions.sessions.size.coerceAtLeast(1) }
        val phase = when { expected == 0 -> EnginePhase.WAITING; first == null -> EnginePhase.ERROR; lost -> EnginePhase.CONTROL_LOST; failures > 0 || effects.size < expected || effects.values.any { it.secondaryProblem(settings) } || (settings.attachmentMode == AttachmentMode.AUTO && sessions.sessions.isNotEmpty() && effects.containsKey(0)) -> EnginePhase.DEGRADED; bypassed -> EnginePhase.BYPASSED; else -> EnginePhase.ACTIVE }
        val message = when (phase) { EnginePhase.WAITING -> "Waiting for a playback session"; EnginePhase.ERROR -> "No audio effect could be attached"; EnginePhase.CONTROL_LOST -> "Another app controls an audio effect"; EnginePhase.DEGRADED -> "Some sessions or settings could not be applied"; EnginePhase.BYPASSED -> "Bypassed"; else -> if (effects.containsKey(0)) "Global effect attached; playback support varies by device" else "Attached to ${effects.size} playback session(s)" }
        val backends = effects.values.map { it.backend }.distinct()
        status.value = AudioStatus(
            phase = phase,
            sessions = effects.size,
            backend = if (backends.size > 1) AudioBackend.MIXED else first?.backend,
            capabilities = combinedCapabilities(message),
            globalAttachment = effects.containsKey(0),
        )
        Log.i("QuietEQ", "status=$phase sessions=${effects.keys} backend=${first?.backend?.diagnosticName} message=$message")
    }
}
