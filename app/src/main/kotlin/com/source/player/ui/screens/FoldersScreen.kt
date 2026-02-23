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
                        // Breadcrumb trail
                        item {
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(
                                                                horizontal = 16.dp,
                                                                vertical = 4.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                ) {
                                        breadcrumbs.forEachIndexed { index, (label, _) ->
                                                val isLast = index == breadcrumbs.lastIndex
                                                Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight =
                                                                if (isLast) FontWeight.Bold
                                                                else FontWeight.Normal,
                                                        color =
                                                                if (isLast)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                        modifier =
                                                                if (!isLast)
                                                                        Modifier.clickable {
                                                                                vm.navigateToBreadcrumb(
                                                                                        index
                                                                                )
                                                                        }
                                                                else Modifier,
                                                )
                                                if (!isLast) {
                                                        Text(
                                                                " › ",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
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
                                                Text(
                                                        "$totalSongs songs",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Row(
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        Button(
                                                                onClick = { vm.playAll() },
                                                                shape = MaterialTheme.shapes.large,
                                                        ) {
                                                                Icon(Icons.Rounded.PlayArrow, null)
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Play All")
                                                        }
                                                        OutlinedButton(
                                                                onClick = { vm.shuffleAll() },
                                                                shape = MaterialTheme.shapes.large,
                                                        ) {
                                                                Icon(Icons.Rounded.Shuffle, null)
                                                                Spacer(Modifier.width(4.dp))
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
