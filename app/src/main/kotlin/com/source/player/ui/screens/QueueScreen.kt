package com.source.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.components.SectionKicker
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.PlayerViewModel

/**
 * Queue — LP back-cover metaphor. Focused secondary view, no mini-player, no
 * tab bar. Current track is accent-tinted and italicised.
 */
@Composable
fun QueueScreen(
        @Suppress("unused") navController: NavController,
        vm: PlayerViewModel = hiltViewModel(),
) {
    val queue by vm.queueItems.collectAsState()
    val currentIndex by vm.queueIndex.collectAsState()

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex, queue.size) {
        if (currentIndex in queue.indices) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().background(colors.bg)) {
        // Header block: kicker + "What's / next."
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 18.dp)) {
            SectionKicker(label = "B Side \u00B7 Tracklist")
            Spacer(Modifier.height(6.dp))
            Text(
                    text = "What's\nnext.",
                    style = text.queueHeadline56,
                    color = colors.text,
            )
        }

        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(queue, key = { i, item -> "${item.mediaId}_$i" }) { i, item ->
                val isCurrent = i == currentIndex
                val isPlayed = i < currentIndex
                Hairline(modifier = Modifier.padding(horizontal = 24.dp))
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clickable { vm.skipToQueueItem(i) }
                                        .alpha(if (isPlayed) 0.45f else 1f)
                                        .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.Top,
                ) {
                    // Track number — mono, 28dp fixed column
                    MonoText(
                            text = "%02d".format(i + 1),
                            color =
                                    if (isCurrent) colors.accent
                                    else colors.textMute.copy(alpha = 0.55f),
                            modifier = Modifier.width(28.dp).padding(top = 4.dp),
                    )

                    Spacer(Modifier.width(14.dp))

                    // Title + artist
                    Column(modifier = Modifier.weight(1f)) {
                        val titleText =
                                (item.mediaMetadata.title?.toString() ?: "—") +
                                        if (isCurrent) " \u00B7" else ""
                        Text(
                                text = titleText,
                                style =
                                        if (isCurrent)
                                                text.trackTitle22.copy(fontStyle = FontStyle.Italic)
                                        else text.trackTitle22,
                                color = if (isCurrent) colors.accent else colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                                text = item.mediaMetadata.artist?.toString() ?: "—",
                                style = text.metaSmall115,
                                color =
                                        if (isCurrent) colors.accent.copy(alpha = 0.8f)
                                        else colors.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    // Duration (if available in metadata). MediaItem doesn't
                    // expose duration directly — we rely on the extras bundle
                    // being set elsewhere; leave blank when unknown.
                    val durationMs =
                            item.mediaMetadata.extras?.getLong("durationMs") ?: 0L
                    if (durationMs > 0) {
                        MonoText(
                                text = formatDuration(durationMs),
                                color =
                                        if (isCurrent) colors.accent.copy(alpha = 0.8f)
                                        else colors.textMute.copy(alpha = 0.6f),
                                size = MonoSize.S10,
                                modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (i == queue.lastIndex) {
                    Hairline(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }
}
