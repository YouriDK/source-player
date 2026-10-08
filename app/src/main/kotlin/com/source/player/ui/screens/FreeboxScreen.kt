package com.source.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.source.player.data.freebox.RemoteEntry
import com.source.player.ui.components.BreadcrumbChip
import com.source.player.ui.components.BrowserFolderRow
import com.source.player.ui.components.BrowserTrackRow
import com.source.player.ui.components.Hairline
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.navigation.Routes
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.FreeboxUiState
import com.source.player.ui.viewmodel.FreeboxViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeboxScreen(
        navController: NavController,
        vm: FreeboxViewModel = hiltViewModel(),
) {
    val isPaired by vm.isPaired.collectAsStateWithLifecycle()
    val boxName by vm.boxName.collectAsStateWithLifecycle()
    val startFolder by vm.startFolder.collectAsStateWithLifecycle()
    val currentFolder by vm.currentFolder.collectAsStateWithLifecycle()
    val breadcrumbs by vm.breadcrumbs.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var sheetTrack by remember { mutableStateOf<RemoteEntry?>(null) }

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    val openSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }

    BackHandler(enabled = breadcrumbs.size > 1) { vm.goUp() }

    Box(Modifier.fillMaxSize().systemBarsPadding().background(colors.bg)) {
        Column(Modifier.fillMaxSize()) {
            // Header ------------------------------------------------
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                        onClick = { if (!vm.goUp()) navController.popBackStack() },
                        modifier = Modifier.size(32.dp),
                ) {
                    Icon(HugeIcons.ArrowLeft, "Back", tint = colors.text)
                }
                Column(Modifier.weight(1f)) {
                    Text(text = "Freebox", style = text.editorialHeadline32, color = colors.text)
                    if (isPaired == true && !boxName.isNullOrBlank()) {
                        Text(
                                boxName!!,
                                style = text.metaSmall115,
                                color = colors.textMute,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isPaired == true) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(HugeIcons.Settings, "Browser options", tint = colors.textDim)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    leadingIcon = { Icon(HugeIcons.Refresh, null) },
                                    onClick = {
                                        menuOpen = false
                                        vm.refresh()
                                    },
                            )
                            val isStart =
                                    startFolder != null && startFolder?.url == currentFolder?.url
                            if (currentFolder != null && !isStart) {
                                DropdownMenuItem(
                                        text = { Text("Set as start folder") },
                                        leadingIcon = { Icon(HugeIcons.Bookmark, null) },
                                        onClick = {
                                            menuOpen = false
                                            vm.setStartFolderHere()
                                        },
                                )
                            }
                            if (startFolder != null) {
                                DropdownMenuItem(
                                        text = { Text("Clear start folder") },
                                        leadingIcon = { Icon(HugeIcons.Close, null) },
                                        onClick = {
                                            menuOpen = false
                                            vm.clearStartFolder()
                                        },
                                )
                            }
                        }
                    }
                }
            }

            when (isPaired) {
                null -> CenteredProgress()
                false ->
                        EmptyState(
                                icon = HugeIcons.Cloud,
                                title = "Connect your Freebox",
                                body =
                                        "Pair Source with your Freebox to browse its disks and play your music.",
                                action = "Connect your Freebox",
                                onAction = openSettings,
                        )
                true -> {
                    // Breadcrumb trail --------------------------------------
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        breadcrumbs.forEachIndexed { index, crumb ->
                            val isLast = index == breadcrumbs.lastIndex
                            BreadcrumbChip(
                                    label = crumb.name,
                                    selected = isLast,
                                    leading = if (index == 0) HugeIcons.Cloud else null,
                                    onClick = { if (!isLast) vm.navigateTo(index) },
                            )
                            if (!isLast) {
                                Icon(
                                        HugeIcons.ChevronRight,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = colors.textMute,
                                )
                            }
                        }
                    }

                    Hairline(modifier = Modifier.padding(horizontal = 24.dp))

                    when (val state = entries) {
                        FreeboxUiState.Loading -> CenteredProgress()
                        is FreeboxUiState.Error ->
                                EmptyState(
                                        icon = HugeIcons.FolderOff,
                                        title = "Can't open this folder",
                                        body = state.message,
                                        action =
                                                when {
                                                    state.needsPairing -> "Open Settings"
                                                    state.retryable -> "Retry"
                                                    else -> null
                                                },
                                        onAction = {
                                            if (state.needsPairing) openSettings() else vm.retry()
                                        },
                                )
                        is FreeboxUiState.Content ->
                                FreeboxListing(
                                        content = state,
                                        atRoot = currentFolder == null,
                                        onOpen = { vm.open(it) },
                                        onPlay = { vm.playTrack(it) },
                                        onMore = { sheetTrack = it },
                                        onPlayAll = { vm.playAll() },
                                        onShuffle = { vm.shuffleAll() },
                                        onQueueFolder = { vm.addFolderToQueue() },
                                )
                    }
                }
            }
        }
    }

    sheetTrack?.let { track ->
        ModalBottomSheet(onDismissRequest = { sheetTrack = null }) {
            Column(
                    Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 16.dp)
            ) {
                Text(
                        track.name,
                        style = text.trackTitle22,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp),
                )
                ListItem(
                        headlineContent = { Text("Play next", style = text.trackTitle22) },
                        leadingContent = { Icon(HugeIcons.Next, null) },
                        modifier =
                                Modifier.clickable {
                                    vm.playNext(track)
                                    sheetTrack = null
                                },
                )
                ListItem(
                        headlineContent = { Text("Add to queue", style = text.trackTitle22) },
                        leadingContent = { Icon(HugeIcons.Queue, null) },
                        modifier =
                                Modifier.clickable {
                                    vm.addToQueue(track)
                                    sheetTrack = null
                                },
                )
            }
        }
    }
}

@Composable
private fun FreeboxListing(
        content: FreeboxUiState.Content,
        atRoot: Boolean,
        onOpen: (RemoteEntry) -> Unit,
        onPlay: (RemoteEntry) -> Unit,
        onMore: (RemoteEntry) -> Unit,
        onPlayAll: () -> Unit,
        onShuffle: () -> Unit,
        onQueueFolder: () -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    if (content.folders.isEmpty() && content.tracks.isEmpty()) {
        EmptyState(
                icon = HugeIcons.MusicNote,
                title = if (atRoot) "No disks found" else "No audio files here",
                body = if (atRoot) "Plug a disk into your Freebox." else null,
        )
        return
    }

    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        if (content.tracks.isNotEmpty()) {
            item(key = "actions") {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                            onClick = onPlayAll,
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.weight(1f),
                    ) { Text("Play all", style = text.pillLabel12) }
                    FilledTonalButton(
                            onClick = onShuffle,
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.weight(1f),
                    ) { Text("Shuffle", style = text.pillLabel12) }
                    IconButton(onClick = onQueueFolder) {
                        Icon(HugeIcons.PlaylistAdd, "Add folder to queue", tint = colors.textDim)
                    }
                }
            }
        }

        items(content.folders, key = { "d:" + it.url }) { folder ->
            Hairline(modifier = Modifier.padding(horizontal = 24.dp))
            BrowserFolderRow(
                    name = folder.name,
                    meta = null,
                    onClick = { onOpen(folder) },
            )
        }

        if (content.folders.isNotEmpty() && content.tracks.isNotEmpty()) {
            item(key = "sep") { Hairline(modifier = Modifier.padding(horizontal = 24.dp)) }
        }

        items(content.tracks, key = { "f:" + it.url }) { track ->
            Hairline(modifier = Modifier.padding(horizontal = 24.dp))
            BrowserTrackRow(
                    title = track.name,
                    subtitle = null,
                    trailingMeta = formatBytes(track.size),
                    onClick = { onPlay(track) },
                    onLongClick = { onMore(track) },
                    trailing = {
                        IconButton(onClick = { onMore(track) }) {
                            Icon(HugeIcons.Queue, "Queue options", tint = colors.textMute)
                        }
                    },
            )
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(32.dp))
    }
}

@Composable
private fun EmptyState(
        icon: ImageVector,
        title: String,
        body: String?,
        action: String? = null,
        onAction: () -> Unit = {},
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, modifier = Modifier.size(48.dp), tint = colors.textMute)
        Spacer(Modifier.height(12.dp))
        Text(
                title,
                style = text.editorialHeadline32,
                color = colors.textDim,
                textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                    body,
                    style = text.metaSmall115,
                    color = colors.textMute,
                    textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(100.dp)) {
                Text(action, style = text.pillLabel12)
            }
        }
    }
}

/** 1536 → "1.5 KB"; binary units, one decimal under 10. */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value < 10) String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
    else "${value.toInt()} ${units[unit]}"
}
