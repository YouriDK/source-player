package com.source.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.data.db.entity.*
import com.source.player.ui.navigation.Routes
import com.source.player.ui.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
        navController: NavController,
        vm: SearchViewModel = hiltViewModel(),
) {
  val query by vm.query.collectAsState()
  val songResults by vm.songResults.collectAsState()
  val albumResults by vm.albumResults.collectAsState()
  val artistResults by vm.artistResults.collectAsState()

  Column(Modifier.fillMaxSize().systemBarsPadding()) {
    OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("Songs, albums, artists…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
              if (query.isNotEmpty()) {
                IconButton(onClick = { vm.onQueryChange("") }) {
                  Icon(Icons.Rounded.Close, "Clear")
                }
              }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                    OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
    )

    if (query.isBlank()) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
                  Icons.Rounded.Search,
                  null,
                  modifier = Modifier.size(64.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(Modifier.height(12.dp))
          Text(
                  "Search your library",
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        if (songResults.isNotEmpty()) {
          item { SearchSectionHeader("Songs", songResults.size) }
          items(songResults, key = { it.id }) { song ->
            SongResultItem(song, onClick = { vm.playSong(song, songResults) })
          }
        }
        if (albumResults.isNotEmpty()) {
          item { SearchSectionHeader("Albums", albumResults.size) }
          items(albumResults, key = { it.id }) { album ->
            AlbumResultItem(
                    album,
                    onClick = { navController.navigate(Routes.albumDetail(album.id)) }
            )
          }
        }
        if (artistResults.isNotEmpty()) {
          item { SearchSectionHeader("Artists", artistResults.size) }
          items(artistResults, key = { it.id }) { artist ->
            ArtistResultItem(
                    artist,
                    onClick = { navController.navigate(Routes.artistDetail(artist.id)) }
            )
          }
        }
        if (songResults.isEmpty() && albumResults.isEmpty() && artistResults.isEmpty()) {
          item {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
              Text("No results for \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SearchSectionHeader(title: String, count: Int) {
  Row(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
  ) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.width(8.dp))
    Text(
            "($count)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun SongResultItem(song: SongEntity, onClick: () -> Unit) {
  ListItem(
          headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          supportingContent = {
            Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          },
          leadingContent = {
            AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small),
            )
          },
          modifier = Modifier.clickable(onClick = onClick),
          colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
  )
}

@Composable
private fun AlbumResultItem(album: AlbumEntity, onClick: () -> Unit) {
  ListItem(
          headlineContent = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          supportingContent = {
            Text(
                    album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          },
          leadingContent = {
            Icon(
                    Icons.Rounded.Album,
                    null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
            )
          },
          trailingContent = {
            Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          },
          modifier = Modifier.clickable(onClick = onClick),
          colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
  )
}

@Composable
private fun ArtistResultItem(artist: ArtistEntity, onClick: () -> Unit) {
  ListItem(
          headlineContent = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          supportingContent = {
            Text(
                    "${artist.songCount} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          },
          leadingContent = {
            Icon(
                    Icons.Rounded.Person,
                    null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
            )
          },
          trailingContent = {
            Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          },
          modifier = Modifier.clickable(onClick = onClick),
          colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
  )
}
