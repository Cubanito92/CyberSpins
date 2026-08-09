package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
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
    modifier: Modifier = Modifier,
    onPickSongForDeckA: () -> Unit = {},
    onPickSongForDeckB: () -> Unit = {}
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
                0 -> MainConsoleTab(
                    state = state,
                    viewModel = viewModel,
                    onPickSongForDeckA = onPickSongForDeckA,
                    onPickSongForDeckB = onPickSongForDeckB
                )
                1 -> EqualizerAndFxTab(state = state, viewModel = viewModel)
                2 -> ServerStreamingTab(state = state, viewModel = viewModel)
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
    viewModel: RadioStudioViewModel,
    onPickSongForDeckA: () -> Unit = {},
    onPickSongForDeckB: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Two-turntable DJ console: load a song per deck, spin while
        // playing, mix them with the crossfader.
        item {
            DjDecksCard(
                state = state,
                viewModel = viewModel,
                onPickSongForDeckA = onPickSongForDeckA,
                onPickSongForDeckB = onPickSongForDeckB
            )
        }

        // Mic + Master channel strip
        item {
            FadersConsoleCard(state = state, viewModel = viewModel)
        }

        // Low Latency Native Engine Card
        item {
            NativeEngineInfoCard(state = state)
        }

        // Soundboard Instant Effects Grid
        item {
            SoundboardGridCard(state = state, viewModel = viewModel)
        }
    }
}

@Composable
fun DjDecksCard(
    state: StudioUiState,
    viewModel: RadioStudioViewModel,
    onPickSongForDeckA: () -> Unit,
    onPickSongForDeckB: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioCardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "CONSOLA DJ - 2 PLATOS",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = StudioTextPrimary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TurntableDeck(
                    modifier = Modifier.weight(1f),
                    label = "PLATO A",
                    accentColor = NeonCyan,
                    deck = state.deckA,
                    onLoad = onPickSongForDeckA,
                    onPlayPause = { viewModel.toggleDeckPlayPause(0) },
                    onVolumeChange = { viewModel.setDeckVolume(0, it) }
                )
                TurntableDeck(
                    modifier = Modifier.weight(1f),
                    label = "PLATO B",
                    accentColor = NeonPurple,
                    deck = state.deckB,
                    onLoad = onPickSongForDeckB,
                    onPlayPause = { viewModel.toggleDeckPlayPause(1) },
                    onVolumeChange = { viewModel.setDeckVolume(1, it) }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Crossfader
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "A", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    Text(
                        text = "CROSSFADER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Text(text = "B", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonPurple)
                }
                Slider(
                    value = state.crossfaderPosition,
                    onValueChange = { viewModel.setCrossfader(it) },
                    valueRange = -1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = StudioTextPrimary,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = NeonPurple.copy(alpha = 0.5f)
                    )
                )
                Text(
                    text = "Al terminar una canción, el volumen baja solo y sube el del otro plato.",
                    fontSize = 10.sp,
                    color = StudioTextSecondary
                )
            }
        }
    }
}

@Composable
fun TurntableDeck(
    modifier: Modifier = Modifier,
    label: String,
    accentColor: Color,
    deck: DeckUiState,
    onLoad: () -> Unit,
    onPlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    // The disc keeps spinning smoothly while playing and freezes exactly
    // where it was when paused - like a real turntable needle drop.
    var rotationDegrees by remember { mutableStateOf(0f) }
    LaunchedEffect(deck.isPlaying) {
        if (deck.isPlaying) {
            var lastNanos = withFrameNanos { it }
            while (true) {
                val nowNanos = withFrameNanos { it }
                val deltaSeconds = (nowNanos - lastNanos) / 1_000_000_000f
                lastNanos = nowNanos
                rotationDegrees = (rotationDegrees + deltaSeconds * 90f) % 360f // ~33 RPM feel
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .size(110.dp)
                .clickable(enabled = deck.isLoaded, onClick = onPlayPause)
        ) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Vinyl body
            drawCircle(color = Color(0xFF11151C), radius = radius, center = center)
            drawCircle(
                color = StudioCardBorder,
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
            )
            // Grooves
            for (i in 1..4) {
                drawCircle(
                    color = Color(0xFF20262F),
                    radius = radius * (i / 5f),
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
                )
            }
            // Rotating label + notch so spinning is visible
            rotate(degrees = rotationDegrees, pivot = center) {
                drawCircle(color = accentColor.copy(alpha = 0.85f), radius = radius * 0.32f, center = center)
                drawCircle(color = Color.Black, radius = radius * 0.06f, center = center)
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = radius * 0.035f,
                    center = Offset(center.x, center.y - radius * 0.32f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (deck.title.isNotEmpty()) deck.title else "Sin canción",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (deck.title.isNotEmpty()) StudioTextPrimary else StudioTextSecondary,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (deck.durationMs > 0) {
            Text(
                text = "${formatMs(deck.positionMs)} / ${formatMs(deck.durationMs)}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = StudioTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onLoad,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Cargar", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Cargar", fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onPlayPause,
                enabled = deck.isLoaded,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (deck.isLoaded) accentColor else StudioMutedAccent)
            ) {
                Icon(
                    imageVector = if (deck.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(text = "Vol. ${(deck.volume * 100).toInt()}%", fontSize = 10.sp, color = StudioTextSecondary)
        Slider(
            value = deck.volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = StudioCardBorder
            )
        )
    }
}

fun formatMs(ms: Int): String {
    val totalSeconds = ms / 1000
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format("%02d:%02d", mins, secs)
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
fun FadersConsoleCard(
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
            Text(
                text = "MEZCLADOR DE CANALES",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = StudioTextPrimary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Mic Channel
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleMicMute() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (state.isMicMuted) OnAirRed else StudioMutedAccent)
                        ) {
                            Icon(
                                imageVector = if (state.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mic Mute",
                                tint = if (state.isMicMuted) Color.White else NeonCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Micrófono Principal", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = if (state.isMicMuted) "SILENCIADO" else "Ganan.: ${(state.micGain * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = if (state.isMicMuted) OnAirRed else StudioTextSecondary
                            )
                        }
                    }

                    // Ducking Switch
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Ducking Auto", fontSize = 11.sp, color = StudioTextSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = state.isDuckingEnabled,
                            onCheckedChange = { viewModel.toggleDucking() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = NeonCyan
                            )
                        )
                    }
                }

                Slider(
                    value = state.micGain,
                    onValueChange = { viewModel.setMicGain(it) },
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = StudioCardBorder
                    )
                )
            }

            HorizontalDivider(color = StudioCardBorder, modifier = Modifier.padding(vertical = 12.dp))

            // Master Volume Channel
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Master",
                            tint = OnAirRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Master Out Principal", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = "Nivel: ${(state.masterVolume * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = StudioTextSecondary
                            )
                        }
                    }
                }

                Slider(
                    value = state.masterVolume,
                    onValueChange = { viewModel.setMasterVolume(it) },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = OnAirRed,
                        activeTrackColor = OnAirRed,
                        inactiveTrackColor = StudioCardBorder
                    )
                )
            }
        }
    }
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

                    OutlinedTextField(
                        value = stationName,
                        onValueChange = { stationName = it },
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
                            onValueChange = { serverUrl = it },
                            label = { Text("Host / IP Servidor") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = StudioCardBorder
                            ),
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
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
                            onValueChange = { mountPoint = it },
                            label = { Text("Punto de Montaje (Mount)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = StudioCardBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Contraseña Fuente") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = StudioCardBorder
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Tasa de Bits (Encoder Bitrate)", fontSize = 12.sp, color = StudioTextSecondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(64, 128, 192, 320).forEach { bitrate ->
                            val isSel = state.streamConfig.bitrateKbps == bitrate
                            Button(
                                onClick = {
                                    viewModel.updateStreamConfig(
                                        state.streamConfig.copy(
                                            serverUrl = serverUrl,
                                            port = port,
                                            mountPoint = mountPoint,
                                            password = password,
                                            stationName = stationName,
                                            bitrateKbps = bitrate
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) NeonCyan else StudioMutedAccent,
                                    contentColor = if (isSel) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "$bitrate k", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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

        items(state.playlist) { item ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (item.isPlaying) StudioMutedAccent else StudioCardSurface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (item.isPlaying) NeonCyan else StudioCardBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { viewModel.playPlaylistItem(item.id) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (item.isPlaying) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                            contentDescription = "Track",
                            tint = if (item.isPlaying) NeonCyan else StudioTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (item.isPlaying) NeonCyan else StudioTextPrimary
                            )
                            Text(
                                text = item.artist,
                                fontSize = 11.sp,
                                color = StudioTextSecondary
                            )
                        }
                    }

                    Text(
                        text = item.duration,
                        fontSize = 11.sp,
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
