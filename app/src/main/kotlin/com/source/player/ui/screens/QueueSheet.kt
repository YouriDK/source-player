package com.source.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.PlayerViewModel

/**
 * The queue, pulled up over the player: same tracklist as the former Queue tab, the
 * current track accent-tinted. Material 3 modal sheet, opened full height — dragging it
 * down or tapping the scrim / Back closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
        onDismiss: () -> Unit,
        vm: PlayerViewModel = hiltViewModel(),
) {
    val colors = MaterialTheme.sourceColors
    ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.bg,
            contentColor = colors.text,
            scrimColor = Color.Black.copy(alpha = 0.5f),
            dragHandle = { BottomSheetDefaults.DragHandle(color = colors.textMute) },
    ) {
        QueueList(vm)
    }
}

@Composable
private fun QueueList(vm: PlayerViewModel) {
    val queue by vm.queueItems.collectAsStateWithLifecycle()
    val currentIndex by vm.queueIndex.collectAsStateWithLifecycle()

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    // Occurrence-stable keys: the same mediaId can appear multiple times in the
    // queue, so each duplicate gets a "#n" suffix by occurrence order. Key and
    // item are fused into ONE remembered list — LazyColumn measure can run
    // between recompositions (e.g. during the auto-scroll animation below), and
    // a separate keys list let it observe the row count of one queue snapshot
    // with the keys of another (IndexOutOfBounds crash on queue open).
    val keyedQueue =
            remember(queue) {
                val seen = HashMap<String, Int>()
                queue.map { item ->
                    val occurrence = (seen[item.mediaId] ?: 0) + 1
                    seen[item.mediaId] = occurrence
                    "${item.mediaId}#$occurrence" to item
                }
            }

    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex, keyedQueue.size) {
        if (keyedQueue.isNotEmpty()) {
            // runCatching: the scroll animation races queue swaps by design;
            // a cancelled/invalid scroll must never take the sheet down.
            runCatching {
                listState.animateScrollToItem(currentIndex.coerceIn(0, keyedQueue.lastIndex))
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        // Header: "Queue · 4 / 12"
        Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "Queue", style = text.editorialHeadline32, color = colors.text)
            MonoText(
                    text =
                            if (keyedQueue.isEmpty()) "\u00B7 Empty"
                            else "\u00B7 ${(currentIndex + 1).coerceAtMost(keyedQueue.size)} / ${keyedQueue.size}",
                    color = colors.textMute,
                    size = MonoSize.S10,
                    modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(keyedQueue, key = { _, entry -> entry.first }) { i, entry ->
                val item = entry.second
                val isCurrent = i == currentIndex
                val isPlayed = i < currentIndex
                // animateItem: removing a track or reordering the queue slides the
                // remaining rows instead of snapping them to their new positions.
                Hairline(modifier = Modifier.animateItem().padding(horizontal = 24.dp))
                Row(
                        modifier =
                                Modifier.animateItem()
                                        .fillMaxWidth()
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
                                style = text.trackTitle22,
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
                if (i == keyedQueue.lastIndex) {
                    Hairline(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }
}
