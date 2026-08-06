package com.example.audio

import android.util.Log

class NativeAudioEngine {

    companion object {
        private const val TAG = "NativeAudioEngine"

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
    external fun nativePlaySoundEffect(effectId: Int)
    external fun nativeGetVuMeter(): Float
    external fun nativeIsEngineLive(): Boolean
    external fun nativeGetAudioApiName(): String
    external fun nativeConnectStream(host: String, port: Int, mount: String, pass: String, bitrate: Int): Boolean
    external fun nativeDisconnectStream()
    external fun nativeGetStreamStatus(): Int
    external fun nativeUpdateMetadata(title: String)
}
