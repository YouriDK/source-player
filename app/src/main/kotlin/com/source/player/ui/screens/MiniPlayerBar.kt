package com.source.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.source.player.ui.viewmodel.PlayerViewModel

/**
 * MiniPlayerBar — persistent bottom bar shown across all non-fullscreen screens.
 *
 * - Tapping the bar → onTap() opens full Player screen
 * - Play / Pause toggles without navigation
 * - Real-time progress bar at the very bottom edge
 */
@Composable
fun MiniPlayerBar(
        modifier: Modifier = Modifier,
        onTap: () -> Unit,
        vm: PlayerViewModel = hiltViewModel(),
) {
  val song by vm.currentSong.collectAsState()
  val isPlaying by vm.isPlaying.collectAsState()
  val positionMs by vm.positionMs.collectAsState()
  val durationMs by vm.durationMs.collectAsState()

  AnimatedVisibility(
          visible = song != null,
          enter = slideInVertically { it } + fadeIn(),
          exit = slideOutVertically { it } + fadeOut(),
          modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    Card(
            onClick = onTap,
            shape = RoundedCornerShape(20.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
            elevation = CardDefaults.cardElevation(8.dp),
    ) {
      Column {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // Album art
          AsyncImage(
                  model = song?.mediaMetadata?.artworkUri,
                  contentDescription = "Album Art",
                  contentScale = ContentScale.Crop,
                  modifier =
                          Modifier.size(44.dp)
                                  .clip(RoundedCornerShape(10.dp))
                                  .background(MaterialTheme.colorScheme.outline),
          )

          // Title + artist
          Column(Modifier.weight(1f)) {
            Text(
                    text = song?.mediaMetadata?.title?.toString() ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                    text = song?.mediaMetadata?.artist?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          // Play / Pause
          IconButton(
                  onClick = { if (isPlaying) vm.pause() else vm.play() },
                  modifier = Modifier.size(40.dp),
          ) {
            Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
            )
          }

          // Next
          IconButton(
                  onClick = { vm.skipToNext() },
                  modifier = Modifier.size(40.dp),
          ) {
            Icon(
                    Icons.Rounded.SkipNext,
                    "Next",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
            )
          }
        }

        // Progress line
        LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
        )
      }
    }
  }
}
