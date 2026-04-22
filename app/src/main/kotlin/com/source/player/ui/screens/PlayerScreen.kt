package com.source.player.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.components.SectionKicker
import com.source.player.ui.navigation.Routes
import com.source.player.ui.theme.oklchToColor
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.PlayerViewModel

@Composable
fun PlayerScreen(
        navController: NavController,
        vm: PlayerViewModel = hiltViewModel(),
) {
    val song by vm.currentSong.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val positionMs by vm.positionMs.collectAsState()
    val durationMs by vm.durationMs.collectAsState()
    val repeatMode by vm.repeatMode.collectAsState()
    val shuffleEnabled by vm.shuffleEnabled.collectAsState()
    val queueIndex by vm.queueIndex.collectAsState()
    val queueSize by vm.queueItems.collectAsState()
    val songId by vm.currentSongId.collectAsState()
    val playbackError by vm.playbackError.collectAsState()
    val sonosActive by vm.sonosActive.collectAsState()
    val sonosVolume by vm.sonosVolume.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(playbackError) {
        playbackError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearError()
        }
    }

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        // Ambient gradient — two soft radial glows behind everything.
        AmbientGlow(accent = colors.accent)

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .systemBarsPadding()
                                .pointerInput(queueSize.size, shuffleEnabled) {
                                    // Horizontal fling anywhere on the hero area → next/prev.
                                    detectHorizontalDragGestures(onDragEnd = {}) { change, drag ->
                                        change.consume()
                                        if (kotlin.math.abs(drag) > 28f) {
                                            if (drag < 0) vm.skipToNext() else vm.skipToPrevious()
                                        }
                                    }
                                },
        ) {
            // ── Top bar ──────────────────────────────────────────────
            Row(
                    modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Close",
                            tint = colors.text,
                    )
                }
                MonoText(
                        text = sideLabel(queueIndex),
                        color = colors.textDim,
                        size = MonoSize.S95,
                )
                IconButton(onClick = { navController.navigate(Routes.QUEUE) }) {
                    Icon(
                            Icons.Rounded.QueueMusic,
                            contentDescription = "Queue",
                            tint = colors.text,
                    )
                }
            }

            // ── Hero area ────────────────────────────────────────────
            Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SectionKicker(
                        label = "Now Playing",
                        color = colors.accent,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(20.dp))

                // The title — 140px italic serif with trailing period. Wraps
                // naturally; if the total line count would push the scrubber
                // off-screen, fall back to 112px.
                val rawTitle = song?.mediaMetadata?.title?.toString() ?: "—"
                val titleText = if (rawTitle.endsWith(".")) rawTitle else "$rawTitle."
                VinylHeroTitle(titleText)

                Spacer(Modifier.height(22.dp))

                // Art dot + artist
                Row(
                        modifier =
                                Modifier.clickable(enabled = songId != null) {
                                    songId?.let { navController.navigate(Routes.tagEditor(it)) }
                                },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ArtDot(artUri = song?.mediaMetadata?.artworkUri?.toString())
                    Text(
                            text = song?.mediaMetadata?.artist?.toString() ?: "—",
                            style = text.libraryRow19,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                    )
                }

                val album = song?.mediaMetadata?.albumTitle?.toString()
                if (!album.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                            text = album,
                            style = text.metaSmall115,
                            color = colors.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                    )
                }
            }

            // ── Scrubber ─────────────────────────────────────────────
            VinylScrubber(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    accent = colors.accent,
                    hair = colors.hair,
                    textDim = colors.textDim,
                    onSeek = { vm.seekTo(it) },
                    modifier = Modifier.padding(horizontal = 28.dp),
            )

            Spacer(Modifier.height(14.dp))

            // ── Controls ─────────────────────────────────────────────
            Row(
                    modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { vm.setShuffleEnabled(!shuffleEnabled) }) {
                    Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (shuffleEnabled) colors.accent else colors.textDim,
                            modifier = Modifier.size(22.dp),
                    )
                }

                Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                            onClick = { vm.skipToPrevious() },
                            modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                                Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous",
                                tint = colors.text,
                                modifier = Modifier.size(28.dp),
                        )
                    }
                    FilledIconButton(
                            onClick = { if (isPlaying) vm.pause() else vm.play() },
                            modifier = Modifier.size(76.dp),
                            shape = CircleShape,
                            colors =
                                    IconButtonDefaults.filledIconButtonColors(
                                            containerColor = colors.accent
                                    ),
                    ) {
                        Icon(
                                if (isPlaying) Icons.Rounded.Pause
                                else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = colors.bg,
                                modifier = Modifier.size(34.dp),
                        )
                    }
                    IconButton(
                            onClick = { vm.skipToNext() },
                            modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                                Icons.Rounded.SkipNext,
                                contentDescription = "Next",
                                tint = colors.text,
                                modifier = Modifier.size(28.dp),
                        )
                    }
                }

                IconButton(onClick = { vm.cycleRepeatMode() }) {
                    Icon(
                            imageVector =
                                    when (repeatMode) {
                                        1 -> Icons.Rounded.Repeat
                                        2 -> Icons.Rounded.RepeatOne
                                        else -> Icons.Rounded.Repeat
                                    },
                            contentDescription = "Repeat",
                            tint = if (repeatMode != 0) colors.accent else colors.textDim,
                            modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (sonosActive) {
                Spacer(Modifier.height(8.dp))
                SonosVolumeSlider(
                        volume = sonosVolume,
                        onVolumeChange = { vm.setSonosVolume(it) },
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── Ambient background ────────────────────────────────────────────────────
@Composable
private fun AmbientGlow(accent: Color) {
    // Two soft radial gradients — one accent-tinted top-left, one plum bottom-right.
    val plum = remember { oklchToColor(0.35f, 0.08f, 300f) }
    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(
                                    Brush.radialGradient(
                                            colors =
                                                    listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                                            radius = 900f,
                                    )
                            ),
    )
    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(
                                    Brush.radialGradient(
                                            colors =
                                                    listOf(plum.copy(alpha = 0.12f), Color.Transparent),
                                            radius = 1100f,
                                    )
                            ),
    )
}

// ── Hero title with graceful fallback ─────────────────────────────────────
@Composable
private fun VinylHeroTitle(title: String) {
    val base = MaterialTheme.sourceText.heroTitle140
    val colors = MaterialTheme.sourceColors
    // Heuristic: if the title is short enough to fit on one or two lines at
    // 140px, keep it. Otherwise drop to 112px per the handoff.
    val large = title.length <= 16
    val style =
            if (large) base
            else
                    base.copy(
                            fontSize = 112.sp,
                    )
    Text(
            text = title,
            style = style.copy(fontStyle = FontStyle.Italic),
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
    )
}

// ── 36px circular art dot ─────────────────────────────────────────────────
@Composable
private fun ArtDot(artUri: String?) {
    Box(
            modifier =
                    Modifier.size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.sourceColors.surface2),
    ) {
        if (artUri != null) {
            AsyncImage(
                    model = artUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ── Scrubber ──────────────────────────────────────────────────────────────
@Composable
private fun VinylScrubber(
        positionMs: Long,
        durationMs: Long,
        accent: Color,
        hair: Color,
        textDim: Color,
        onSeek: (Long) -> Unit,
        modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val display = if (isDragging) dragProgress else progress
    val elapsed = if (isDragging) (dragProgress * durationMs).toLong() else positionMs
    val remainingMs = (durationMs - elapsed).coerceAtLeast(0L)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .height(24.dp)
                                .pointerInput(durationMs) {
                                    detectTapGestures { offset ->
                                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                                        onSeek((f * durationMs).toLong())
                                    }
                                }
                                .pointerInput(durationMs) {
                                    detectHorizontalDragGestures(
                                            onDragStart = { offset ->
                                                isDragging = true
                                                dragProgress =
                                                        (offset.x / size.width).coerceIn(0f, 1f)
                                            },
                                            onDragEnd = {
                                                onSeek((dragProgress * durationMs).toLong())
                                                isDragging = false
                                            },
                                            onDragCancel = { isDragging = false },
                                            onHorizontalDrag = { _, d ->
                                                dragProgress =
                                                        (dragProgress + d / size.width)
                                                                .coerceIn(0f, 1f)
                                            },
                                    )
                                },
                contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                val trackY = size.height / 2
                val stroke = 2.dp.toPx()
                drawLine(
                        color = hair,
                        start = Offset(0f, trackY),
                        end = Offset(size.width, trackY),
                        strokeWidth = stroke,
                )
                val x = size.width * display
                if (x > 0f) {
                    drawLine(
                            color = accent,
                            start = Offset(0f, trackY),
                            end = Offset(x, trackY),
                            strokeWidth = stroke,
                    )
                }
                drawCircle(color = accent, radius = 5.dp.toPx(), center = Offset(x, trackY))
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoText(text = formatDuration(elapsed), color = textDim, size = MonoSize.S105)
            MonoText(
                    text = "-" + formatDuration(remainingMs),
                    color = textDim,
                    size = MonoSize.S105,
            )
        }
    }
}

// ── Sonos volume slider (scoped to Player) ────────────────────────────────
@Composable
private fun SonosVolumeSlider(
        volume: Int?,
        onVolumeChange: (Int) -> Unit,
        modifier: Modifier = Modifier,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val display = dragValue ?: (volume?.toFloat() ?: 0f)
    val colors = MaterialTheme.sourceColors
    Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.VolumeDown, null, tint = colors.textDim, modifier = Modifier.size(18.dp))
        Slider(
                value = display,
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { onVolumeChange(it.toInt()) }
                    dragValue = null
                },
                valueRange = 0f..100f,
                colors =
                        SliderDefaults.colors(
                                thumbColor = colors.accent,
                                activeTrackColor = colors.accent,
                                inactiveTrackColor = colors.hair,
                        ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Icon(Icons.Rounded.VolumeUp, null, tint = colors.textDim, modifier = Modifier.size(18.dp))
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────
/** "SIDE A · 04" style label derived from queue index (1-based). */
private fun sideLabel(index: Int): String {
    val side = if (index < 12) "A" else if (index < 24) "B" else "C"
    return "SIDE $side \u00B7 %02d".format((index + 1).coerceAtLeast(1))
}

fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val min = totalSecs / 60
    val sec = totalSecs % 60
    return "%d:%02d".format(min, sec)
}
