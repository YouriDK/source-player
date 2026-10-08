package com.source.player.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.source.player.data.db.entity.PlaylistEntity
import com.source.player.data.db.entity.SongEntity
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.navigation.Routes
import com.source.player.ui.viewmodel.SongOptionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
        song: SongEntity,
        navController: NavController,
        onDismiss: () -> Unit,
        vm: SongOptionsViewModel = hiltViewModel(),
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val addedToPlaylistId by vm.addedToPlaylistId.collectAsStateWithLifecycle()
    val toastMsg by vm.toastMessage.collectAsStateWithLifecycle()
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Show toast when song is added
    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearToast()
        }
    }

    LaunchedEffect(addedToPlaylistId) {
        if (addedToPlaylistId != null) {
            showPlaylistPicker = false
            vm.resetAddedState()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (!showPlaylistPicker) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                // Song header
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                            HugeIcons.MusicNote,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                song.title,
                                style = MaterialTheme.typography.titleMedium,
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
                }
                HorizontalDivider()

                OptionRow(icon = HugeIcons.Play, label = "Play Now") {
                    vm.playSong(song)
                    onDismiss()
                }
                OptionRow(icon = HugeIcons.Next, label = "Play Next") {
                    vm.playNext(song)
                    onDismiss()
                }
                OptionRow(icon = HugeIcons.Queue, label = "Add to Queue") {
                    vm.addToQueue(song)
                    onDismiss()
                }
                OptionRow(
                        icon = HugeIcons.PlaylistAdd,
                        label = "Add to Playlist"
                ) { showPlaylistPicker = true }
                OptionRow(icon = HugeIcons.Edit, label = "Edit Tags") {
                    navController.navigate(Routes.tagEditor(song.id))
                    onDismiss()
                }
                OptionRow(icon = HugeIcons.Album, label = "Go to Album") {
                    navController.navigate(Routes.albumDetail(song.albumId))
                    onDismiss()
                }
                OptionRow(icon = HugeIcons.Person, label = "Go to Artist") {
                    navController.navigate(Routes.artistDetail(song.artistId))
                    onDismiss()
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showPlaylistPicker = false }) {
                        Icon(HugeIcons.ArrowLeft, "Back")
                    }
                    Text(
                            "Add to Playlist",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider()
                if (playlists.isEmpty()) {
                    Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                    HugeIcons.PlaylistAdd,
                                    null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                    "No playlists yet",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn {
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistPickerRow(
                                    playlist = playlist,
                                    onClick = { vm.addSongToPlaylist(song.id, playlist.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
        )
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlaylistPickerRow(playlist: PlaylistEntity, onClick: () -> Unit) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
                HugeIcons.Playlist,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
        )
        Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
        )
        Icon(HugeIcons.Add, "Add to playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
