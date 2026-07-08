package com.source.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.source.player.ui.components.Hairline
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.PlayerViewModel

/**
 * Vinyl mini-player — full-bleed, flat, hair top border, 2px accent progress
 * line at the bottom edge. No rounded card; this reads as part of the chrome,
 * not a floating widget.
 */
@Composable
fun MiniPlayerBar(
        modifier: Modifier = Modifier,
        onTap: () -> Unit,
        vm: PlayerViewModel = hiltViewModel(),
) {
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    // Deliberately NOT delegated with `by`: the values are read only inside the
    // progress-indicator lambda (draw phase). Reading them here would recompose
    // this whole bar — overlaid on every screen — on every 300ms position tick.
    val positionMs = vm.positionMs.collectAsStateWithLifecycle()
    val durationMs = vm.durationMs.collectAsStateWithLifecycle()

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    AnimatedVisibility(
            visible = song != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().background(colors.bg).clickable(onClick = onTap)) {
            Hairline()
            Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                        model = song?.mediaMetadata?.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                                Modifier.size(44.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(colors.surface2),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = song?.mediaMetadata?.title?.toString() ?: "",
                            style = text.rotationTitle18,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.text,
                    )
                    Text(
                            text = song?.mediaMetadata?.artist?.toString() ?: "",
                            style = text.metaSmall115,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.textDim,
                    )
                }
                IconButton(
                        onClick = { if (isPlaying) vm.pause() else vm.play() },
                        modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = colors.text,
                            modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { vm.skipToNext() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = colors.textDim,
                            modifier = Modifier.size(22.dp),
                    )
                }
            }
            LinearProgressIndicator(
                    progress = {
                        val duration = durationMs.value
                        if (duration > 0) positionMs.value.toFloat() / duration else 0f
                    },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = colors.accent,
                    trackColor = Color.Transparent,
                    drawStopIndicator = {},
            )
        }
    }
}
