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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.source.player.data.db.entity.SongEntity
import com.source.player.ui.components.BreadcrumbChip
import com.source.player.ui.components.BrowserFolderRow
import com.source.player.ui.components.BrowserTrackRow
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.navigation.Routes
import com.source.player.ui.viewmodel.FoldersViewModel
import com.source.player.ui.viewmodel.FreeboxStatusViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
        navController: NavController,
        vm: FoldersViewModel = hiltViewModel(),
        freeboxVm: FreeboxStatusViewModel = hiltViewModel(),
) {
        val freeboxPaired by freeboxVm.isPaired.collectAsStateWithLifecycle()
        val freeboxName by freeboxVm.boxName.collectAsStateWithLifecycle()
        val breadcrumbs by vm.breadcrumbs.collectAsStateWithLifecycle()
        val subFolders by vm.subFolders.collectAsStateWithLifecycle()
        val songsInFolder by vm.songsInFolder.collectAsStateWithLifecycle()
        val totalSongs by vm.totalSongsCount.collectAsStateWithLifecycle()
        val currentPath by vm.currentPath.collectAsStateWithLifecycle()
        val playlists by vm.playlists.collectAsStateWithLifecycle()

        var showPlaylistSheet by remember { mutableStateOf(false) }
        var showNewPlaylistDialog by remember { mutableStateOf(false) }
        var newPlaylistName by remember { mutableStateOf("") }

        val colors = MaterialTheme.sourceColors
        val text = MaterialTheme.sourceText

        BackHandler(enabled = breadcrumbs.size > 1) { vm.popBack() }

        Box(Modifier.fillMaxSize().systemBarsPadding().background(colors.bg)) {
                Column(Modifier.fillMaxSize()) {
                        // Header ------------------------------------------------
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(
                                                        start = 24.dp,
                                                        end = 24.dp,
                                                        top = 14.dp,
                                                        bottom = 8.dp
                                                ),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                        if (breadcrumbs.size > 1) {
                                                IconButton(
                                                        onClick = { vm.popBack() },
                                                        modifier = Modifier.size(32.dp),
                                                ) {
                                                        Icon(
                                                                HugeIcons.ArrowLeft,
                                                                "Back",
                                                                tint = colors.text,
                                                        )
                                                }
                                        }
                                        Text(
                                                text = "Folders",
                                                style = text.editorialHeadline32,
                                                color = colors.text,
                                        )
                                }
                                if (totalSongs > 0) {
                                        MonoText(
                                                text = "$totalSongs",
                                                color = colors.textMute,
                                                size = MonoSize.S10,
                                        )
                                }
                        }

                        // Breadcrumb trail --------------------------------------
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                                breadcrumbs.forEachIndexed { index, (label, _) ->
                                        val isLast = index == breadcrumbs.lastIndex
                                        BreadcrumbChip(
                                                label = label,
                                                selected = isLast,
                                                leading = if (index == 0) HugeIcons.Folder else null,
                                                onClick = {
                                                        if (!isLast) vm.navigateToBreadcrumb(index)
                                                },
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

                        // Body --------------------------------------------------
                        LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                                if (currentPath != null && totalSongs > 0) {
                                        item {
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(
                                                                                horizontal = 24.dp,
                                                                                vertical = 14.dp,
                                                                        ),
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                ) {
                                                        Button(
                                                                onClick = { vm.playAll() },
                                                                shape = RoundedCornerShape(100.dp),
                                                                modifier = Modifier.weight(1f),
                                                        ) {
                                                                Text(
                                                                        "Play all",
                                                                        style = text.pillLabel12,
                                                                )
                                                        }
                                                        FilledTonalButton(
                                                                onClick = { vm.shuffleAll() },
                                                                shape = RoundedCornerShape(100.dp),
                                                                modifier = Modifier.weight(1f),
                                                        ) {
                                                                Text(
                                                                        "Shuffle",
                                                                        style = text.pillLabel12,
                                                                )
                                                        }
                                                        IconButton(
                                                                onClick = {
                                                                        showPlaylistSheet = true
                                                                }
                                                        ) {
                                                                Icon(
                                                                        HugeIcons.PlaylistAdd,
                                                                        "Add Folder to Playlist",
                                                                        tint = colors.textDim,
                                                                )
                                                        }
                                                }
                                        }
                                }

                                if (currentPath == null) {
                                        item(key = "freebox-entry") {
                                                BrowserFolderRow(
                                                        name = "Freebox",
                                                        meta =
                                                                if (freeboxPaired) freeboxName ?: "Browse your Freebox"
                                                                else "Connect in Settings",
                                                        icon = HugeIcons.Cloud,
                                                        onClick = { navController.navigate(Routes.FREEBOX) },
                                                )
                                        }
                                }

                                if (subFolders.isNotEmpty()) {
                                        items(subFolders, key = { it.path }) { folder ->
                                                Hairline(
                                                        modifier = Modifier.padding(horizontal = 24.dp)
                                                )
                                                FolderRow(
                                                        folder = folder,
                                                        onClick = {
                                                                vm.navigateTo(folder.path, folder.name)
                                                        },
                                                )
                                        }
                                }

                                if (subFolders.isNotEmpty() && songsInFolder.isNotEmpty()) {
                                        item {
                                                Hairline(
                                                        modifier = Modifier.padding(horizontal = 24.dp)
                                                )
                                        }
                                }

                                items(songsInFolder, key = { it.id }) { song ->
                                        Hairline(modifier = Modifier.padding(horizontal = 24.dp))
                                        SongRowInFolder(song = song, onClick = { vm.playSong(song) })
                                }

                                if (subFolders.isEmpty() && songsInFolder.isEmpty()) {
                                        item {
                                                Column(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(
                                                                                horizontal = 24.dp,
                                                                                vertical = 60.dp,
                                                                        ),
                                                        horizontalAlignment =
                                                                Alignment.CenterHorizontally,
                                                ) {
                                                        Icon(
                                                                HugeIcons.FolderOff,
                                                                null,
                                                                modifier = Modifier.size(48.dp),
                                                                tint = colors.textMute,
                                                        )
                                                        Spacer(Modifier.height(12.dp))
                                                        Text(
                                                                "No music found",
                                                                style = text.editorialHeadline32,
                                                                color = colors.textDim,
                                                        )
                                                        Text(
                                                                "Scan your library first",
                                                                style = text.metaSmall115,
                                                                color = colors.textMute,
                                                        )
                                                }
                                        }
                                }
                        }
                }
        }

        if (showPlaylistSheet) {
                ModalBottomSheet(onDismissRequest = { showPlaylistSheet = false }) {
                        Column(
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                                Text(
                                        "Add folder to playlist",
                                        style = text.editorialHeadline32,
                                        color = colors.text,
                                        modifier = Modifier.padding(bottom = 16.dp),
                                )
                                ListItem(
                                        headlineContent = {
                                                Text("New playlist", style = text.trackTitle22)
                                        },
                                        leadingContent = { Icon(HugeIcons.Add, "Add") },
                                        modifier =
                                                Modifier.clickable {
                                                        showPlaylistSheet = false
                                                        showNewPlaylistDialog = true
                                                },
                                )
                                Hairline(modifier = Modifier.padding(vertical = 8.dp))
                                LazyColumn {
                                        items(playlists, key = { it.id }) { playlist ->
                                                ListItem(
                                                        headlineContent = {
                                                                Text(
                                                                        playlist.name,
                                                                        style = text.trackTitle22,
                                                                )
                                                        },
                                                        leadingContent = {
                                                                Icon(
                                                                        HugeIcons.PlaylistPlay,
                                                                        null,
                                                                )
                                                        },
                                                        modifier =
                                                                Modifier.clickable {
                                                                        vm.addFolderToPlaylist(
                                                                                playlist.id
                                                                        )
                                                                        showPlaylistSheet = false
                                                                },
                                                )
                                        }
                                }
                        }
                }
        }

        if (showNewPlaylistDialog) {
                AlertDialog(
                        onDismissRequest = { showNewPlaylistDialog = false },
                        title = { Text("New playlist", style = text.editorialHeadline32) },
                        text = {
                                OutlinedTextField(
                                        value = newPlaylistName,
                                        onValueChange = { newPlaylistName = it },
                                        label = { Text("Name") },
                                        singleLine = true,
                                )
                        },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                vm.createPlaylist(newPlaylistName)
                                                showNewPlaylistDialog = false
                                                newPlaylistName = ""
                                        }
                                ) { Text("Create") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showNewPlaylistDialog = false }) {
                                        Text("Cancel")
                                }
                        },
                )
        }
}

@Composable
private fun FolderRow(folder: FolderItem, onClick: () -> Unit) {
        val meta = buildString {
                if (folder.subFolderCount > 0) append("${folder.subFolderCount} folders")
                if (folder.subFolderCount > 0 && folder.totalSongCount > 0) append(" · ")
                if (folder.totalSongCount > 0) append("${folder.totalSongCount} songs")
        }
        BrowserFolderRow(name = folder.name, meta = meta, onClick = onClick)
}

@Composable
private fun SongRowInFolder(song: SongEntity, onClick: () -> Unit) {
        BrowserTrackRow(
                title = song.title,
                subtitle = song.artist,
                trailingMeta = formatDuration(song.duration),
                onClick = onClick,
        )
}
