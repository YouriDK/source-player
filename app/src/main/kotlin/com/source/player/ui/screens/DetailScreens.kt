package com.source.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.ui.viewmodel.AlbumDetailViewModel

// ---- Album Detail ----

@Composable
fun AlbumDetailScreen(
        navController: NavController,
        albumId: Long,
        vm: AlbumDetailViewModel = hiltViewModel(),
) {
  LaunchedEffect(albumId) { vm.load(albumId) }
  val album by vm.album.collectAsState()
  val songs by vm.songs.collectAsState()

  LazyColumn(Modifier.fillMaxSize().systemBarsPadding()) {
    item {
      Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        AsyncImage(
                model = album?.artUri,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
        )
        Box(
                Modifier.fillMaxSize()
                        .background(
                                Brush.verticalGradient(
                                        listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.background
                                        )
                                ),
                        )
        )
        IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White) }
      }
    }

    item {
      Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(album?.title ?: "—", style = MaterialTheme.typography.displayMedium)
        Text(
                album?.artist ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Button(onClick = { vm.playAll(songs) }, shape = MaterialTheme.shapes.large) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(4.dp))
            Text("Play")
          }
          OutlinedButton(onClick = { vm.shuffle(songs) }, shape = MaterialTheme.shapes.large) {
            Icon(Icons.Rounded.Shuffle, null)
            Spacer(Modifier.width(4.dp))
            Text("Shuffle")
          }
        }
        Spacer(Modifier.height(12.dp))
      }
    }

    items(songs, key = { it.id }) { song ->
      ListItem(
              headlineContent = {
                Text(
                        "${song.trackNumber}. ${song.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
              },
              supportingContent = {
                Text(
                        formatDuration(song.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              },
              modifier = Modifier.clickable { vm.playSongFromAlbum(song, songs) },
              colors =
                      ListItemDefaults.colors(
                              containerColor = MaterialTheme.colorScheme.background
                      ),
      )
    }
  }
}

// ---- Artist Detail ----
@Composable
fun ArtistDetailScreen(navController: NavController, artistId: Long) {
  Column(Modifier.fillMaxSize().systemBarsPadding()) {
    TopAppBar(
            title = { Text("Artist") },
            navigationIcon = {
              IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Rounded.ArrowBack, "Back")
              }
            },
            colors =
                    TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                    ),
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Artist detail for ID $artistId", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}


// ---- Playlist Detail ----
@Composable
fun PlaylistDetailScreen(navController: NavController, playlistId: Long) {
  Column(Modifier.fillMaxSize().systemBarsPadding()) {
    TopAppBar(
            title = { Text("Playlist") },
            navigationIcon = {
              IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Rounded.ArrowBack, "Back")
              }
            },
            colors =
                    TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                    ),
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Playlist detail for ID $playlistId", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

