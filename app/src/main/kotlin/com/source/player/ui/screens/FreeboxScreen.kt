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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.source.player.data.freebox.FreeboxFolder
import com.source.player.data.freebox.RemoteEntry
import com.source.player.ui.components.BreadcrumbChip
import com.source.player.ui.components.BrowserEmptyState
import com.source.player.ui.components.BrowserFolderRow
import com.source.player.ui.components.BrowserTrackRow
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.SectionKicker
import com.source.player.ui.components.openMusicFolderPicker
import com.source.player.ui.components.openSettingsTab
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.FreeboxBrowseMode
import com.source.player.ui.viewmodel.FreeboxUiState
import com.source.player.ui.viewmodel.FreeboxViewModel

/**
 * Live Freebox browser, in one of two [FreeboxBrowseMode]s:
 * - MUSIC_FOLDER — content of the Folders tab in Stream mode, rooted at the music folder
 *   (no way up to the disks); [banner] is the Stream status strip.
 * - PICK — the music folder picker, rooted at the disks, with "Choose this folder" and
 *   suggestions; pops itself once a folder is chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeboxScreen(
        navController: NavController,
        vm: FreeboxViewModel = hiltViewModel(),
        banner: (@Composable () -> Unit)? = null,
) {
    val isPaired by vm.isPaired.collectAsStateWithLifecycle()
    val boxName by vm.boxName.collectAsStateWithLifecycle()
    val currentFolder by vm.currentFolder.collectAsStateWithLifecycle()
    val breadcrumbs by vm.breadcrumbs.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val chosen by vm.chosen.collectAsStateWithLifecycle()

    val picking = vm.mode == FreeboxBrowseMode.PICK
    var sheetTrack by remember { mutableStateOf<RemoteEntry?>(null) }

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    val openSettings = { navController.openSettingsTab() }

    LaunchedEffect(chosen) { if (chosen) navController.popBackStack() }

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
                // The picker can always leave; the Folders tab only goes back up its tree.
                if (picking || breadcrumbs.size > 1) {
                    IconButton(
                            onClick = { if (!vm.goUp()) navController.popBackStack() },
                            modifier = Modifier.size(32.dp),
                    ) {
                        Icon(HugeIcons.ArrowLeft, "Back", tint = colors.text)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                            text = if (picking) "Music folder" else "Folders",
                            style = text.editorialHeadline32,
                            color = colors.text,
                    )
                    val subtitle = if (picking) "Choose your music folder" else boxName
                    if (isPaired == true && !subtitle.isNullOrBlank()) {
                        Text(
                                subtitle,
                                style = text.metaSmall115,
                                color = colors.textMute,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isPaired == true) {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(HugeIcons.Refresh, "Refresh", tint = colors.textDim)
                    }
                }
            }

            banner?.invoke()

            when (isPaired) {
                null -> CenteredProgress()
                false ->
                        BrowserEmptyState(
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

                    // Picker: every screen but the disks can be chosen.
                    if (picking && currentFolder != null) {
                        Button(
                                onClick = { vm.chooseCurrent() },
                                enabled = !chosen,
                                shape = RoundedCornerShape(100.dp),
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 24.dp, vertical = 14.dp),
                        ) { Text("Choose this folder", style = text.pillLabel12) }
                    }

                    when (val state = entries) {
                        FreeboxUiState.Loading -> CenteredProgress()
                        is FreeboxUiState.Error ->
                                BrowserEmptyState(
                                        icon = HugeIcons.FolderOff,
                                        title =
                                                if (state.musicFolderMissing) "Music folder not found"
                                                else "Can't open this folder",
                                        body = state.message,
                                        action =
                                                when {
                                                    state.musicFolderMissing -> "Choose your music folder"
                                                    state.needsPairing -> "Open Settings"
                                                    state.retryable -> "Retry"
                                                    else -> null
                                                },
                                        onAction = {
                                            when {
                                                state.musicFolderMissing ->
                                                        navController.openMusicFolderPicker()
                                                state.needsPairing -> openSettings()
                                                else -> vm.retry()
                                            }
                                        },
                                )
                        is FreeboxUiState.Content ->
                                if (picking) {
                                    FreeboxPickerListing(
                                            content = state,
                                            atRoot = currentFolder == null,
                                            suggestions = if (currentFolder == null) suggestions else emptyList(),
                                            onOpen = { vm.open(it) },
                                            onOpenFolder = { vm.open(it) },
                                            onChoose = { vm.choose(it) },
                                    )
                                } else {
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

/** Picker body: suggestions (disks root only), then sub-folders; tracks are only counted. */
@Composable
private fun FreeboxPickerListing(
        content: FreeboxUiState.Content,
        atRoot: Boolean,
        suggestions: List<FreeboxFolder>,
        onOpen: (RemoteEntry) -> Unit,
        onOpenFolder: (FreeboxFolder) -> Unit,
        onChoose: (FreeboxFolder) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    if (content.folders.isEmpty() && content.tracks.isEmpty() && suggestions.isEmpty()) {
        BrowserEmptyState(
                icon = HugeIcons.FolderOff,
                title = if (atRoot) "No disks found" else "Empty folder",
                body = if (atRoot) "Plug a disk into your Freebox." else null,
        )
        return
    }

    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        if (suggestions.isNotEmpty()) {
            item(key = "suggested-kicker") {
                SectionKicker(
                        label = "Suggested",
                        color = colors.accent,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(suggestions, key = { "s:" + it.url }) { folder ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        BrowserFolderRow(
                                name = "Suggested: ${folder.name}",
                                meta = "/" + folder.displayPath,
                                onClick = { onOpenFolder(folder) },
                        )
                    }
                    TextButton(
                            onClick = { onChoose(folder) },
                            modifier = Modifier.padding(end = 12.dp),
                    ) { Text("Choose", style = text.pillLabel12, color = colors.accent) }
                }
            }
            item(key = "suggested-sep") {
                Hairline(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
        }

        if (content.tracks.isNotEmpty()) {
            item(key = "track-count") {
                Text(
                        "${content.tracks.size} audio files in this folder",
                        style = text.metaSmall115,
                        color = colors.textMute,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
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
        BrowserEmptyState(
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
