package com.quieteq.app.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.quieteq.app.core.*
import kotlin.math.roundToInt

internal class SessionEffects private constructor(
    private val effect: AudioEffect,
    private val dynamics: DynamicsProcessing?,
    private val equalizer: Equalizer?,
    private val loudness: LoudnessEnhancer?,
    private val virtualizer: Virtualizer?,
    val capabilities: AudioCapabilities,
    val backend: String,
) {
    val hasControl: Boolean get() = runCatching { effect.hasControl() }.getOrDefault(false)
    private var secondaryFailed = false
    fun secondaryProblem(settings: AudioSettings): Boolean = secondaryFailed || (settings.loudness > 0 && loudness != null && !runCatching { loudness?.hasControl() == true }.getOrDefault(false)) || (settings.virtualizer > 0 && virtualizer != null && !runCatching { virtualizer?.hasControl() == true }.getOrDefault(false))
    fun onControlChanged(callback: () -> Unit) { listOfNotNull(effect, loudness, virtualizer).forEach { e -> runCatching { e.setControlStatusListener { _, _ -> callback() } } } }
    fun apply(raw: AudioSettings, bypass: Boolean) {
        val s = raw.normalized()
        dynamics?.let { dp ->
            for (channel in 0 until dp.channelCount) {
                dp.setInputGainbyChannel(channel, AudioMath.channelGain(if (dp.channelCount < 2) s.copy(balance = 0f) else s, channel))
                for (band in 0 until BAND_COUNT) {
                    val cutoff = if (band == BAND_COUNT - 1) 20000f else kotlin.math.sqrt(BAND_FREQUENCIES_HZ[band].toFloat() * BAND_FREQUENCIES_HZ[band + 1])
                    dp.setPreEqBandByChannelIndex(channel, band, DynamicsProcessing.EqBand(true, cutoff, AudioMath.gainAt(BAND_FREQUENCIES_HZ[band], s)))
                }
            }
        }
        equalizer?.let { eq ->
            val range = eq.bandLevelRange
            for (band in 0 until eq.numberOfBands.toInt()) {
                val hz = eq.getCenterFreq(band.toShort()) / 1000
                val level = ((AudioMath.gainAt(hz, s) - AudioMath.headroom(s)) * 100).roundToInt().coerceIn(range[0].toInt(), range[1].toInt())
                eq.setBandLevel(band.toShort(), level.toShort())
            }
        }
        check(effect.setEnabled(!bypass) == AudioEffect.SUCCESS) { "Primary effect could not be enabled or bypassed" }
        secondaryFailed = false
        loudness?.takeIf { it.hasControl() }?.let { fx ->
            runCatching {
                fx.setTargetGain(s.loudness * 6)
                check(fx.setEnabled(!bypass && s.loudness > 0) == AudioEffect.SUCCESS)
            }.onFailure { e -> secondaryFailed = true; Log.w("QuietEQ", "Loudness update failed", e) }
        }
        virtualizer?.takeIf { it.hasControl() }?.let { fx ->
            runCatching {
                if (fx.strengthSupported) fx.setStrength((s.virtualizer * 10).toShort())
                check(fx.setEnabled(!bypass && s.virtualizer > 0) == AudioEffect.SUCCESS)
            }.onFailure { e -> secondaryFailed = true; Log.w("QuietEQ", "Virtualizer update failed", e) }
        }
    }
    fun release() { listOfNotNull(virtualizer, loudness, effect).forEach { runCatching { it.release() } } }
    companion object {
        fun create(session: Int, s: AudioSettings, advertised: List<String>): SessionEffects? {
            if (!s.preferLegacy) {
                var dp: DynamicsProcessing? = null
                var secondary: Virtualizer? = null
                try {
                    val config = DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, true, BAND_COUNT, false, 0, false, 0, true)
                        .setPreferredFrameDuration(s.frameDurationMs)
                        .setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 20f, -1f, 0f)).build()
                    dp = DynamicsProcessing(0, session, config)
                    val virtualizer = tryVirtualizer(session).also { secondary = it }
                    return SessionEffects(dp, dp, null, null, virtualizer, AudioCapabilities(dynamicsProcessing = true, equalizer = true, bass = true, loudness = true, balance = dp.channelCount >= 2, virtualizer = virtualizer != null, virtualizerStrength = virtualizer?.strengthSupported == true, bandFrequenciesHz = BAND_FREQUENCIES_HZ, gainRangeDb = -12f to 12f, availableEffects = advertised), "Dynamics Processing")
                } catch (e: Exception) { runCatching { secondary?.release() }; runCatching { dp?.release() }; Log.w("QuietEQ", "DynamicsProcessing unavailable for session $session", e) }
            }
            var eq: Equalizer? = null
            var secondaryLoudness: LoudnessEnhancer? = null
            var secondaryVirtualizer: Virtualizer? = null
            try {
                eq = Equalizer(0, session)
                val loudness = runCatching { LoudnessEnhancer(session) }.getOrNull().also { secondaryLoudness = it }
                val virtualizer = tryVirtualizer(session).also { secondaryVirtualizer = it }
                val frequencies = (0 until eq.numberOfBands.toInt()).map { eq.getCenterFreq(it.toShort()) / 1000 }
                val range = eq.bandLevelRange
                return SessionEffects(eq, null, eq, loudness, virtualizer, AudioCapabilities(equalizer = true, bass = true, loudness = loudness != null, virtualizer = virtualizer != null, virtualizerStrength = virtualizer?.strengthSupported == true, bandFrequenciesHz = frequencies, gainRangeDb = range[0] / 100f to range[1] / 100f, availableEffects = advertised, unsupportedReason = "Legacy equalizer cannot control stereo balance or guarantee headroom"), "Legacy Equalizer")
            } catch (e: Exception) { runCatching { secondaryVirtualizer?.release() }; runCatching { secondaryLoudness?.release() }; runCatching { eq?.release() }; Log.w("QuietEQ", "Equalizer unavailable for session $session", e); return null }
        }
        private fun tryVirtualizer(session: Int) = runCatching { Virtualizer(0, session) }.getOrNull()
    }
}
