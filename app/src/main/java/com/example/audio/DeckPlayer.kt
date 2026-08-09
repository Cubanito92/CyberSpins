package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlin.math.max
import kotlin.math.min

/**
 * One DJ deck (turntable). Wraps a MediaPlayer so loading, playing and
 * volume control of a local song "just works" without needing a custom
 * audio decoder - simple and reliable, matching what was asked for.
 */
class DeckPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    var title: String = ""
        private set
    var isLoaded: Boolean = false
        private set
    var volume: Float = 1.0f
        private set

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    val durationMs: Int
        get() = try { mediaPlayer?.duration ?: 0 } catch (e: Exception) { 0 }

    val positionMs: Int
        get() = try { mediaPlayer?.currentPosition ?: 0 } catch (e: Exception) { 0 }

    fun load(uri: Uri, displayName: String, onReady: () -> Unit, onCompletion: () -> Unit) {
        release()
        title = displayName
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, uri)
            setOnPreparedListener {
                isLoaded = true
                setVolume(volume, volume)
                onReady()
            }
            setOnCompletionListener {
                onCompletion()
            }
            prepareAsync()
        }
    }

    fun play() {
        if (isLoaded) mediaPlayer?.start()
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    fun seekTo(ms: Int) {
        mediaPlayer?.seekTo(ms)
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(volume, volume)
    }

    /** True once playback is within [thresholdMs] of the end of the track. */
    fun isNearEnd(thresholdMs: Int): Boolean {
        val dur = durationMs
        if (!isLoaded || dur <= 0) return false
        return isPlaying && (dur - positionMs) in 0..thresholdMs
    }

    fun release() {
        mediaPlayer?.apply {
            try { stop() } catch (e: Exception) { /* not started yet */ }
            release()
        }
        mediaPlayer = null
        isLoaded = false
        title = ""
    }
}
