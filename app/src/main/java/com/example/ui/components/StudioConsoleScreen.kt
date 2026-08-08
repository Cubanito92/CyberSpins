package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.*
import com.example.ui.theme.*
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioConsoleScreen(
    viewModel: RadioStudioViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            OnAirHeaderBar(state = state, onToggleLive = { viewModel.toggleLiveBroadcast() })
        },
        bottomBar = {
            NavigationBar(
                containerColor = StudioCardSurface,
                contentColor = StudioTextPrimary
            ) {
                NavigationBarItem(
                    selected = state.activeTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Equalizer, contentDescription = "Console") },
                    label = { Text("Consola") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        indicatorColor = StudioMutedAccent
                    )
                )
                NavigationBarItem(
                    selected = state.activeTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = "EQ & FX") },
                    label = { Text("EQ & Efectos") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        indicatorColor = StudioMutedAccent
                    )
                )
                NavigationBarItem(
                    selected = state.activeTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.CellTower, contentDescription = "Emisión") },
                    label = { Text("Servidor") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        indicatorColor = StudioMutedAccent
                    )
                )
                NavigationBarItem(
                    selected = state.activeTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Playlist") },
                    label = { Text("Música") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        indicatorColor = StudioMutedAccent
                    )
                )
            }
        },
        containerColor = StudioDarkBackground,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.activeTab) {
                0 -> MainConsoleTab(state = state, viewModel = viewModel)
                1 -> EqualizerAndFxTab(state = state, viewModel = viewModel)
                2 -> ServerStreamingTab(state = state, viewModel = viewModel)
                3 -> PlaylistTab(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun OnAirHeaderBar(
    state: StudioUiState,
    onToggleLive: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LivePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    Surface(
        color = StudioCardSurface,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (state.isLive) OnAirRed else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RADIO STUDIO PRO",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = StudioTextPrimary,
                        letterSpacing = 1.sp
                    )
                }

                Button(
                    onClick = onToggleLive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isLive) OnAirRed else NeonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("toggle_live_button")
                ) {
                    if (state.isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isLive) Icons.Default.Stop else Icons.Default.RadioButtonChecked,
                            contentDescription = "Transmitir",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isLive) "TRANSMITIENDO" else "INICIAR TRANSMISIÓN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Badge & Uptime
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (state.isLive) OnAirRed.copy(alpha = if (state.isLive) pulseAlpha else 1.0f) else StudioMutedAccent,
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = if (state.isLive) OnAirRed else StudioCardBorder,
                        shape = RoundedCornerShape(6.dp)
                    )
                ) {
                    Text(
                        text = if (state.isLive) "● ON AIR LIVE" else "OFFLINE",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = "Uptime",
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatDuration(state.uptimeSeconds),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NeonCyan
                    )
                }
            }

            if (state.connectionError != null) {
                Text(
                    text = state.connectionError,
                    fontSize = 11.sp,
                    color = OnAirRed,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Peak Master VU Meter
            MasterVuMeter(peak = state.vuPeakLevel)
        }
    }
}

@Composable
fun MasterVuMeter(peak: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "MASTER VU METRIC", fontSize = 10.sp, color = StudioTextSecondary, fontWeight = FontWeight.Bold)
            Text(
                text = "${(peak * 100).toInt()}% PEAK",
                fontSize = 10.sp,
                color = if (peak > 0.85f) VuRed else VuGreen,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF07090D))
                .border(1.dp, StudioCardBorder, RoundedCornerShape(4.dp))
        ) {
            val totalSegments = 30
            val segmentWidth = (size.width - (totalSegments - 1) * 2f) / totalSegments
            val activeSegments = (peak * totalSegments).toInt()

            for (i in 0 until totalSegments) {
                val left = i * (segmentWidth + 2f)
                val isActive = i < activeSegments

                val color = when {
                    i >= 25 -> if (isActive) VuRed else VuRed.copy(alpha = 0.15f)
                    i >= 20 -> if (isActive) VuOrange else VuOrange.copy(alpha = 0.15f)
                    i >= 15 -> if (isActive) VuYellow else VuYellow.copy(alpha = 0.15f)
                    else -> if (isActive) VuGreen else VuGreen.copy(alpha = 0.15f)
                }

                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, 2f),
                    size = Size(segmentWidth, size.height - 4f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
    }
}

@Composable
fun MainConsoleTab(
    state: StudioUiState,
    viewModel: RadioStudioViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MasterVuMeter(peak = state.vuPeakLevel)
        MixerRackCard(state, viewModel)
        QuickEqStripCard(state, viewModel)
        SoundboardGridCard(state = state, viewModel = viewModel)
        NativeEngineInfoCard(state)
    }
}

@Composable
private fun MixerRackCard(state: StudioUiState, viewModel: RadioStudioViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(StudioCardSurface, StudioDarkBackground)))
            .border(1.dp, StudioCardBorder, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MEZCLADOR", color = StudioTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            DuckingPill(active = state.isDuckingEnabled, onClick = { viewModel.toggleDucking() })
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModernKnob(
                label = "MIC",
                value = state.micGain / 2f,
                onValueChange = { viewModel.setMicGain((it * 2f).coerceIn(0f, 2f)) },
                accentColor = if (state.isMicMuted) StudioTextSecondary else NeonCyan,
                valueText = if (state.isMicMuted) "MUTE" else "${(state.micGain * 100).toInt()}%"
            )
            ModernKnob(
                label = "MÚSICA",
                value = state.musicVolume,
                onValueChange = { viewModel.setMusicVolume(it) },
                accentColor = NeonPurple,
                valueText = "${(state.musicVolume * 100).toInt()}%"
            )
            ModernKnob(
                label = "MASTER",
                value = state.masterVolume,
                onValueChange = { viewModel.setMasterVolume(it) },
                accentColor = OnAirRed,
                valueText = "${(state.masterVolume * 100).toInt()}%"
            )
        }

        Spacer(Modifier.height(18.dp))

        MicMuteButton(isMuted = state.isMicMuted, onClick = { viewModel.toggleMicMute() })
    }
}

@Composable
private fun DuckingPill(active: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) NeonCyan.copy(alpha = 0.15f) else StudioMutedAccent)
            .border(1.dp, if (active) NeonCyan else StudioCardBorder, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Default.VolumeDown,
            contentDescription = null,
            tint = if (active) NeonCyan else StudioTextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "AUTO-DUCKING",
            color = if (active) NeonCyan else StudioTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MicMuteButton(isMuted: Boolean, onClick: () -> Unit) {
    val bg = if (isMuted) {
        Brush.horizontalGradient(listOf(Color(0xFF3A1220), Color(0xFF2A0E18)))
    } else {
        Brush.horizontalGradient(listOf(NeonCyan, NeonPurple))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = null,
            tint = if (isMuted) OnAirRed else StudioDarkBackground,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (isMuted) "MIC SILENCIADO" else "MIC ABIERTO — PULSA PARA SILENCIAR",
            color = if (isMuted) OnAirRed else StudioDarkBackground,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp
        )
    }
}

@Composable
fun NativeEngineInfoCard(state: StudioUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioCardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "C++ Engine",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MOTOR C++ OBOE NDK",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = StudioTextPrimary
                    )
                }
                Surface(
                    color = NeonCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "BAJA LATENCIA",
                        color = NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoTile(label = "Audio API", value = state.audioApiName)
                InfoTile(label = "Latencia", value = "${state.bufferLatencyMs} ms")
                InfoTile(label = "Muestreo", value = "${state.sampleRate / 1000} kHz")
                InfoTile(label = "Canales", value = "${state.activeChannelCount} Estéreo")
            }
        }
    }
}

@Composable
fun InfoTile(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = StudioTextSecondary)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = StudioTextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun QuickEqStripCard(state: StudioUiState, viewModel: RadioStudioViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(StudioCardSurface)
            .border(1.dp, StudioCardBorder, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text("ECUALIZADOR RÁPIDO", color = StudioTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EqPillKnob("GRAVES", state.eqLowDb, NeonPurple) {
                viewModel.setEqGains(it, state.eqMidDb, state.eqHighDb)
            }
            EqPillKnob("MEDIOS", state.eqMidDb, NeonCyan) {
                viewModel.setEqGains(state.eqLowDb, it, state.eqHighDb)
            }
            EqPillKnob("AGUDOS", state.eqHighDb, OnAirRed) {
                viewModel.setEqGains(state.eqLowDb, state.eqMidDb, it)
            }
        }
    }
}

@Composable
private fun EqPillKnob(
    label: String,
    valueDb: Float,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    val normalized = ((valueDb + 12f) / 24f).coerceIn(0f, 1f)
    ModernKnob(
        label = label,
        value = normalized,
        onValueChange = { onValueChange(it * 24f - 12f) },
        accentColor = accent,
        size = 72.dp,
        valueText = "${if (valueDb >= 0) "+" else ""}${valueDb.toInt()}dB"
    )
}

@Composable
fun SoundboardGridCard(
    state: StudioUiState,
    viewModel: RadioStudioViewModel
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioCardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SOUNDBOARD - EFECTOS INSTANTÁNEOS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = StudioTextPrimary
                )
                Text(
                    text = "8 PADS",
                    fontSize = 10.sp,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(180.dp)
            ) {
                items(state.soundPads) { pad ->
                    SoundPadTile(pad = pad, onClick = { viewModel.triggerSoundPad(pad.id) })
                }
            }
        }
    }
}

@Composable
fun SoundPadTile(pad: SoundPad, onClick: () -> Unit) {
    val padColor by animateColorAsState(
        targetValue = if (pad.isPlaying) OnAirRed else StudioMutedAccent,
        label = "PadColor"
    )

    Surface(
        color = padColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .aspectRatio(1.0f)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (pad.isPlaying) OnAirRed else StudioCardBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(
                imageVector = when (pad.iconName) {
                    "campaign" -> Icons.Default.Campaign
                    "thumb_up" -> Icons.Default.ThumbUp
                    "radio" -> Icons.Default.Radio
                    "groups" -> Icons.Default.Groups
                    "album" -> Icons.Default.Album
                    "music_note" -> Icons.Default.MusicNote
                    "graphic_eq" -> Icons.Default.GraphicEq
                    else -> Icons.Default.RecordVoiceOver
                },
                contentDescription = pad.title,
                tint = if (pad.isPlaying) Color.White else NeonCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pad.title,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (pad.isPlaying) Color.White else StudioTextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun EqualizerAndFxTab(
    state: StudioUiState,
    viewModel: RadioStudioViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Live Waveform Visualizer Canvas
            AudioWaveformVisualizer(isLive = state.isLive)
        }

        item {
            // 3-Band Parametric EQ Card
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ECUALIZADOR PARAMÉTRICO 3 BANDAS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = StudioTextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    EqBandSlider(
                        label = "Graves (Low - 100Hz)",
                        value = state.eqLowDb,
                        onValueChange = { viewModel.setEqGains(it, state.eqMidDb, state.eqHighDb) }
                    )

                    EqBandSlider(
                        label = "Medios (Mid - 1kHz)",
                        value = state.eqMidDb,
                        onValueChange = { viewModel.setEqGains(state.eqLowDb, it, state.eqHighDb) }
                    )

                    EqBandSlider(
                        label = "Agudos (High - 10kHz)",
                        value = state.eqHighDb,
                        onValueChange = { viewModel.setEqGains(state.eqLowDb, state.eqMidDb, it) }
                    )
                }
            }
        }

        item {
            // Voice Processing FX
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PROCESADOR Y EFECTOS DE VOZ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = StudioTextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    EqBandSlider(
                        label = "Reverberación de Estudio",
                        value = state.voiceReverb * 12.0f - 6.0f,
                        onValueChange = {
                            val normReverb = (it + 6.0f) / 12.0f
                            viewModel.setVoiceEffects(normReverb, state.voicePitch, state.noiseGateThresholdDb)
                        }
                    )

                    EqBandSlider(
                        label = "Tone Pitch Shift (Semitonos)",
                        value = state.voicePitch,
                        onValueChange = {
                            viewModel.setVoiceEffects(state.voiceReverb, it, state.noiseGateThresholdDb)
                        }
                    )

                    EqBandSlider(
                        label = "Noise Gate Threshold (dB)",
                        value = state.noiseGateThresholdDb,
                        valueRange = -60.0f..0.0f,
                        onValueChange = {
                            viewModel.setVoiceEffects(state.voiceReverb, state.voicePitch, it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EqBandSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = -12.0f..12.0f,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 12.sp, color = StudioTextPrimary)
            Text(
                text = "${if (value >= 0) "+" else ""}${value.toInt()} dB",
                fontSize = 12.sp,
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = NeonCyan,
                activeTrackColor = NeonCyan,
                inactiveTrackColor = StudioCardBorder
            )
        )
    }
}

@Composable
fun AudioWaveformVisualizer(isLive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveAnim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = StudioCardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val centerY = height / 2f

                if (isLive) {
                    val points = 60
                    val step = width / points

                    for (i in 0 until points - 1) {
                        val x1 = i * step
                        val x2 = (i + 1) * step

                        val amp1 = sin(i * 0.2f + phase) * 35f * (if (i % 2 == 0) 1.2f else 0.8f)
                        val amp2 = sin((i + 1) * 0.2f + phase) * 35f * (if (i % 2 == 0) 0.8f else 1.2f)

                        drawLine(
                            brush = Brush.horizontalGradient(listOf(NeonCyan, NeonPurple, OnAirRed)),
                            start = Offset(x1, centerY + amp1),
                            end = Offset(x2, centerY + amp2),
                            strokeWidth = 3f
                        )
                    }
                } else {
                    drawLine(
                        color = StudioCardBorder,
                        start = Offset(0f, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = 2f
                    )
                }
            }

            Text(
                text = if (isLive) "● SEÑAL DE AUDIO EN VIVO (48kHz 24-bit)" else "SEÑAL EN ESPERA",
                fontSize = 11.sp,
                color = if (isLive) NeonCyan else StudioTextSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
fun ServerStreamingTab(
    state: StudioUiState,
    viewModel: RadioStudioViewModel
) {
    var serverUrl by remember { mutableStateOf(state.streamConfig.serverUrl) }
    var port by remember { mutableStateOf(state.streamConfig.port) }
    var mountPoint by remember { mutableStateOf(state.streamConfig.mountPoint) }
    var password by remember { mutableStateOf(state.streamConfig.password) }
    var stationName by remember { mutableStateOf(state.streamConfig.stationName) }
    var protocol by remember { mutableStateOf(state.streamConfig.protocol) }
    var selectedBitrate by remember { mutableStateOf(state.streamConfig.bitrateKbps) }
    var justSaved by remember { mutableStateOf(false) }

    fun currentConfig() = StreamConfig(
        serverUrl = serverUrl,
        port = port,
        mountPoint = mountPoint,
        password = password,
        stationName = stationName,
        genre = state.streamConfig.genre,
        bitrateKbps = selectedBitrate,
        protocol = protocol
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CONFIGURACIÓN DEL SERVIDOR DE TRANSMISIÓN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = StudioTextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Protocolo del Servidor", fontSize = 12.sp, color = StudioTextSecondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StreamProtocolType.entries.forEach { option ->
                            val isSel = protocol == option
                            Surface(
                                onClick = { protocol = option; justSaved = false },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) NeonCyan else StudioMutedAccent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = option.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.Black else Color.White,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = stationName,
                        onValueChange = { stationName = it; justSaved = false },
                        label = { Text("Nombre de la Estación") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = StudioCardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it; justSaved = false },
                            label = { Text("Host / IP Servidor") },
                            placeholder = { Text("tu.servidor.com") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = StudioCardBorder
                            ),
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it; justSaved = false },
                            label = { Text("Puerto") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = StudioCardBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = mountPoint,
                            onValueChange = { mountPoint = it; justSaved = false },
                            label = { Text("Punto de Montaje (Mount)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = StudioCardBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; justSaved = false },
                            label = { Text("Contraseña Fuente") },
                            placeholder = { Text("••••••") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = StudioCardBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Tasa de Bits (kbps)", fontSize = 12.sp, color = StudioTextSecondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(64, 96, 128, 192, 320).forEach { bitrate ->
                            val isSel = selectedBitrate == bitrate
                            Surface(
                                onClick = { selectedBitrate = bitrate; justSaved = false },
                                shape = RoundedCornerShape(5.dp),
                                color = if (isSel) NeonCyan else StudioMutedAccent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${bitrate}k",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.Black else Color.White,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.saveStreamConfig(currentConfig())
                            justSaved = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Guardar")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "GUARDAR CONFIGURACIÓN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (justSaved) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Servidor guardado. Se usará esta configuración al transmitir.",
                            fontSize = 11.sp,
                            color = NeonCyan
                        )
                    }
                }
            }
        }

        item {
            // Local MP3 Session Recorder
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Grabador de Sesión Local MP3", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = if (state.isRecordingLocally) "Grabando: ${formatDuration(state.recordedDurationSeconds)}" else "Guardar copia local en SD",
                            fontSize = 11.sp,
                            color = if (state.isRecordingLocally) OnAirRed else StudioTextSecondary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleLocalRecording() },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (state.isRecordingLocally) OnAirRed else StudioMutedAccent)
                    ) {
                        Icon(
                            imageVector = if (state.isRecordingLocally) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = "Record",
                            tint = if (state.isRecordingLocally) Color.White else OnAirRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistTab(
    state: StudioUiState,
    viewModel: RadioStudioViewModel
) {
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.setMusicFolder(uri)
        }
    }

    val filteredPlaylist = remember(state.playlist, state.searchQuery) {
        if (state.searchQuery.isBlank()) {
            state.playlist
        } else {
            state.playlist.filter { it.title.contains(state.searchQuery, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LISTA DE REPRODUCCIÓN Y DECK",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = StudioTextPrimary
                )
                Text(
                    text = "${state.playlist.size} Pistas",
                    fontSize = 11.sp,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Button(
                onClick = { folderPickerLauncher.launch(null) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioMutedAccent,
                    contentColor = NeonCyan
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Añadir ruta")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.musicFolderName?.let { "Carpeta: $it" } ?: "AÑADIR RUTA DE MÚSICA LOCAL",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Buscar canción por nombre...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = StudioCardBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.isScanningFolder) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonCyan, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Explorando carpeta...", fontSize = 12.sp, color = StudioTextSecondary)
                }
            }
        } else if (state.playlist.isEmpty()) {
            item {
                Text(
                    text = "No hay música cargada. Toca \"Añadir ruta\" y elige la carpeta de música de tu teléfono.",
                    fontSize = 12.sp,
                    color = StudioTextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else if (filteredPlaylist.isEmpty()) {
            item {
                Text(
                    text = "Sin resultados para \"${state.searchQuery}\"",
                    fontSize = 12.sp,
                    color = StudioTextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        items(filteredPlaylist) { item ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (item.isPlaying) StudioMutedAccent else StudioCardSurface
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (item.isPlaying) NeonCyan else StudioCardBorder,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        if (item.isPlaying) viewModel.stopPlayback() else viewModel.playPlaylistItem(item.id)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (item.isPlaying) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                            contentDescription = "Track",
                            tint = if (item.isPlaying) NeonCyan else StudioTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = if (item.isPlaying) NeonCyan else StudioTextPrimary
                            )
                            Text(
                                text = item.artist,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = StudioTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = item.duration,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = StudioTextSecondary
                    )
                }
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
