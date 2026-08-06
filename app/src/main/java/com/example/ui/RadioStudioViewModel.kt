package com.example.ui

import android.app.Application
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.NativeAudioEngine
import com.example.ui.components.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "RadioStudioViewModel"

enum class StreamProtocolType(val label: String, val nativeValue: Int) {
    ICECAST("Icecast", NativeAudioEngine.PROTOCOL_ICECAST),
    SHOUTCAST("Shoutcast", NativeAudioEngine.PROTOCOL_SHOUTCAST)
}

data class StreamConfig(
    val serverUrl: String = "",
    val port: String = "8000",
    val mountPoint: String = "/stream.wav",
    val password: String = "",
    val stationName: String = "Mi Radio Online",
    val genre: String = "Variety / Live Talk / Electronic",
    val bitrateKbps: Int = 128,
    val protocol: StreamProtocolType = StreamProtocolType.ICECAST
)

data class SoundPad(
    val id: Int,
    val title: String,
    val iconName: String,
    val category: String,
    val isPlaying: Boolean = false
)

data class PlaylistItem(
    val id: String, // content:// URI string of the local audio file
    val title: String,
    val artist: String,
    val duration: String,
    val isPlaying: Boolean = false
)

data class StudioUiState(
    val isLive: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
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
    val musicFolderUri: String? = null,
    val musicFolderName: String? = null,
    val isScanningFolder: Boolean = false,
    val searchQuery: String = "",
    val playlist: List<PlaylistItem> = emptyList(),
    val isRecordingLocally: Boolean = false,
    val recordedDurationSeconds: Long = 0L,
    val activeTab: Int = 0
)

class RadioStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val audioEngine = NativeAudioEngine()
    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var connectionWatchJob: Job? = null
    private var musicDecodeJob: Job? = null

    init {
        // Load the user's previously saved server configuration (if any). The app
        // never ships with a fixed/hardcoded server — this restores whatever the
        // user typed and tapped "Guardar" on last time, or sane empty defaults.
        val savedConfig = StreamConfigStore.load(application)
        _uiState.update { it.copy(streamConfig = savedConfig) }

        // Initialize Native Audio Settings so the engine matches the UI defaults
        // from the very first frame it processes.
        val initial = _uiState.value
        audioEngine.nativeSetMasterVolume(initial.masterVolume)
        audioEngine.nativeSetMicGain(initial.micGain)
        audioEngine.nativeSetMusicVolume(initial.musicVolume)
        audioEngine.nativeSetDuckingEnabled(initial.isDuckingEnabled)
        audioEngine.nativeSetEqGains(initial.eqLowDb, initial.eqMidDb, initial.eqHighDb)
        audioEngine.nativeSetVoiceEffects(initial.voiceReverb, initial.voicePitch, initial.noiseGateThresholdDb)

        // Poll VU meter
        viewModelScope.launch {
            while (true) {
                delay(40) // ~25 FPS VU updates
                val state = _uiState.value
                val peak = if (state.isLive) audioEngine.nativeGetVuMeter() else 0.0f
                _uiState.update { it.copy(vuPeakLevel = peak) }
            }
        }

        // Uptime counter + connection watchdog: if the socket to the Icecast server
        // drops (lost internet, server restart, etc.) this is what flips the badge
        // back to OFFLINE instead of leaving a stale "ON AIR" shown.
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val state = _uiState.value
                if (state.isLive) {
                    val status = audioEngine.nativeGetStreamStatus()
                    if (status == NativeAudioEngine.STREAM_STREAMING) {
                        _uiState.update { it.copy(uptimeSeconds = it.uptimeSeconds + 1) }
                    } else {
                        Log.w(TAG, "Stream dropped (status=$status), going OFFLINE")
                        audioEngine.nativeDisconnectStream()
                        audioEngine.nativeStopEngine()
                        _uiState.update {
                            it.copy(
                                isLive = false,
                                uptimeSeconds = 0L,
                                connectionError = "Se perdió la conexión con el servidor"
                            )
                        }
                    }
                }
                if (state.isRecordingLocally) {
                    _uiState.update { it.copy(recordedDurationSeconds = it.recordedDurationSeconds + 1) }
                }
            }
        }
    }

    private fun hasInternetConnection(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun toggleLiveBroadcast() {
        val current = _uiState.value
        if (current.isLive || current.isConnecting) {
            connectionWatchJob?.cancel()
            audioEngine.nativeDisconnectStream()
            audioEngine.nativeStopEngine()
            _uiState.update { it.copy(isLive = false, isConnecting = false, uptimeSeconds = 0L) }
            return
        }

        if (!hasInternetConnection()) {
            _uiState.update { it.copy(connectionError = "Sin conexión a Internet. Revisa tu red (Wi-Fi/datos) e inténtalo de nuevo.") }
            return
        }

        _uiState.update { it.copy(isConnecting = true, connectionError = null) }

        val engineStarted = audioEngine.nativeStartEngine()
        if (!engineStarted) {
            _uiState.update {
                it.copy(isConnecting = false, connectionError = "No se pudo iniciar el motor de audio (revisa el permiso de micrófono)")
            }
            return
        }

        val cfg = current.streamConfig
        if (cfg.serverUrl.isBlank()) {
            audioEngine.nativeStopEngine()
            _uiState.update {
                it.copy(
                    isConnecting = false,
                    connectionError = "Configura primero tu servidor en la pestaña \"Servidor\" y pulsa Guardar."
                )
            }
            return
        }
        val portInt = cfg.port.toIntOrNull() ?: 8000
        audioEngine.nativeConnectStream(
            cfg.serverUrl,
            portInt,
            cfg.mountPoint,
            cfg.password,
            cfg.bitrateKbps,
            cfg.protocol.nativeValue,
            cfg.stationName
        )

        connectionWatchJob?.cancel()
        connectionWatchJob = viewModelScope.launch {
            var elapsedMs = 0
            val timeoutMs = 15000
            while (elapsedMs < timeoutMs && isActive) {
                delay(200)
                elapsedMs += 200
                when (audioEngine.nativeGetStreamStatus()) {
                    NativeAudioEngine.STREAM_STREAMING -> {
                        val apiName = try {
                            audioEngine.nativeGetAudioApiName()
                        } catch (e: Exception) {
                            "AAudio (Oboe Native)"
                        }
                        _uiState.update {
                            it.copy(
                                isLive = true,
                                isConnecting = false,
                                connectionError = null,
                                audioApiName = if (apiName.isBlank()) "AAudio (Oboe Native)" else apiName
                            )
                        }
                        return@launch
                    }
                    NativeAudioEngine.STREAM_ERROR -> {
                        audioEngine.nativeStopEngine()
                        _uiState.update {
                            it.copy(
                                isLive = false,
                                isConnecting = false,
                                connectionError = "No se pudo conectar al servidor. Revisa host, puerto y contraseña."
                            )
                        }
                        return@launch
                    }
                }
            }
            // Timed out still connecting
            audioEngine.nativeDisconnectStream()
            audioEngine.nativeStopEngine()
            _uiState.update {
                it.copy(isLive = false, isConnecting = false, connectionError = "Tiempo de espera agotado conectando al servidor")
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
        val enabled = !_uiState.value.isDuckingEnabled
        _uiState.update { it.copy(isDuckingEnabled = enabled) }
        audioEngine.nativeSetDuckingEnabled(enabled)
    }

    fun setEqGains(low: Float, mid: Float, high: Float) {
        _uiState.update { it.copy(eqLowDb = low, eqMidDb = mid, eqHighDb = high) }
        audioEngine.nativeSetEqGains(low, mid, high)
    }

    fun setVoiceEffects(reverb: Float, pitch: Float, gate: Float) {
        _uiState.update { it.copy(voiceReverb = reverb, voicePitch = pitch, noiseGateThresholdDb = gate) }
        audioEngine.nativeSetVoiceEffects(reverb, pitch, gate)
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
            delay(900)
            _uiState.update { state ->
                val updatedPads = state.soundPads.map { pad ->
                    if (pad.id == padId) pad.copy(isPlaying = false) else pad
                }
                state.copy(soundPads = updatedPads)
            }
        }
    }

    /** Updates the in-memory config only (used for live UI previews, e.g. picking a bitrate). */
    fun updateStreamConfig(config: StreamConfig) {
        _uiState.update { it.copy(streamConfig = config) }
    }

    /** Updates the config AND persists it to disk so it survives an app restart. */
    fun saveStreamConfig(config: StreamConfig) {
        _uiState.update { it.copy(streamConfig = config) }
        StreamConfigStore.save(getApplication(), config)
    }

    fun toggleLocalRecording() {
        val nextRec = !_uiState.value.isRecordingLocally
        _uiState.update { it.copy(isRecordingLocally = nextRec, recordedDurationSeconds = 0L) }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(activeTab = tabIndex) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // --- Local music folder (Storage Access Framework) ---

    fun setMusicFolder(treeUri: Uri) {
        val context = getApplication<Application>()
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not persist folder permission: ${e.message}")
        }

        _uiState.update {
            it.copy(
                isScanningFolder = true,
                musicFolderUri = treeUri.toString(),
                musicFolderName = treeUri.lastPathSegment ?: "Carpeta local"
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val tracks = scanFolderForAudio(treeUri)
            _uiState.update { it.copy(playlist = tracks, isScanningFolder = false) }
        }
    }

    private fun scanFolderForAudio(treeUri: Uri): List<PlaylistItem> {
        val context = getApplication<Application>()
        val tracks = mutableListOf<PlaylistItem>()
        val audioExtensions = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "wma", "opus")

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )

        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx) ?: ""
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (!mime.startsWith("audio/") && ext !in audioExtensions) continue

                    val docId = cursor.getString(idIdx)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val durationStr = readDurationSafely(docUri)

                    tracks.add(
                        PlaylistItem(
                            id = docUri.toString(),
                            title = name.substringBeforeLast('.'),
                            artist = "Música local",
                            duration = durationStr
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning music folder: ${e.message}")
        }

        return tracks.sortedBy { it.title.lowercase() }
    }

    private fun readDurationSafely(uri: Uri): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(getApplication(), uri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            formatDuration(ms / 1000)
        } catch (e: Exception) {
            "--:--"
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    // --- Music deck playback: decode the local file and feed PCM into the native mixer ---

    fun playPlaylistItem(itemId: String) {
        val track = _uiState.value.playlist.find { it.id == itemId } ?: return

        _uiState.update { state ->
            state.copy(playlist = state.playlist.map { it.copy(isPlaying = it.id == itemId) })
        }

        musicDecodeJob?.cancel()
        audioEngine.nativeClearMusicBuffer()
        audioEngine.nativeSetMusicPlaying(true)

        musicDecodeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                decodeAndFeedTrack(Uri.parse(track.id))
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding track ${track.title}: ${e.message}")
            }
            if (isActive) {
                audioEngine.nativeSetMusicPlaying(false)
                _uiState.update { state ->
                    state.copy(playlist = state.playlist.map {
                        if (it.id == itemId) it.copy(isPlaying = false) else it
                    })
                }
            }
        }
    }

    fun stopPlayback() {
        musicDecodeJob?.cancel()
        audioEngine.nativeSetMusicPlaying(false)
        audioEngine.nativeClearMusicBuffer()
        _uiState.update { state ->
            state.copy(playlist = state.playlist.map { it.copy(isPlaying = false) })
        }
    }

    private suspend fun decodeAndFeedTrack(uri: Uri) {
        val context = getApplication<Application>()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open track: ${e.message}")
            extractor.release()
            return
        }

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            return
        }
        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone && kotlinx.coroutines.currentCoroutineContext().isActive) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)
                        val sampleSize = inBuf?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        outBuf.get(chunk)

                        val shorts = ShortArray(chunk.size / 2)
                        ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

                        if (shorts.isNotEmpty() && channelCount > 0) {
                            audioEngine.nativeFeedMusicPcm(shorts, shorts.size / channelCount, channelCount, sampleRate)
                            // Throttle the decode loop to roughly real-time so the native
                            // ring buffer doesn't fill up far ahead of what's being played.
                            val frameCount = shorts.size / channelCount
                            kotlinx.coroutines.delay((frameCount * 1000L / sampleRate).coerceAtLeast(1))
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
            extractor.release()
        }
    }
}
