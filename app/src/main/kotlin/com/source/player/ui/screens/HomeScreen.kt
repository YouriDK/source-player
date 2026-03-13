package com.source.player.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.data.db.entity.PlaylistEntity
import com.source.player.data.db.entity.SongEntity
import com.source.player.ui.navigation.Routes
import com.source.player.ui.viewmodel.HomeViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
        navController: NavController,
        vm: HomeViewModel = hiltViewModel(),
) {
        val context = LocalContext.current
        val songCount by vm.songCount.collectAsState()
        val scanProgress by vm.scanProgress.collectAsState()

        val playlists by vm.playlists.collectAsState()
        val recentlyAdded by vm.recentlyAdded.collectAsState()
        val quickPicks by vm.quickPicks.collectAsState()
        val continueListening by vm.continueListening.collectAsState()

        // ---- Permission management ----
        val permission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }

        var permissionGranted by remember {
                mutableStateOf(
                        ContextCompat.checkSelfPermission(context, permission) ==
                                PackageManager.PERMISSION_GRANTED
                )
        }

        val permissionLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                        permissionGranted = granted
                        if (granted) vm.scanLibrary()
                }

        // ---- Greeting ----
        val greeting = remember {
                when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                        in 5..11 -> "Good morning"
                        in 12..17 -> "Good afternoon"
                        else -> "Good evening"
                }
        }

        LazyColumn(
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
                contentPadding = PaddingValues(vertical = 24.dp),
        ) {
                // Greeting header
                item {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Column {
                                        Text(
                                                greeting,
                                                style =
                                                        MaterialTheme.typography.headlineMedium
                                                                .copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                                "Here's what's been playing",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        IconButton(
                                                onClick = { /* TODO: Notifications */},
                                                modifier =
                                                        Modifier.background(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant,
                                                                        CircleShape
                                                                )
                                                                .size(40.dp)
                                        ) {
                                                Icon(
                                                        Icons.Rounded.Notifications,
                                                        contentDescription = "Notifications",
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }
                                        IconButton(
                                                onClick = { /* TODO: Profile */},
                                                modifier =
                                                        Modifier.background(
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                        CircleShape
                                                                )
                                                                .size(40.dp)
                                        ) {
                                                Icon(
                                                        Icons.Rounded.Person,
                                                        contentDescription = "Profile",
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                        }
                                }
                        }
                        Spacer(Modifier.height(32.dp))
                }

                // Empty state / Scan CTA
                if (songCount == 0) {
                        item {
                                Column(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                        Icon(
                                                Icons.Rounded.LibraryMusic,
                                                null,
                                                modifier = Modifier.size(64.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                                "Your library is empty",
                                                style = MaterialTheme.typography.titleMedium,
                                                textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                                "Scan your device to find music",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(20.dp))

                                        if (!permissionGranted) {
                                                OutlinedCard(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = MaterialTheme.shapes.large
                                                ) {
                                                        Column(
                                                                Modifier.padding(16.dp),
                                                                horizontalAlignment =
                                                                        Alignment.CenterHorizontally
                                                        ) {
                                                                Icon(
                                                                        Icons.Rounded.FolderOpen,
                                                                        null,
                                                                        modifier =
                                                                                Modifier.size(
                                                                                        40.dp
                                                                                ),
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                )
                                                                Spacer(Modifier.height(8.dp))
                                                                Text(
                                                                        "Storage access required",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleSmall,
                                                                        textAlign = TextAlign.Center
                                                                )
                                                                Spacer(Modifier.height(4.dp))
                                                                Text(
                                                                        "Source needs permission to read your audio files.",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodySmall,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant,
                                                                        textAlign = TextAlign.Center
                                                                )
                                                                Spacer(Modifier.height(12.dp))
                                                                Button(
                                                                        onClick = {
                                                                                permissionLauncher
                                                                                        .launch(
                                                                                                permission
                                                                                        )
                                                                        },
                                                                        shape =
                                                                                MaterialTheme.shapes
                                                                                        .large
                                                                ) {
                                                                        Icon(
                                                                                Icons.Rounded.Lock,
                                                                                null
                                                                        )
                                                                        Spacer(Modifier.width(8.dp))
                                                                        Text("Grant Permission")
                                                                }
                                                        }
                                                }
                                        } else if (scanProgress != null) {
                                                Column(
                                                        horizontalAlignment =
                                                                Alignment.CenterHorizontally
                                                ) {
                                                        CircularProgressIndicator(
                                                                modifier = Modifier.size(40.dp)
                                                        )
                                                        Spacer(Modifier.height(12.dp))
                                                        Text(
                                                                scanProgress ?: "Scanning…",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                        } else {
                                                Button(
                                                        onClick = { vm.scanLibrary() },
                                                        shape = MaterialTheme.shapes.large,
                                                ) {
                                                        Icon(Icons.Rounded.Search, null)
                                                        Spacer(Modifier.width(8.dp))
                                                        Text("Scan Library")
                                                }
                                        }
                                }
                        }
                } else {
                        // Continue Listening
                        if (continueListening.isNotEmpty()) {
                                item {
                                        SectionHeader(title = "Continue Listening")
                                        LazyRow(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                modifier = Modifier.fillMaxWidth()
                                        ) {
                                                items(continueListening) { song ->
                                                        LargeSquareCard(
                                                                title = song.title,
                                                                subtitle = song.artist,
                                                                artworkUri = song.albumArtUri,
                                                                onClick = {
                                                                        vm.playSong(song)
                                                                        navController.navigate(
                                                                                Routes.PLAYER
                                                                        )
                                                                }
                                                        )
                                                }
                                        }
                                        Spacer(Modifier.height(32.dp))
                                }
                        }

                        // Quick Picks
                        if (quickPicks.isNotEmpty()) {
                                item {
                                        SectionHeader(
                                                title = "Quick Picks",
                                                actionText = "Play all",
                                                onActionClick = {
                                                        vm.playSongs(quickPicks)
                                                        navController.navigate(Routes.PLAYER)
                                                }
                                        )
                                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                                quickPicks.forEach { song ->
                                                        QuickPickItem(
                                                                song = song,
                                                                onClick = {
                                                                        vm.playSong(song)
                                                                        navController.navigate(
                                                                                Routes.PLAYER
                                                                        )
                                                                },
                                                                onOptionsClick = { /* TODO: Implement options */
                                                                }
                                                        )
                                                }
                                        }
                                        Spacer(Modifier.height(32.dp))
                                }
                        }

                        // Your Playlists
                        item {
                                SectionHeader(
                                        title = "Your Playlists",
                                        actionText = "See all",
                                        onActionClick = { /* TODO: Navigate to all playlists */}
                                )
                                LazyRow(
                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                        item {
                                                NewPlaylistCard(
                                                        onClick = {
                                                                vm.createPlaylist("New Playlist")
                                                        }
                                                )
                                        }
                                        items(playlists) { playlist ->
                                                PlaylistCard(
                                                        playlist = playlist,
                                                        onClick = {
                                                                navController.navigate(
                                                                        Routes.PLAYLIST_DETAIL
                                                                                .replace(
                                                                                        "{playlistId}",
                                                                                        playlist.id
                                                                                                .toString()
                                                                                )
                                                                )
                                                        }
                                                )
                                        }
                                }
                                Spacer(Modifier.height(32.dp))
                        }

                        // Recently Added
                        if (recentlyAdded.isNotEmpty()) {
                                item {
                                        SectionHeader(title = "Recently Added")
                                        LazyRow(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                modifier = Modifier.fillMaxWidth()
                                        ) {
                                                items(recentlyAdded) { song ->
                                                        LargeSquareCard(
                                                                title = song.album,
                                                                subtitle = song.artist,
                                                                artworkUri = song.albumArtUri,
                                                                onClick = {
                                                                        vm.playSong(song)
                                                                        navController.navigate(
                                                                                Routes.PLAYER
                                                                        )
                                                                }
                                                        )
                                                }
                                        }
                                        Spacer(Modifier.height(32.dp))
                                }
                        }
                }
        }
}

@Composable
fun SectionHeader(title: String, actionText: String? = null, onActionClick: (() -> Unit)? = null) {
        Row(
                modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                        text = title,
                        style =
                                MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        color = MaterialTheme.colorScheme.onSurface
                )
                if (actionText != null && onActionClick != null) {
                        Text(
                                text = actionText,
                                style =
                                        MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.SemiBold
                                        ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onActionClick() }.padding(4.dp)
                        )
                }
        }
}

@Composable
fun LargeSquareCard(title: String, subtitle: String, artworkUri: String?, onClick: () -> Unit) {
        Column(modifier = Modifier.width(140.dp).clickable { onClick() }) {
                AsyncImage(
                        model = artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                                Modifier.size(140.dp)
                                        .clip(MaterialTheme.shapes.large)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                )
                Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                )
        }
}

@Composable
fun QuickPickItem(song: SongEntity, onClick: () -> Unit, onOptionsClick: () -> Unit) {
        Row(
                Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                                Modifier.size(48.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(Modifier.weight(1f)) {
                        Text(
                                song.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                        )
                        val durationMins = song.duration / 1000 / 60
                        val durationSecs = (song.duration / 1000) % 60
                        val durationStr = String.format("%d:%02d", durationMins, durationSecs)
                        Text(
                                "${song.artist} • $durationStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                        )
                }
                IconButton(onClick = onOptionsClick) {
                        Icon(
                                Icons.Rounded.MoreVert,
                                "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
        }
}

@Composable
fun PlaylistCard(playlist: PlaylistEntity, onClick: () -> Unit) {
        Column(modifier = Modifier.width(140.dp).clickable { onClick() }) {
                Box(
                        modifier =
                                Modifier.size(140.dp)
                                        .clip(MaterialTheme.shapes.large)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                ) {
                        Icon(
                                Icons.Rounded.LibraryMusic,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                        playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                )
        }
}

@Composable
fun NewPlaylistCard(onClick: () -> Unit) {
        Column(modifier = Modifier.width(140.dp).clickable { onClick() }) {
                Box(
                        modifier =
                                Modifier.size(140.dp)
                                        .clip(MaterialTheme.shapes.large)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                ) {
                        Icon(
                                Icons.Rounded.Add,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                        "New Playlist",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                )
        }
}
