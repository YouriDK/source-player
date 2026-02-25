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
import androidx.compose.ui.draw.clip
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(navController: NavController, playlistId: Long) {
  val vm: com.source.player.ui.viewmodel.PlaylistDetailViewModel = hiltViewModel()
  val playlist by vm.playlist.collectAsState()
  val songs by vm.songs.collectAsState()
  val filteredSongs by vm.filteredSongs.collectAsState()
  val searchQuery by vm.searchQuery.collectAsState()
  val toastMsg by vm.toastMessage.collectAsState()
  var showAddSheet by remember { mutableStateOf(false) }
  val context = androidx.compose.ui.platform.LocalContext.current

  LaunchedEffect(toastMsg) {
    toastMsg?.let {
      android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
      vm.clearToast()
    }
  }

  Scaffold(
          topBar = {
            TopAppBar(
                    title = {
                      Column {
                        Text(
                                playlist?.name ?: "Playlist",
                                style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                                "${songs.size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    },
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
          },
          floatingActionButton = {
            ExtendedFloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
            ) {
              Icon(Icons.Rounded.Add, null)
              Spacer(Modifier.width(8.dp))
              Text("Add Songs")
            }
          },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      // Play / Shuffle buttons
      if (songs.isNotEmpty()) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
                  onClick = { vm.playSongs() },
                  shape = MaterialTheme.shapes.large,
          ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(4.dp))
            Text("Play All")
          }
          OutlinedButton(
                  onClick = { vm.shuffleSongs() },
                  shape = MaterialTheme.shapes.large,
          ) {
            Icon(Icons.Rounded.Shuffle, null)
            Spacer(Modifier.width(4.dp))
            Text("Shuffle")
          }
        }
      }

      if (songs.isEmpty()) {
        Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                    Icons.Rounded.PlaylistPlay,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                    "No songs yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                    "Tap + to add songs to this playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = { showAddSheet = true }) {
              Icon(Icons.Rounded.Add, null)
              Spacer(Modifier.width(4.dp))
              Text("Add Songs")
            }
          }
        }
      } else {
        LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
        ) {
          items(songs, key = { it.id }) { song ->
            ListItem(
                    headlineContent = {
                      Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                      Text(
                              "${song.artist} • ${formatDuration(song.duration)}",
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    },
                    leadingContent = {
                      AsyncImage(
                              model = song.albumArtUri,
                              contentDescription = null,
                              contentScale = ContentScale.Crop,
                              modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
                      )
                    },
                    trailingContent = {
                      IconButton(onClick = { vm.removeSongFromPlaylist(song.id) }) {
                        Icon(
                                Icons.Rounded.RemoveCircleOutline,
                                "Remove",
                                tint = MaterialTheme.colorScheme.error,
                        )
                      }
                    },
                    modifier = Modifier.clickable { vm.playSongs(songs.indexOf(song)) },
                    colors =
                            ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.background
                            ),
            )
          }
        }
      }
    }
  }

  // Add Songs Bottom Sheet
  if (showAddSheet) {
    ModalBottomSheet(
            onDismissRequest = {
              showAddSheet = false
              vm.setSearchQuery("")
            },
    ) {
      Column(
              modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
      ) {
        Text(
                "Add Songs",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                placeholder = { Text("Search songs...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()

        if (filteredSongs.isEmpty()) {
          Box(
                  modifier = Modifier.fillMaxWidth().padding(48.dp),
                  contentAlignment = Alignment.Center,
          ) {
            Text(
                    if (searchQuery.isNotBlank()) "No matching songs"
                    else "All songs already added",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
                  modifier = Modifier.heightIn(max = 400.dp),
          ) {
            items(filteredSongs.take(50), key = { it.id }) { song ->
              ListItem(
                      headlineContent = {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      },
                      supportingContent = {
                        Text(
                                song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      },
                      trailingContent = {
                        IconButton(onClick = { vm.addSongToPlaylist(song.id) }) {
                          Icon(
                                  Icons.Rounded.AddCircleOutline,
                                  "Add",
                                  tint = MaterialTheme.colorScheme.primary,
                          )
                        }
                      },
                      modifier = Modifier.clickable { vm.addSongToPlaylist(song.id) },
                      colors =
                              ListItemDefaults.colors(
                                      containerColor = MaterialTheme.colorScheme.surface
                              ),
              )
            }
          }
        }
      }
    }
  }
}
