package com.quieteq.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only inventory and effect probe against a private, never-played AudioTrack session. */
@RunWith(AndroidJUnit4::class)
class EffectDiagnosticsTest {
    @Test fun reportRealEffectControlAndReadback() {
        val descriptors = AudioEffect.queryEffects()
        descriptors.forEach { Log.i("QuietEQProbe", "effect name=${it.name} implementor=${it.implementor} type=${it.type} uuid=${it.uuid}") }
        val format = AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
        val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(format).setBufferSizeInBytes(4096).setTransferMode(AudioTrack.MODE_STREAM).setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE).build()
        try {
            assertEquals(AudioTrack.STATE_INITIALIZED, track.state)
            val id = track.audioSessionId
            Log.i("QuietEQProbe", "private nonplaying session=$id")
            var controllable = false
            var dpReadback: Float? = null
            var dpBandCount: Int? = null
            runCatching {
                val cfg = DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, true, 10, false, 0, false, 0, true).build()
                val dp = DynamicsProcessing(0, id, cfg)
                try {
                    Log.i("QuietEQProbe", "DP control=${dp.hasControl()} channels=${dp.channelCount} bands=${dp.getPreEqByChannelIndex(0).bandCount}")
                    if (dp.hasControl()) { dpBandCount = dp.getPreEqByChannelIndex(0).bandCount; dp.setInputGainbyChannel(0, -1f); dpReadback = dp.getInputGainByChannelIndex(0); controllable = true; Log.i("QuietEQProbe", "DP gain readback=$dpReadback") }
                } finally { dp.release() }
            }.onFailure { Log.w("QuietEQProbe", "DP unavailable", it) }
            runCatching {
                val eq = Equalizer(0, id)
                try {
                    Log.i("QuietEQProbe", "EQ control=${eq.hasControl()} bands=${eq.numberOfBands} range=${eq.bandLevelRange.toList()}")
                    if (eq.hasControl()) controllable = true
                    for (i in 0 until eq.numberOfBands.toInt()) Log.i("QuietEQProbe", "EQ band=$i hz=${eq.getCenterFreq(i.toShort()) / 1000} level=${eq.getBandLevel(i.toShort())}")
                } finally { eq.release() }
            }.onFailure { Log.w("QuietEQProbe", "EQ unavailable", it) }
            assertTrue("No controllable backend attached to private track session", controllable)
            if (dpBandCount != null) {
                assertEquals("DP band count", 10, dpBandCount)
                assertNotNull("DP gain readback missing", dpReadback)
                assertEquals("DP gain readback", -1f, dpReadback!!, 0.01f)
            }
        } finally { track.release() }
    }
}
