package com.source.player.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.ui.navigation.Routes
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
        val queueItems by vm.queueItems.collectAsState()
        val songId by vm.currentSongId.collectAsState()

        val pagerState =
                rememberPagerState(
                        initialPage = queueIndex,
                        pageCount = { maxOf(queueItems.size, 1) },
                )

        // ---- Swipe bug fix ----
        // `isProgrammaticScroll` gates whether the settled-page observer should trigger a skip.
        // We set it ON before any `animateScrollToPage` we initiate ourselves, and OFF after
        // settle.
        val isProgrammaticScroll = remember { mutableStateOf(false) }

        // External queue change (via controls, queue tap, etc.) → animate pager silently
        LaunchedEffect(queueIndex) {
                if (pagerState.currentPage != queueIndex) {
                        isProgrammaticScroll.value = true
                        pagerState.animateScrollToPage(queueIndex)
                        // settledPage will update after animation; gate is reset in the settled
                        // observer below
                }
        }

        // User swipe → trigger skip, but only when we didn't cause the scroll ourselves
        LaunchedEffect(pagerState.settledPage) {
                if (isProgrammaticScroll.value) {
                        // This settle was caused by our own animateScrollToPage — clear the gate
                        // and ignore
                        isProgrammaticScroll.value = false
                        return@LaunchedEffect
                }
                val settled = pagerState.settledPage
                if (settled != queueIndex) {
                        if (shuffleEnabled) {
                                // In shuffle mode, use next/prev to respect the shuffle order
                                if (settled > queueIndex) vm.skipToNext() else vm.skipToPrevious()
                        } else {
                                vm.skipToQueueItem(settled)
                        }
                }
        }

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // Blurred background art
                song?.let {
                        AsyncImage(
                                model = it.mediaMetadata.artworkUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.25f),
                        )
                }

                // Dark gradient overlay
                Box(
                        Modifier.fillMaxSize()
                                .background(
                                        Brush.verticalGradient(
                                                listOf(
                                                        Color.Transparent,
                                                        MaterialTheme.colorScheme.background.copy(
                                                                alpha = 0.95f
                                                        )
                                                ),
                                                startY = 0f,
                                                endY = Float.POSITIVE_INFINITY,
                                        )
                                )
                )

                Column(
                        modifier =
                                Modifier.fillMaxSize()
                                        .systemBarsPadding()
                                        .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                        // Top bar
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                        ) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(
                                                Icons.Rounded.KeyboardArrowDown,
                                                "Close",
                                                tint = MaterialTheme.colorScheme.onSurface
                                        )
                                }
                                Text(
                                        "Now Playing",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(onClick = { navController.navigate(Routes.QUEUE) }) {
                                        Icon(
                                                Icons.Rounded.QueueMusic,
                                                "Queue",
                                                tint = MaterialTheme.colorScheme.onSurface
                                        )
                                }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Swipeable album art
                        HorizontalPager(
                                state = pagerState,
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(24.dp)),
                                pageSpacing = 16.dp,
                        ) { page ->
                                val artUri = queueItems.getOrNull(page)?.mediaMetadata?.artworkUri
                                AlbumArtCard(artUri)
                        }

                        Spacer(Modifier.height(32.dp))

                        // Song info — tap to edit tags
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clickable(enabled = songId != null) {
                                                        songId?.let {
                                                                navController.navigate(
                                                                        Routes.tagEditor(it)
                                                                )
                                                        }
                                                }
                                                .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                        ) {
                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                text = song?.mediaMetadata?.title?.toString()
                                                                ?: "—",
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                                text = song?.mediaMetadata?.artist?.toString()
                                                                ?: "—",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                        )
                                }
                                Icon(
                                        Icons.Rounded.Edit,
                                        "Edit tags",
                                        modifier = Modifier.size(20.dp),
                                        tint =
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.6f
                                                )
                                )
                        }

                        Spacer(Modifier.height(24.dp))

                        // Progress bar + time
                        val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                        Slider(
                                value = progress,
                                onValueChange = { vm.seekTo((it * durationMs).toLong()) },
                                colors =
                                        SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor =
                                                        MaterialTheme.colorScheme.primary,
                                        ),
                                modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Text(
                                        formatDuration(positionMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                        formatDuration(durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }

                        Spacer(Modifier.height(24.dp))

                        // Controls
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                        ) {
                                IconButton(onClick = { vm.setShuffleEnabled(!shuffleEnabled) }) {
                                        Icon(
                                                Icons.Rounded.Shuffle,
                                                "Shuffle",
                                                tint =
                                                        if (shuffleEnabled)
                                                                MaterialTheme.colorScheme.primary
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                        )
                                }
                                IconButton(
                                        onClick = { vm.skipToPrevious() },
                                        modifier = Modifier.size(52.dp)
                                ) {
                                        Icon(
                                                Icons.Rounded.SkipPrevious,
                                                "Previous",
                                                modifier = Modifier.size(32.dp),
                                                tint = MaterialTheme.colorScheme.onSurface
                                        )
                                }
                                FilledIconButton(
                                        onClick = { if (isPlaying) vm.pause() else vm.play() },
                                        modifier = Modifier.size(72.dp),
                                        shape = CircleShape,
                                        colors =
                                                IconButtonDefaults.filledIconButtonColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.primary
                                                ),
                                ) {
                                        Icon(
                                                if (isPlaying) Icons.Rounded.Pause
                                                else Icons.Rounded.PlayArrow,
                                                if (isPlaying) "Pause" else "Play",
                                                modifier = Modifier.size(36.dp),
                                                tint = Color.White,
                                        )
                                }
                                IconButton(
                                        onClick = { vm.skipToNext() },
                                        modifier = Modifier.size(52.dp)
                                ) {
                                        Icon(
                                                Icons.Rounded.SkipNext,
                                                "Next",
                                                modifier = Modifier.size(32.dp),
                                                tint = MaterialTheme.colorScheme.onSurface
                                        )
                                }
                                IconButton(onClick = { vm.cycleRepeatMode() }) {
                                        Icon(
                                                when (repeatMode) {
                                                        1 -> Icons.Rounded.Repeat
                                                        2 -> Icons.Rounded.RepeatOne
                                                        else -> Icons.Rounded.Repeat
                                                },
                                                "Repeat",
                                                tint =
                                                        if (repeatMode != 0)
                                                                MaterialTheme.colorScheme.primary
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                        )
                                }
                        }
                }
        }
}

@Composable
private fun AlbumArtCard(artUri: Uri?) {
        Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(16.dp)
        ) {
                if (artUri != null) {
                        AsyncImage(
                                model = artUri,
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                        )
                } else {
                        Box(
                                Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                Alignment.Center
                        ) {
                                Icon(
                                        Icons.Rounded.Album,
                                        null,
                                        modifier = Modifier.size(80.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                }
        }
}

fun formatDuration(ms: Long): String {
        val totalSecs = ms / 1000
        val min = totalSecs / 60
        val sec = totalSecs % 60
        return "%d:%02d".format(min, sec)
}
