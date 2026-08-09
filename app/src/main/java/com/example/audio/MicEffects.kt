package com.example.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/**
 * Attaches Android's built-in platform Acoustic Echo Canceler, Noise
 * Suppressor and Automatic Gain Control to the microphone's audio session.
 *
 * This is what stops the phone's own speaker (playing the music) from
 * feeding back into the mic as a whistle/beep: the AEC removes the part of
 * the mic signal that correlates with what the device is currently playing.
 * The native engine's InputPreset.VoiceCommunication already asks Android to
 * do this automatically, but attaching the effects explicitly here makes it
 * work reliably across every device, including ones that don't auto-apply
 * it for that preset.
 */
class MicEffects {
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    fun attach(sessionId: Int) {
        release()
        if (sessionId <= 0) return

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) {
            Log.w("MicEffects", "AEC not available: ${e.message}")
        }

        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) {
            Log.w("MicEffects", "NoiseSuppressor not available: ${e.message}")
        }

        try {
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) {
            Log.w("MicEffects", "AGC not available: ${e.message}")
        }
    }

    fun release() {
        echoCanceler?.release()
        noiseSuppressor?.release()
        agc?.release()
        echoCanceler = null
        noiseSuppressor = null
        agc = null
    }
}
