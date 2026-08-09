package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.DeckPlayer
import com.example.audio.MicEffects
import com.example.audio.NativeAudioEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** UI state for one turntable/deck. */
data class DeckUiState(
    val title: String = "",
    val isLoaded: Boolean = false,
    val isPlaying: Boolean = false,
    val volume: Float = 1.0f,
    val positionMs: Int = 0,
    val durationMs: Int = 0
)

data class StreamConfig(
    val serverUrl: String = "stream.radiostudio.live",
    val port: String = "8000",
    val mountPoint: String = "/stream.mp3",
    val password: String = "studio_pass_2026",
    val stationName: String = "Radio Studio 104.5 FM Live",
    val genre: String = "Variety / Live Talk / Electronic",
    val bitrateKbps: Int = 128,
    val protocol: String = "Icecast v2"
)

data class SoundPad(
    val id: Int,
    val title: String,
    val iconName: String,
    val category: String,
    val isPlaying: Boolean = false
)

data class PlaylistItem(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val isPlaying: Boolean = false
)

data class StudioUiState(
    val isLive: Boolean = false,
    val isConnecting: Boolean = false,
    val uptimeSeconds: Long = 0L,
    val masterVolume: Float = 0.85f,
    val micGain: Float = 1.0f,
    val musicVolume: Float = 0.70f,
    val isMicMuted: Boolean = false,
    val isDuckingEnabled: Boolean = true,
    val vuPeakLevel: Float = 0.0f,
    val eqLowDb: Float = 2.0f,
    val eqMidDb: Float = 0.0f,
    val eqHighDb: Float = 3.0f,
    val voiceReverb: Float = 0.15f,
    val voicePitch: Float = 0.0f,
    val noiseGateThresholdDb: Float = -45.0f,
    val audioApiName: String = "AAudio (Oboe Low Latency)",
    val bufferLatencyMs: Int = 11,
    val sampleRate: Int = 48000,
    val activeChannelCount: Int = 2,
    val streamConfig: StreamConfig = StreamConfig(),
    val soundPads: List<SoundPad> = listOf(
        SoundPad(1, "AIRHORN", "campaign", "FX"),
        SoundPad(2, "APPLAUSE", "thumb_up", "Crowd"),
        SoundPad(3, "JINGLE", "radio", "Station"),
        SoundPad(4, "CHEER", "groups", "Crowd"),
        SoundPad(5, "SCRATCH", "album", "DJ"),
        SoundPad(6, "DRUMS", "music_note", "Beat"),
        SoundPad(7, "BASS DROP", "graphic_eq", "DJ"),
        SoundPad(8, "VOX ID", "record_voice_over", "Voice")
    ),
    val playlist: List<PlaylistItem> = listOf(
        PlaylistItem("1", "Studio Synth Drive", "Radio Studio ID", "03:45", isPlaying = true),
        PlaylistItem("2", "Cybernetic Morning Show Track", "DJ Antigravity", "04:12"),
        PlaylistItem("3", "Broadcast Station ID 01", "Voiceover Intro", "00:15"),
        PlaylistItem("4", "Late Night Deep Waves", "Radio Synthwave", "05:20"),
        PlaylistItem("5", "Commercial Break Promo", "Sponsor Audio", "00:30")
    ),
    val isRecordingLocally: Boolean = false,
    val recordedDurationSeconds: Long = 0L,
    val activeTab: Int = 0,
    val deckA: DeckUiState = DeckUiState(volume = 1.0f),
    val deckB: DeckUiState = DeckUiState(volume = 1.0f),
    // -1f = solo Plato A, 0f = mitad y mitad, +1f = solo Plato B
    val crossfaderPosition: Float = 0f
)

class RadioStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val audioEngine = NativeAudioEngine()
    private val micEffects = MicEffects()
    private val deckPlayerA = DeckPlayer(application)
    private val deckPlayerB = DeckPlayer(application)
    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    // When a deck auto-crossfades into the other at the end of a track, we
    // don't want the manual slider fighting it - this just tracks that an
    // automatic fade is in progress so we skip the manual path meanwhile.
    private var autoFading = false

    init {
        // Initialize Native Audio Settings
        audioEngine.nativeSetMasterVolume(_uiState.value.masterVolume)
        audioEngine.nativeSetMicGain(_uiState.value.micGain)
        audioEngine.nativeSetMusicVolume(_uiState.value.musicVolume)
        audioEngine.nativeSetNoiseGateThreshold(_uiState.value.noiseGateThresholdDb)
        applyCrossfaderGains(_uiState.value.crossfaderPosition)

        // Poll VU meter and Uptime ticker
        viewModelScope.launch {
            while (true) {
                delay(40) // ~25 FPS VU updates
                val state = _uiState.value
                val peak = if (state.isLive) {
                    audioEngine.nativeGetVuMeter()
                } else 0.0f

                _uiState.update { it.copy(vuPeakLevel = peak) }
            }
        }

        // Uptime counter
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_uiState.value.isLive) {
                    _uiState.update { it.copy(uptimeSeconds = it.uptimeSeconds + 1) }
                }
                if (_uiState.value.isRecordingLocally) {
                    _uiState.update { it.copy(recordedDurationSeconds = it.recordedDurationSeconds + 1) }
                }
            }
        }

        // Deck position/playing ticker + auto-crossfade at the end of a track
        viewModelScope.launch {
            while (true) {
                delay(250)
                _uiState.update {
                    it.copy(
                        deckA = it.deckA.copy(
                            isPlaying = deckPlayerA.isPlaying,
                            positionMs = deckPlayerA.positionMs,
                            durationMs = deckPlayerA.durationMs
                        ),
                        deckB = it.deckB.copy(
                            isPlaying = deckPlayerB.isPlaying,
                            positionMs = deckPlayerB.positionMs,
                            durationMs = deckPlayerB.durationMs
                        )
                    )
                }
                maybeAutoCrossfade()
            }
        }
    }

    fun toggleLiveBroadcast() {
        viewModelScope.launch {
            if (_uiState.value.isLive) {
                audioEngine.nativeDisconnectStream()
                audioEngine.nativeStopEngine()
                micEffects.release()
                _uiState.update { it.copy(isLive = false, uptimeSeconds = 0L) }
            } else {
                _uiState.update { it.copy(isConnecting = true) }
                val cfg = _uiState.value.streamConfig
                val portInt = cfg.port.toIntOrNull() ?: 8000
                audioEngine.nativeConnectStream(cfg.serverUrl, portInt, cfg.mountPoint, cfg.password, cfg.bitrateKbps)
                delay(600) // Fast handshake
                audioEngine.nativeStartEngine()
                // Attach the platform echo canceler / noise suppressor to the
                // mic's audio session now that the input stream is open, so
                // the phone's own speaker (playing the decks) doesn't feed
                // back into the mic as a whistle.
                val sessionId = try { audioEngine.nativeGetInputSessionId() } catch (e: Exception) { -1 }
                micEffects.attach(sessionId)
                val apiName = try { audioEngine.nativeGetAudioApiName() } catch (e: Exception) { "AAudio (Oboe Native)" }
                _uiState.update {
                    it.copy(
                        isLive = true,
                        isConnecting = false,
                        audioApiName = if (apiName.isBlank()) "AAudio (Oboe Native)" else apiName
                    )
                }
            }
        }
    }

    // ---------- DJ Decks ----------

    fun loadTrack(deck: Int, uri: Uri, displayName: String) {
        val player = if (deck == 0) deckPlayerA else deckPlayerB
        player.load(
            uri = uri,
            displayName = displayName,
            onReady = {
                updateDeckState(deck) { it.copy(title = displayName, isLoaded = true, durationMs = player.durationMs) }
            },
            onCompletion = {
                updateDeckState(deck) { it.copy(isPlaying = false, positionMs = 0) }
            }
        )
        updateDeckState(deck) { it.copy(title = displayName, isLoaded = false, isPlaying = false, positionMs = 0) }
    }

    fun toggleDeckPlayPause(deck: Int) {
        val player = if (deck == 0) deckPlayerA else deckPlayerB
        player.togglePlayPause()
        updateDeckState(deck) { it.copy(isPlaying = player.isPlaying) }
    }

    fun setDeckVolume(deck: Int, volume: Float) {
        val player = if (deck == 0) deckPlayerA else deckPlayerB
        player.setVolume(volume)
        updateDeckState(deck) { it.copy(volume = volume) }
    }

    fun seekDeck(deck: Int, ms: Int) {
        val player = if (deck == 0) deckPlayerA else deckPlayerB
        player.seekTo(ms)
        updateDeckState(deck) { it.copy(positionMs = ms) }
    }

    /** Manual crossfader: -1 = solo A ... 0 = ambos ... +1 = solo B */
    fun setCrossfader(position: Float) {
        val clamped = position.coerceIn(-1f, 1f)
        autoFading = false
        _uiState.update { it.copy(crossfaderPosition = clamped) }
        applyCrossfaderGains(clamped)
    }

    private fun applyCrossfaderGains(position: Float) {
        // Equal-power crossfade curve so the perceived loudness stays
        // constant while mixing, instead of a straight-line volume dip.
        val t = (position + 1f) / 2f // 0..1
        val gainA = sqrt((1f - t).toDouble()).toFloat()
        val gainB = sqrt(t.toDouble()).toFloat()
        val volA = _uiState.value.deckA.volume
        val volB = _uiState.value.deckB.volume
        deckPlayerA.setVolume(gainA * volA)
        deckPlayerB.setVolume(gainB * volB)
    }

    private fun maybeAutoCrossfade() {
        if (autoFading) return
        val state = _uiState.value
        val aEnding = deckPlayerA.isNearEnd(4000) && state.deckB.isLoaded && state.crossfaderPosition < 0.9f
        val bEnding = deckPlayerB.isNearEnd(4000) && state.deckA.isLoaded && state.crossfaderPosition > -0.9f

        if (aEnding) {
            autoCrossfadeTo(target = 1f)
        } else if (bEnding) {
            autoCrossfadeTo(target = -1f)
        }
    }

    private fun autoCrossfadeTo(target: Float) {
        autoFading = true
        viewModelScope.launch {
            if (target > 0f && !deckPlayerB.isPlaying && deckPlayerB.isLoaded) deckPlayerB.play()
            if (target < 0f && !deckPlayerA.isPlaying && deckPlayerA.isLoaded) deckPlayerA.play()

            val steps = 40
            val start = _uiState.value.crossfaderPosition
            for (i in 1..steps) {
                if (!autoFading) return@launch
                val pos = start + (target - start) * (i / steps.toFloat())
                _uiState.update { it.copy(crossfaderPosition = pos) }
                applyCrossfaderGains(pos)
                delay(100) // ~4s total fade
            }
            autoFading = false
        }
    }

    private fun updateDeckState(deck: Int, transform: (DeckUiState) -> DeckUiState) {
        _uiState.update {
            if (deck == 0) it.copy(deckA = transform(it.deckA)) else it.copy(deckB = transform(it.deckB))
        }
    }

    override fun onCleared() {
        super.onCleared()
        deckPlayerA.release()
        deckPlayerB.release()
        micEffects.release()
    }

    fun setMasterVolume(volume: Float) {
        _uiState.update { it.copy(masterVolume = volume) }
        audioEngine.nativeSetMasterVolume(volume)
    }

    fun setMicGain(gain: Float) {
        _uiState.update { it.copy(micGain = gain) }
        if (!_uiState.value.isMicMuted) {
            audioEngine.nativeSetMicGain(gain)
        }
    }

    fun setMusicVolume(volume: Float) {
        _uiState.update { it.copy(musicVolume = volume) }
        audioEngine.nativeSetMusicVolume(volume)
    }

    fun toggleMicMute() {
        val newMute = !_uiState.value.isMicMuted
        _uiState.update { it.copy(isMicMuted = newMute) }
        audioEngine.nativeSetMicGain(if (newMute) 0.0f else _uiState.value.micGain)
    }

    fun toggleDucking() {
        _uiState.update { it.copy(isDuckingEnabled = !it.isDuckingEnabled) }
    }

    fun setEqGains(low: Float, mid: Float, high: Float) {
        _uiState.update { it.copy(eqLowDb = low, eqMidDb = mid, eqHighDb = high) }
    }

    fun setVoiceEffects(reverb: Float, pitch: Float, gate: Float) {
        _uiState.update { it.copy(voiceReverb = reverb, voicePitch = pitch, noiseGateThresholdDb = gate) }
        audioEngine.nativeSetNoiseGateThreshold(gate)
    }

    fun triggerSoundPad(padId: Int) {
        audioEngine.nativePlaySoundEffect(padId)
        _uiState.update { state ->
            val updatedPads = state.soundPads.map { pad ->
                if (pad.id == padId) pad.copy(isPlaying = true) else pad
            }
            state.copy(soundPads = updatedPads)
        }
        viewModelScope.launch {
            delay(1200)
            _uiState.update { state ->
                val updatedPads = state.soundPads.map { pad ->
                    if (pad.id == padId) pad.copy(isPlaying = false) else pad
                }
                state.copy(soundPads = updatedPads)
            }
        }
    }

    fun updateStreamConfig(config: StreamConfig) {
        _uiState.update { it.copy(streamConfig = config) }
    }

    fun toggleLocalRecording() {
        val nextRec = !_uiState.value.isRecordingLocally
        _uiState.update { it.copy(isRecordingLocally = nextRec, recordedDurationSeconds = 0L) }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(activeTab = tabIndex) }
    }

    fun playPlaylistItem(itemId: String) {
        _uiState.update { state ->
            val updated = state.playlist.map { item ->
                item.copy(isPlaying = item.id == itemId)
            }
            state.copy(playlist = updated)
        }
    }
}
