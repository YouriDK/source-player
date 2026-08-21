package com.source.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.source.player.data.db.entity.SongEntity
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.FoldersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
        navController: NavController,
        vm: FoldersViewModel = hiltViewModel(),
) {
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
private fun BreadcrumbChip(
        label: String,
        selected: Boolean,
        leading: androidx.compose.ui.graphics.vector.ImageVector?,
        onClick: () -> Unit,
) {
        val colors = MaterialTheme.sourceColors
        val text = MaterialTheme.sourceText
        val bg = if (selected) colors.text else Color.Transparent
        val fg = if (selected) colors.bg else colors.textDim
        Row(
                modifier =
                        Modifier.clip(CircleShape)
                                .background(bg)
                                .clickable(onClick = onClick)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
                if (leading != null) {
                        Icon(leading, null, modifier = Modifier.size(12.dp), tint = fg)
                }
                Text(text = label, style = text.pillLabel12, color = fg, maxLines = 1)
        }
}

@Composable
private fun FolderRow(folder: FolderItem, onClick: () -> Unit) {
        val colors = MaterialTheme.sourceColors
        val text = MaterialTheme.sourceText
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable(onClick = onClick)
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
                Icon(
                        HugeIcons.Folder,
                        null,
                        tint = colors.accent,
                        modifier = Modifier.size(32.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                folder.name,
                                style = text.folderName17,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                        val meta = buildString {
                                if (folder.subFolderCount > 0)
                                        append("${folder.subFolderCount} folders")
                                if (folder.subFolderCount > 0 && folder.totalSongCount > 0)
                                        append(" · ")
                                if (folder.totalSongCount > 0)
                                        append("${folder.totalSongCount} songs")
                        }
                        if (meta.isNotEmpty()) {
                                Text(
                                        meta,
                                        style = text.metaSmall115,
                                        color = colors.textDim,
                                )
                        }
                }
                Text(
                        text = "→",
                        style = text.folderName17,
                        color = colors.textMute,
                )
        }
}

@Composable
private fun SongRowInFolder(song: SongEntity, onClick: () -> Unit) {
        val colors = MaterialTheme.sourceColors
        val text = MaterialTheme.sourceText
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable(onClick = onClick)
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
                Icon(
                        HugeIcons.MusicNote,
                        null,
                        tint = colors.textMute,
                        modifier = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                song.title,
                                style = text.trackTitle22,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                                song.artist,
                                style = text.metaSmall115,
                                color = colors.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                }
                MonoText(
                        text = formatDuration(song.duration),
                        color = colors.textMute,
                        size = MonoSize.S10,
                )
        }
}
