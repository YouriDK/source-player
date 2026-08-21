package com.source.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.source.player.ui.components.Hairline
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.PlayerViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Fraction of the bar's width a drag must pass to count as a dismiss. */
private const val DismissFraction = 0.35f

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

    // Horizontal swipe dismisses playback. The offset is an Animatable so the
    // release can either spring back or carry the bar the rest of the way out,
    // and it lives outside AnimatedVisibility so a dismiss doesn't fight the
    // enter/exit slide.
    val dragScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var barWidth by remember { mutableIntStateOf(0) }

    AnimatedVisibility(
            visible = song != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = modifier.fillMaxWidth(),
    ) {
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .onSizeChanged { barWidth = it.width }
                                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                                // Fade with travel so a half-swipe reads as "this is
                                // leaving" rather than a bar that just slid sideways.
                                .alpha(
                                        if (barWidth == 0) 1f
                                        else (1f - abs(offsetX.value) / barWidth).coerceIn(0.3f, 1f)
                                )
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                            onDragEnd = {
                                                val width = barWidth
                                                val past =
                                                        width > 0 &&
                                                                abs(offsetX.value) >
                                                                        width * DismissFraction
                                                dragScope.launch {
                                                    if (past) {
                                                        // Finish the throw off-screen, then drop
                                                        // playback so the bar never reappears
                                                        // mid-animation.
                                                        val target =
                                                                if (offsetX.value > 0) width.toFloat()
                                                                else -width.toFloat()
                                                        offsetX.animateTo(target, tween(180))
                                                        vm.dismissPlayback()
                                                        offsetX.snapTo(0f)
                                                    } else {
                                                        offsetX.animateTo(0f, tween(200))
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                dragScope.launch { offsetX.animateTo(0f, tween(200)) }
                                            },
                                    ) { change, dragAmount ->
                                        change.consume()
                                        dragScope.launch {
                                            offsetX.snapTo(offsetX.value + dragAmount)
                                        }
                                    }
                                }
                                .background(colors.bg)
                                .clickable(onClick = onTap)
        ) {
            Hairline()
            Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Cover and text cross-dissolve on a track change so the bar reads as
                // one continuous surface rather than flickering to new content.
                AnimatedContent(
                        targetState = song?.mediaMetadata?.artworkUri,
                        transitionSpec = {
                            fadeIn(tween(220)).togetherWith(fadeOut(tween(160)))
                        },
                        label = "miniArtwork",
                        modifier = Modifier.size(44.dp),
                ) { artworkUri ->
                    AsyncImage(
                            model = artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                    Modifier.fillMaxSize()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(colors.surface2),
                    )
                }
                AnimatedContent(
                        targetState =
                                (song?.mediaMetadata?.title?.toString() ?: "") to
                                        (song?.mediaMetadata?.artist?.toString() ?: ""),
                        transitionSpec = {
                            fadeIn(tween(220)).togetherWith(fadeOut(tween(160)))
                        },
                        label = "miniLabels",
                        modifier = Modifier.weight(1f),
                ) { (title, artist) ->
                    Column {
                        Text(
                                text = title,
                                style = text.rotationTitle18,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.text,
                        )
                        Text(
                                text = artist,
                                style = text.metaSmall115,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.textDim,
                        )
                    }
                }
                IconButton(
                        onClick = { if (isPlaying) vm.pause() else vm.play() },
                        modifier = Modifier.size(40.dp),
                ) {
                    AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = {
                                (fadeIn(tween(120)) + scaleIn(tween(180), initialScale = 0.7f))
                                        .togetherWith(
                                                fadeOut(tween(90)) +
                                                        scaleOut(tween(180), targetScale = 0.7f)
                                        )
                            },
                            label = "miniPlayPause",
                    ) { playing ->
                        Icon(
                                if (playing) HugeIcons.Pause else HugeIcons.Play,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = colors.text,
                                modifier = Modifier.size(24.dp),
                        )
                    }
                }
                IconButton(onClick = { vm.skipToNext() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                            HugeIcons.Next,
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
