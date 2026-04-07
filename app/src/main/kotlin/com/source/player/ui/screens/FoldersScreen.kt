package com.source.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.source.player.data.db.entity.SongEntity
import com.source.player.ui.viewmodel.FoldersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
        navController: NavController,
        vm: FoldersViewModel = hiltViewModel(),
) {
        val breadcrumbs by vm.breadcrumbs.collectAsState()
        val subFolders by vm.subFolders.collectAsState()
        val songsInFolder by vm.songsInFolder.collectAsState()
        val totalSongs by vm.totalSongsCount.collectAsState()
        val currentPath by vm.currentPath.collectAsState()
        val playlists by vm.playlists.collectAsState()

        var showPlaylistSheet by remember { mutableStateOf(false) }
        var showNewPlaylistDialog by remember { mutableStateOf(false) }
        var newPlaylistName by remember { mutableStateOf("") }

        // Handle system back
        BackHandler(enabled = breadcrumbs.size > 1) { vm.popBack() }

        Scaffold(
                topBar = {
                        TopAppBar(
                                title = {
                                        Text("Folders", style = MaterialTheme.typography.titleLarge)
                                },
                                navigationIcon = {
                                        if (breadcrumbs.size > 1) {
                                                IconButton(onClick = { vm.popBack() }) {
                                                        Icon(
                                                                Icons.AutoMirrored.Rounded
                                                                        .ArrowBack,
                                                                "Back"
                                                        )
                                                }
                                        }
                                },
                                colors =
                                        TopAppBarDefaults.topAppBarColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.background
                                        ),
                        )
                }
        ) { padding ->
                LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                        // Breadcrumb trail (Stitch-styled chips)
                        item {
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(
                                                                horizontal = 16.dp,
                                                                vertical = 8.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                        breadcrumbs.forEachIndexed { index, (label, _) ->
                                                val isLast = index == breadcrumbs.lastIndex
                                                Surface(
                                                        onClick = {
                                                                if (!isLast)
                                                                        vm.navigateToBreadcrumb(
                                                                                index
                                                                        )
                                                        },
                                                        shape = MaterialTheme.shapes.small,
                                                        color =
                                                                if (isLast)
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant,
                                                        modifier = Modifier.height(32.dp),
                                                ) {
                                                        Row(
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 10.dp,
                                                                        ),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                horizontalArrangement =
                                                                        Arrangement.spacedBy(4.dp),
                                                        ) {
                                                                if (index == 0) {
                                                                        Icon(
                                                                                Icons.Rounded
                                                                                        .Folder,
                                                                                null,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                14.dp
                                                                                        ),
                                                                                tint =
                                                                                        if (isLast)
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .onPrimaryContainer
                                                                                        else
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .onSurfaceVariant
                                                                        )
                                                                }
                                                                Text(
                                                                        text = label,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelMedium,
                                                                        fontWeight =
                                                                                if (isLast)
                                                                                        FontWeight
                                                                                                .Bold
                                                                                else
                                                                                        FontWeight
                                                                                                .Normal,
                                                                        color =
                                                                                if (isLast)
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onPrimaryContainer
                                                                                else
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurfaceVariant,
                                                                        maxLines = 1,
                                                                )
                                                        }
                                                }
                                                if (!isLast) {
                                                        Icon(
                                                                Icons.Rounded.ChevronRight,
                                                                null,
                                                                modifier = Modifier.size(16.dp),
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                                                .copy(alpha = 0.6f),
                                                        )
                                                }
                                        }
                                }
                        }

                        // Folder header with Play All / Shuffle (only when inside a folder)
                        if (currentPath != null && totalSongs > 0) {
                                item {
                                        Column(
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 16.dp,
                                                                vertical = 8.dp
                                                        )
                                        ) {
                                                Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement =
                                                                Arrangement.SpaceBetween,
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Text(
                                                                "$totalSongs songs",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                        )
                                                        IconButton(
                                                                onClick = {
                                                                        showPlaylistSheet = true
                                                                }
                                                        ) {
                                                                Icon(
                                                                        Icons.Rounded.PlaylistAdd,
                                                                        "Add Folder to Playlist",
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant
                                                                )
                                                        }
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(top = 4.dp),
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        Button(
                                                                onClick = { vm.playAll() },
                                                                modifier = Modifier.weight(1f),
                                                                shape =
                                                                        MaterialTheme.shapes
                                                                                .extraLarge,
                                                        ) {
                                                                Icon(Icons.Rounded.PlayArrow, "Play")
                                                                Spacer(Modifier.width(6.dp))
                                                                Text("Play All")
                                                        }
                                                        FilledTonalButton(
                                                                onClick = { vm.shuffleAll() },
                                                                modifier = Modifier.weight(1f),
                                                                shape =
                                                                        MaterialTheme.shapes
                                                                                .extraLarge,
                                                                colors =
                                                                        ButtonDefaults
                                                                                .filledTonalButtonColors(
                                                                                        containerColor =
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .surfaceVariant,
                                                                                        contentColor =
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .onSurface,
                                                                                ),
                                                        ) {
                                                                Icon(Icons.Rounded.Shuffle, "Shuffle")
                                                                Spacer(Modifier.width(6.dp))
                                                                Text("Shuffle")
                                                        }
                                                }
                                        }
                                }
                        }

                        // Subfolders
                        if (subFolders.isNotEmpty()) {
                                item {
                                        if (currentPath != null) {
                                                HorizontalDivider(
                                                        modifier =
                                                                Modifier.padding(horizontal = 16.dp)
                                                )
                                        }
                                }
                                items(subFolders, key = { it.path }) { folder ->
                                        FolderRow(
                                                folder = folder,
                                                onClick = {
                                                        vm.navigateTo(folder.path, folder.name)
                                                }
                                        )
                                }
                        }

                        // Divider between folders and songs
                        if (subFolders.isNotEmpty() && songsInFolder.isNotEmpty()) {
                                item {
                                        HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                }
                        }

                        // Direct songs
                        items(songsInFolder, key = { it.id }) { song ->
                                SongRowInFolder(song = song, onClick = { vm.playSong(song) })
                        }

                        // Empty state
                        if (subFolders.isEmpty() && songsInFolder.isEmpty()) {
                                item {
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(top = 80.dp),
                                                contentAlignment = Alignment.Center,
                                        ) {
                                                Column(
                                                        horizontalAlignment =
                                                                Alignment.CenterHorizontally
                                                ) {
                                                        Icon(
                                                                Icons.Rounded.FolderOff,
                                                                null,
                                                                modifier = Modifier.size(64.dp),
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                        )
                                                        Spacer(Modifier.height(12.dp))
                                                        Text(
                                                                "No music found",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleMedium,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                        Text(
                                                                "Scan your library first",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
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
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                                Text(
                                        "Add Folder to Playlist",
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                )
                                ListItem(
                                        headlineContent = { Text("New Playlist") },
                                        leadingContent = { Icon(Icons.Rounded.Add, "Add") },
                                        modifier =
                                                Modifier.clickable {
                                                        showPlaylistSheet = false
                                                        showNewPlaylistDialog = true
                                                }
                                )
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                LazyColumn {
                                        items(playlists) { playlist ->
                                                ListItem(
                                                        headlineContent = { Text(playlist.name) },
                                                        leadingContent = {
                                                                Icon(
                                                                        Icons.Rounded.PlaylistPlay,
                                                                        null
                                                                )
                                                        },
                                                        modifier =
                                                                Modifier.clickable {
                                                                        vm.addFolderToPlaylist(
                                                                                playlist.id
                                                                        )
                                                                        showPlaylistSheet = false
                                                                }
                                                )
                                        }
                                }
                        }
                }
        }

        if (showNewPlaylistDialog) {
                AlertDialog(
                        onDismissRequest = { showNewPlaylistDialog = false },
                        title = { Text("New Playlist") },
                        text = {
                                OutlinedTextField(
                                        value = newPlaylistName,
                                        onValueChange = { newPlaylistName = it },
                                        label = { Text("Playlist Name") },
                                        singleLine = true
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
                        }
                )
        }
}

@Composable
private fun FolderRow(folder: FolderItem, onClick: () -> Unit) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable(onClick = onClick)
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
                Icon(
                        Icons.Rounded.Folder,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                folder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                        val meta = buildString {
                                if (folder.subFolderCount > 0)
                                        append("${folder.subFolderCount} folders")
                                if (folder.subFolderCount > 0 && folder.totalSongCount > 0)
                                        append(" • ")
                                if (folder.totalSongCount > 0)
                                        append("${folder.totalSongCount} songs")
                        }
                        if (meta.isNotEmpty()) {
                                Text(
                                        meta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                }
                Icon(
                        Icons.Rounded.ChevronRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
        }
}

@Composable
private fun SongRowInFolder(song: SongEntity, onClick: () -> Unit) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable(onClick = onClick)
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
                Icon(
                        Icons.Rounded.MusicNote,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                song.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                        Text(
                                song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                        )
                }
                Text(
                        formatDuration(song.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
        }
}
