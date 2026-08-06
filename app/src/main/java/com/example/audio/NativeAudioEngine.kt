package com.example.audio

import android.util.Log

class NativeAudioEngine {

    companion object {
        private const val TAG = "NativeAudioEngine"

        // Debe coincidir con el enum StreamState de IcecastStreamer.h
        const val STREAM_DISCONNECTED = 0
        const val STREAM_CONNECTING = 1
        const val STREAM_STREAMING = 2
        const val STREAM_ERROR = 3

        // Debe coincidir con el enum StreamProtocol de IcecastStreamer.h
        const val PROTOCOL_ICECAST = 0
        const val PROTOCOL_SHOUTCAST = 1

        init {
            try {
                System.loadLibrary("radioengine")
                Log.i(TAG, "Native library 'radioengine' loaded successfully.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library 'radioengine': ${e.message}")
            }
        }
    }

    external fun nativeStartEngine(): Boolean
    external fun nativeStopEngine()
    external fun nativeSetMasterVolume(volume: Float)
    external fun nativeSetMicGain(gain: Float)
    external fun nativeSetMusicVolume(volume: Float)
    external fun nativeSetDuckingEnabled(enabled: Boolean)
    external fun nativeSetEqGains(lowDb: Float, midDb: Float, highDb: Float)
    external fun nativeSetVoiceEffects(reverb: Float, pitchSemitones: Float, gateDb: Float)
    external fun nativePlaySoundEffect(effectId: Int)
    external fun nativeGetVuMeter(): Float
    external fun nativeIsEngineLive(): Boolean
    external fun nativeGetAudioApiName(): String
    external fun nativeConnectStream(
        host: String,
        port: Int,
        mount: String,
        pass: String,
        bitrate: Int,
        protocol: Int,
        stationName: String
    ): Boolean
    external fun nativeDisconnectStream()
    external fun nativeGetStreamStatus(): Int
    external fun nativeUpdateMetadata(title: String)

    // Deck de música: se alimenta con PCM 16-bit ya decodificado desde Kotlin
    // (MediaExtractor/MediaCodec) y el motor nativo lo remuestrea/mezcla.
    external fun nativeFeedMusicPcm(pcm: ShortArray, frames: Int, channels: Int, sampleRate: Int)
    external fun nativeSetMusicPlaying(playing: Boolean)
    external fun nativeClearMusicBuffer()
}
