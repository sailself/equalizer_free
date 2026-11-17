package com.quieteq.app.audio

import com.quieteq.app.core.*
import kotlin.math.ln

internal object AudioMath {
    fun gainAt(frequency: Int, settings: AudioSettings): Float {
        val s = settings.normalized()
        val f = frequency.coerceAtLeast(1).toDouble()
        val index = BAND_FREQUENCIES_HZ.indexOfFirst { it >= f }.let { if (it < 0) BAND_COUNT - 1 else it }
        val gain = if (index == 0) s.gains[0] else {
            val lo = BAND_FREQUENCIES_HZ[index - 1].toDouble()
            val hi = BAND_FREQUENCIES_HZ[index].toDouble()
            val t = ((ln(f) - ln(lo)) / (ln(hi) - ln(lo))).coerceIn(0.0, 1.0).toFloat()
            s.gains[index - 1] * (1 - t) + s.gains[index] * t
        }
        val bassShape = when { frequency <= 62 -> 1f; frequency >= 250 -> 0f; else -> ((ln(250.0) - ln(f)) / (ln(250.0) - ln(62.0))).toFloat() }
        return ((if (s.eqEnabled) gain else 0f) + s.bass * 0.06f * bassShape).coerceIn(-12f, 12f)
    }
    fun headroom(s: AudioSettings): Float = if (!s.autoHeadroom) 0f else BAND_FREQUENCIES_HZ.maxOf { gainAt(it, s) }.coerceAtLeast(0f)
    fun channelGain(s: AudioSettings, channel: Int): Float {
        val balance = s.normalized().balance
        val attenuation = if (channel == 0) balance.coerceAtLeast(0f) else (-balance).coerceAtLeast(0f)
        return -headroom(s) + s.loudness.coerceIn(0, 100) * 0.06f - attenuation * 24f
    }
}
