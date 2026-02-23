package com.source.player.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.ui.navigation.Routes
import com.source.player.ui.viewmodel.HomeViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
        navController: NavController,
        vm: HomeViewModel = hiltViewModel(),
) {
        val context = LocalContext.current
        val currentSong by vm.currentSong.collectAsState()
        val songCount by vm.songCount.collectAsState()
        val scanProgress by vm.scanProgress.collectAsState()

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
                        Column(Modifier.padding(horizontal = 24.dp)) {
                                Text(
                                        greeting,
                                        style = MaterialTheme.typography.displayMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                        "Welcome to Source",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(24.dp))
                        }
                }

                // Continue Listening card
                currentSong?.let { song ->
                        item {
                                Column(Modifier.padding(horizontal = 24.dp)) {
                                        Text(
                                                "Continue Listening",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        ContinueListeningCard(
                                                song = song,
                                                onTap = { navController.navigate(Routes.PLAYER) }
                                        )
                                        Spacer(Modifier.height(24.dp))
                                }
                        }
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
                                                // Permission required — show explanatory UI then
                                                // request
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
                                                // Scanning in progress
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
                                                // Permission granted — show scan button
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
                }
        }
}

@Composable
private fun ContinueListeningCard(
        song: androidx.media3.common.MediaItem,
        onTap: () -> Unit,
) {
        Card(
                onClick = onTap,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
        ) {
                Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        AsyncImage(
                                model = song.mediaMetadata.artworkUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.medium),
                        )
                        Column(Modifier.weight(1f)) {
                                Text(
                                        song.mediaMetadata.title?.toString() ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                        song.mediaMetadata.artist?.toString() ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                        Icon(
                                Icons.Rounded.PlayCircle,
                                "Play",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )
                }
        }
}
