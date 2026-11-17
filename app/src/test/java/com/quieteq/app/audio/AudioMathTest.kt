package com.quieteq.app.audio

import com.quieteq.app.core.AudioSettings
import org.junit.Assert.*
import org.junit.Test

class AudioMathTest {
    @Test fun interpolationAndBounds() {
        val s = AudioSettings(gains = listOf(4f, 8f))
        assertEquals(4f, AudioMath.gainAt(31, s), 0.001f)
        assertEquals(8f, AudioMath.gainAt(62, s), 0.001f)
        assertTrue(AudioMath.gainAt(45, s) in 4f..8f)
        assertEquals(0f, AudioMath.gainAt(16000, s), 0.001f)
    }
    @Test fun bassHeadroomAndBalance() {
        val s = AudioSettings(bass = 100, balance = 1f)
        assertEquals(6f, AudioMath.gainAt(31, s), 0.001f)
        assertEquals(0f, AudioMath.gainAt(1000, s), 0.001f)
        assertEquals(6f, AudioMath.headroom(s), 0.001f)
        assertEquals(-30f, AudioMath.channelGain(s, 0), 0.001f)
        assertEquals(-6f, AudioMath.channelGain(s, 1), 0.001f)
    }
    @Test fun normalizeRejectsNonFiniteAndOutOfRange() {
        val s = AudioSettings(gains = listOf(Float.NaN, 30f), balance = Float.NaN, bass = -5, loudness = 200).normalized()
        assertEquals(10, s.gains.size)
        assertEquals(0f, s.gains[0], 0f)
        assertEquals(12f, s.gains[1], 0f)
        assertEquals(0f, s.balance, 0f)
        assertEquals(0, s.bass)
        assertEquals(100, s.loudness)
    }
}
