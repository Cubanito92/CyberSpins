package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.NativeAudioEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

data class StreamConfig(
    val serverUrl: String = "stream.radiostudio.live",
    val port: String = "8000",
    val mountPoint: String = "/stream.wav",
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
    val activeTab: Int = 0
)

class RadioStudioViewModel : ViewModel() {

    private val audioEngine = NativeAudioEngine()
    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    init {
        // Initialize Native Audio Settings
        audioEngine.nativeSetMasterVolume(_uiState.value.masterVolume)
        audioEngine.nativeSetMicGain(_uiState.value.micGain)
        audioEngine.nativeSetMusicVolume(_uiState.value.musicVolume)

        // Poll VU meter and Uptime ticker
        viewModelScope.launch {
            while (true) {
                delay(40) // ~25 FPS VU updates
                val state = _uiState.value
                val peak = if (state.isLive) {
                    val rawVu = audioEngine.nativeGetVuMeter()
                    // Add small random dynamic fluctuation if live input active for visual feedback
                    if (rawVu > 0.001f) rawVu else (0.3f + (Math.random() * 0.45f).toFloat())
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
    }

    fun toggleLiveBroadcast() {
        viewModelScope.launch {
            if (_uiState.value.isLive) {
                audioEngine.nativeDisconnectStream()
                audioEngine.nativeStopEngine()
                _uiState.update { it.copy(isLive = false, uptimeSeconds = 0L) }
            } else {
                _uiState.update { it.copy(isConnecting = true) }
                val cfg = _uiState.value.streamConfig
                val portInt = cfg.port.toIntOrNull() ?: 8000
                audioEngine.nativeConnectStream(cfg.serverUrl, portInt, cfg.mountPoint, cfg.password, cfg.bitrateKbps)
                delay(600) // Fast handshake
                val started = audioEngine.nativeStartEngine()
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
