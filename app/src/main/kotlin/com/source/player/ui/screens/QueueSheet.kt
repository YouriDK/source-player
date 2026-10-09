package com.source.player.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.PlayerViewModel

/**
 * The queue, pulled up over the player: same tracklist as the former Queue tab, the
 * current track accent-tinted. Drag a row's grip (or long-press the row) to move it,
 * swipe it left to remove it. Material 3 modal sheet, opened full height — dragging it
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
    // Follows the playing track (and lands on it when the sheet opens), but not the
    // index shifts a reorder or a removal causes: those keep the list where it is.
    val currentMediaId = queue.getOrNull(currentIndex)?.mediaId
    LaunchedEffect(currentMediaId) {
        if (keyedQueue.isNotEmpty()) {
            // runCatching: the scroll animation races queue swaps by design;
            // a cancelled/invalid scroll must never take the sheet down.
            runCatching {
                listState.animateScrollToItem(currentIndex.coerceIn(0, keyedQueue.lastIndex))
            }
        }
    }

    // ── Drag to reorder ──────────────────────────────────────────────────
    // While a row is dragged the list shows a local copy reordered as it moves;
    // the controller gets a single move on release. The copy stays on screen
    // until the controller's new queue arrives, so the row never snaps back.
    var localOrder by remember { mutableStateOf<List<Pair<String, MediaItem>>?>(null) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    val dragOffset = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(keyedQueue) { if (draggedKey == null) localOrder = null }
    val rows = localOrder ?: keyedQueue
    // The gesture handlers outlive this composition (keyed on the row): they read
    // the queue through this, never a captured snapshot.
    val latestQueue by rememberUpdatedState(keyedQueue)

    fun startDrag(key: String) {
        val queueNow = latestQueue
        val index = queueNow.indexOfFirst { it.first == key }
        if (index < 0) return
        localOrder = queueNow
        draggedKey = key
        dragFrom = index
        dragOffset.floatValue = 0f
    }

    fun drag(dy: Float) {
        val key = draggedKey ?: return
        val order = localOrder ?: return
        dragOffset.floatValue += dy
        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == key } ?: return
        // Last swap not laid out yet: offsets are stale, wait for the next pass.
        if (dragged.index != order.indexOfFirst { it.first == key }) return
        val center = dragged.offset + dragged.size / 2f + dragOffset.floatValue
        val target =
                visible.firstOrNull {
                    it.key != key && it.key is String && center.toInt() in it.offset until it.offset + it.size
                } ?: return
        val from = order.indexOfFirst { it.first == key }
        val to = order.indexOfFirst { it.first == target.key }
        if (from < 0 || to < 0) return
        localOrder = order.toMutableList().apply { add(to, removeAt(from)) }
        // The row now sits in the target's slot: keep it under the finger.
        dragOffset.floatValue += (dragged.offset - target.offset).toFloat()
    }

    fun endDrag() {
        val key = draggedKey ?: return
        val to = localOrder?.indexOfFirst { it.first == key } ?: -1
        draggedKey = null
        dragOffset.floatValue = 0f
        if (to < 0 || !vm.moveQueueItem(dragFrom, to)) localOrder = null
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
            itemsIndexed(rows, key = { _, entry -> entry.first }) { i, entry ->
                val item = entry.second
                // During a drag, the playing row is found by identity in the local order.
                val isCurrent =
                        if (localOrder == null) i == currentIndex
                        else item === keyedQueue.getOrNull(currentIndex)?.second
                val isPlayed = localOrder == null && i < currentIndex
                val isDragged = entry.first == draggedKey
                val index by rememberUpdatedState(i)

                // Swipe left to drop the track. The state is per lazy key, and keys
                // are reused by duplicates of a removed track: reset it when the
                // row's position changes so a reused key never starts dismissed.
                val dismissState =
                        rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.EndToStart) {
                                        vm.removeQueueItem(index)
                                        true
                                    } else false
                                }
                        )
                LaunchedEffect(i) {
                    if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                }

                // animateItem: removing a track or reordering the queue slides the
                // remaining rows instead of snapping them; the dragged row follows
                // the finger instead.
                Column(
                        modifier =
                                (if (isDragged) Modifier.zIndex(1f) else Modifier.animateItem())
                                        .graphicsLayer {
                                            if (isDragged) {
                                                translationY = dragOffset.floatValue
                                                shadowElevation = 8.dp.toPx()
                                            }
                                        }
                                        .background(colors.bg)
                ) {
                Hairline(modifier = Modifier.padding(horizontal = 24.dp))
                SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        gesturesEnabled = draggedKey == null,
                        backgroundContent = {
                            Box(
                                    modifier =
                                            Modifier.fillMaxSize()
                                                    .background(colors.error.copy(alpha = 0.18f))
                                                    .padding(horizontal = 24.dp),
                                    contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                        HugeIcons.RemoveCircle,
                                        contentDescription = "Remove from queue",
                                        tint = colors.error,
                                )
                            }
                        },
                ) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(colors.bg)
                                        .pointerInput(entry.first) {
                                            detectDragGesturesAfterLongPress(
                                                    onDragStart = { startDrag(entry.first) },
                                                    onDragEnd = { endDrag() },
                                                    onDragCancel = { endDrag() },
                                            ) { change, amount ->
                                                change.consume()
                                                drag(amount.y)
                                            }
                                        }
                                        .clickable { vm.skipToQueueItem(i) }
                                        .alpha(if (isPlayed) 0.45f else 1f)
                                        .padding(start = 24.dp, top = 14.dp, bottom = 14.dp),
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

                    // Grip: drag it straight away (a long press anywhere on the row
                    // works too) to move the track.
                    DragGrip(
                            color = colors.textMute,
                            modifier =
                                    Modifier.pointerInput(entry.first) {
                                        detectVerticalDragGestures(
                                                onDragStart = { startDrag(entry.first) },
                                                onDragEnd = { endDrag() },
                                                onDragCancel = { endDrag() },
                                        ) { change, amount ->
                                            change.consume()
                                            drag(amount)
                                        }
                                    },
                    )
                }
                }
                if (i == rows.lastIndex) {
                    Hairline(modifier = Modifier.padding(horizontal = 24.dp))
                }
                }
            }
        }
    }
}

/** Three short bars: the row can be dragged. 48dp wide so the thumb finds it. */
@Composable
private fun DragGrip(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.padding(top = 2.dp).size(width = 48.dp, height = 24.dp)) {
        val w = 14.dp.toPx()
        val stroke = 1.5.dp.toPx()
        val x = (size.width - w) / 2f
        for (k in -1..1) {
            val y = size.height / 2f + k * 5.dp.toPx()
            drawLine(color, Offset(x, y), Offset(x + w, y), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}
