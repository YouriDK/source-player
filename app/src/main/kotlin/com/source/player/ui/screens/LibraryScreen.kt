package com.source.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.data.db.entity.*
import com.source.player.ui.navigation.Routes
import com.source.player.ui.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
        navController: NavController,
        vm: LibraryViewModel = hiltViewModel(),
) {
        val songs by vm.songs.collectAsState()
        val albums by vm.albums.collectAsState()
        val artists by vm.artists.collectAsState()
        val playlists by vm.playlists.collectAsState()
        val genres by vm.genres.collectAsState()

        val tabs = listOf("Songs", "Albums", "Artists", "Playlists", "Genres")
        var selectedTab by remember { mutableIntStateOf(0) }
        var selectedSong by remember {
                mutableStateOf<com.source.player.data.db.entity.SongEntity?>(null)
        }

        // Song Options bottom sheet
        selectedSong?.let { song ->
                SongOptionsSheet(
                        song = song,
                        navController = navController,
                        onDismiss = { selectedSong = null },
                )
        }

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
                TopAppBar(
                        title = { Text("Library", style = MaterialTheme.typography.headlineLarge) },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.background
                                ),
                )

                ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.background,
                        indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                        modifier =
                                                Modifier.tabIndicatorOffset(
                                                        tabPositions[selectedTab]
                                                ),
                                        color = MaterialTheme.colorScheme.primary,
                                )
                        },
                        divider = {},
                ) {
                        tabs.forEachIndexed { i, title ->
                                Tab(
                                        selected = selectedTab == i,
                                        onClick = { selectedTab = i },
                                        text = {
                                                Text(
                                                        title,
                                                        color =
                                                                if (selectedTab == i)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                )
                                        },
                                )
                        }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                when (selectedTab) {
                        0 ->
                                Column(Modifier.fillMaxSize()) {
                                        if (songs.isNotEmpty()) {
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(
                                                                                horizontal = 16.dp,
                                                                                vertical = 12.dp
                                                                        ),
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp),
                                                ) {
                                                        Button(
                                                                onClick = { vm.playAllSongs() },
                                                                modifier = Modifier.weight(1f),
                                                                shape =
                                                                        MaterialTheme.shapes
                                                                                .extraLarge,
                                                        ) {
                                                                Icon(Icons.Rounded.PlayArrow, null)
                                                                Spacer(Modifier.width(6.dp))
                                                                Text("Play All")
                                                        }
                                                        FilledTonalButton(
                                                                onClick = { vm.shuffleAllSongs() },
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
                                                                Icon(Icons.Rounded.Shuffle, null)
                                                                Spacer(Modifier.width(6.dp))
                                                                Text("Shuffle")
                                                        }
                                                }
                                        }
                                        SongsList(
                                                songs = songs,
                                                onSongClick = { index ->
                                                        vm.playSongsFromIndex(songs, index)
                                                },
                                                onMoreClick = { song -> selectedSong = song },
                                        )
                                }
                        1 ->
                                AlbumsGrid(
                                        albums = albums,
                                        onAlbumClick = {
                                                navController.navigate(Routes.albumDetail(it.id))
                                        }
                                )
                        2 ->
                                ArtistsList(
                                        artists = artists,
                                        onArtistClick = {
                                                navController.navigate(Routes.artistDetail(it.id))
                                        }
                                )
                        3 ->
                                PlaylistsList(
                                        playlists = playlists,
                                        onPlaylistClick = {
                                                navController.navigate(Routes.playlistDetail(it.id))
                                        },
                                        onCreatePlaylist = { vm.createPlaylist(it) }
                                )
                        4 -> GenresList(genres = genres)
                }
        }
}

@Composable
fun SongsList(
        songs: List<SongEntity>,
        onSongClick: (Int) -> Unit,
        onMoreClick: ((SongEntity) -> Unit)? = null,
        modifier: Modifier = Modifier,
) {
        LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
        ) {
                itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                        ListItem(
                                headlineContent = {
                                        Text(
                                                song.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
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
                                                modifier =
                                                        Modifier.size(48.dp)
                                                                .clip(MaterialTheme.shapes.small),
                                        )
                                },
                                trailingContent = {
                                        IconButton(onClick = { onMoreClick?.invoke(song) }) {
                                                Icon(
                                                        Icons.Rounded.MoreVert,
                                                        "Options",
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }
                                },
                                modifier = Modifier.clickable { onSongClick(i) },
                                colors =
                                        ListItemDefaults.colors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.background
                                        ),
                        )
                }
        }
}

@Composable
fun AlbumsGrid(
        albums: List<AlbumEntity>,
        onAlbumClick: (AlbumEntity) -> Unit,
) {
        LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
        ) {
                items(albums.size) { i ->
                        val album = albums[i]
                        Card(
                                onClick = { onAlbumClick(album) },
                                shape = MaterialTheme.shapes.large,
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.surfaceVariant
                                        ),
                        ) {
                                Column {
                                        AsyncImage(
                                                model = album.artUri,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                        )
                                        Column(Modifier.padding(12.dp)) {
                                                Text(
                                                        album.title,
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                        album.artist,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun ArtistsList(
        artists: List<ArtistEntity>,
        onArtistClick: (ArtistEntity) -> Unit,
) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(artists, key = { _, a -> a.id }) { _, artist ->
                        ListItem(
                                headlineContent = { Text(artist.name) },
                                supportingContent = {
                                        Text(
                                                "${artist.albumCount} albums • ${artist.songCount} songs",
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
                                modifier = Modifier.clickable { onArtistClick(artist) },
                                colors =
                                        ListItemDefaults.colors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.background
                                        ),
                        )
                }
        }
}

@Composable
fun PlaylistsList(
        playlists: List<PlaylistEntity>,
        onPlaylistClick: (PlaylistEntity) -> Unit,
        onCreatePlaylist: (String) -> Unit,
) {
        var showDialog by remember { mutableStateOf(false) }
        var newName by remember { mutableStateOf("") }

        Column(Modifier.fillMaxSize()) {
                Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                "${playlists.size} Playlists",
                                style = MaterialTheme.typography.titleMedium
                        )
                        FilledTonalButton(onClick = { showDialog = true }) {
                                Icon(Icons.Rounded.Add, null)
                                Spacer(Modifier.width(4.dp))
                                Text("New")
                        }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                        itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                                ListItem(
                                        headlineContent = { Text(playlist.name) },
                                        leadingContent = {
                                                Icon(
                                                        Icons.Rounded.PlaylistPlay,
                                                        null,
                                                        modifier = Modifier.size(40.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                )
                                        },
                                        modifier = Modifier.clickable { onPlaylistClick(playlist) },
                                        colors =
                                                ListItemDefaults.colors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.background
                                                ),
                                )
                        }
                }
        }

        if (showDialog) {
                AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("New Playlist") },
                        text = {
                                OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        label = { Text("Name") },
                                        singleLine = true
                                )
                        },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                onCreatePlaylist(newName)
                                                showDialog = false
                                                newName = ""
                                        }
                                ) { Text("Create") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                        },
                )
        }
}

@Composable
fun GenresList(genres: List<GenreEntity>) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(genres, key = { _, g -> g.id }) { _, genre ->
                        ListItem(
                                headlineContent = { Text(genre.name) },
                                supportingContent = {
                                        Text(
                                                "${genre.songCount} songs",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                },
                                leadingContent = {
                                        Icon(
                                                Icons.Rounded.MusicNote,
                                                null,
                                                modifier = Modifier.size(36.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                },
                                colors =
                                        ListItemDefaults.colors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.background
                                        ),
                        )
                }
        }
}
