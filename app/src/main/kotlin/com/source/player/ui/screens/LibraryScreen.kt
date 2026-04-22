package com.source.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.data.db.entity.*
import com.source.player.ui.components.EditorialDivider
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.components.PillTab
import com.source.player.ui.components.SectionKicker
import com.source.player.ui.components.VerticalHairline
import com.source.player.ui.navigation.Routes
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

private enum class LibraryTab { Songs, Albums, Artists, Playlists, Genres, Folders }

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

    var selectedTab by remember { mutableStateOf(LibraryTab.Artists) }
    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }

    selectedSong?.let { song ->
        SongOptionsSheet(
                song = song,
                navController = navController,
                onDismiss = { selectedSong = null },
        )
    }

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    Column(Modifier.fillMaxSize().systemBarsPadding().background(colors.bg)) {
        // Header --------------------------------------------------------
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Library", style = text.editorialHeadline32, color = colors.text)
            MonoText(text = "A-Z", color = colors.textMute, size = MonoSize.S10)
        }

        // Tabs ----------------------------------------------------------
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LibraryTab.entries.forEach { tab ->
                PillTab(
                        label = tab.name,
                        selected = tab == selectedTab,
                        onClick = {
                            if (tab == LibraryTab.Folders) {
                                navController.navigate(Routes.FOLDERS)
                            } else {
                                selectedTab = tab
                            }
                        },
                )
            }
        }
        Hairline()

        // Body ----------------------------------------------------------
        when (selectedTab) {
            LibraryTab.Songs ->
                    VinylSongsList(
                            songs = songs,
                            onPlayAll = { vm.playAllSongs() },
                            onShuffleAll = { vm.shuffleAllSongs() },
                            onSongClick = { idx -> vm.playSongsFromIndex(songs, idx) },
                            onMore = { selectedSong = it },
                    )
            LibraryTab.Albums ->
                    VinylAlbumsGrid(
                            albums = albums,
                            onClick = { navController.navigate(Routes.albumDetail(it.id)) },
                    )
            LibraryTab.Artists ->
                    VinylArtistsList(
                            artists = artists,
                            onClick = { navController.navigate(Routes.artistDetail(it.id)) },
                    )
            LibraryTab.Playlists ->
                    VinylPlaylistsList(
                            playlists = playlists,
                            onCreate = { vm.createPlaylist(it) },
                            onClick = {
                                navController.navigate(Routes.playlistDetail(it.id))
                            },
                    )
            LibraryTab.Genres -> VinylGenresList(genres)
            LibraryTab.Folders -> Unit // Folders opens its own route
        }
    }
}

// ── Songs: play-all / shuffle-all + 22px serif rows ───────────────────────
@Composable
private fun VinylSongsList(
        songs: List<SongEntity>,
        onPlayAll: () -> Unit,
        onShuffleAll: () -> Unit,
        onSongClick: (Int) -> Unit,
        onMore: (SongEntity) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    Column(Modifier.fillMaxSize()) {
        if (songs.isNotEmpty()) {
            Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                        onClick = onPlayAll,
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.weight(1f),
                ) { Text("Play all", style = text.pillLabel12) }
                FilledTonalButton(
                        onClick = onShuffleAll,
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.weight(1f),
                ) { Text("Shuffle", style = text.pillLabel12) }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                Hairline(modifier = Modifier.padding(horizontal = 24.dp))
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clickable { onSongClick(i) }
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                    Modifier.size(48.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(colors.surface2),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = song.title,
                                style = text.trackTitle22,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                                text = song.artist,
                                style = text.metaSmall115,
                                color = colors.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    MonoText(
                            text = formatDuration(song.duration),
                            color = colors.textMute,
                            size = MonoSize.S10,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                            text = "\u2026",
                            style = text.trackTitle22,
                            color = colors.textMute,
                            modifier = Modifier.clickable { onMore(song) }.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

// ── Albums grid: 2-col, 2px radius, italic 18 title ───────────────────────
@Composable
private fun VinylAlbumsGrid(
        albums: List<AlbumEntity>,
        onClick: (AlbumEntity) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
    ) {
        items(albums.size) { i ->
            val album = albums[i]
            Column(modifier = Modifier.clickable { onClick(album) }) {
                AsyncImage(
                        model = album.artUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                                Modifier.fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(colors.surface2),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                        text = album.title,
                        style = text.rotationTitle18,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                )
                Text(
                        text = album.artist,
                        style = text.metaSmall115,
                        color = colors.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Artists: 64px accent italic jump letters + A-Z rail ───────────────────
@Composable
private fun VinylArtistsList(
        artists: List<ArtistEntity>,
        onClick: (ArtistEntity) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    // Build letter → first-index map for jumping.
    val letterIndex =
            remember(artists) {
                val map = LinkedHashMap<Char, Int>()
                artists.forEachIndexed { i, a ->
                    val c = a.name.firstOrNull()?.uppercaseChar() ?: '#'
                    if (c !in map) map[c] = i
                }
                map
            }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), state = listState) {
            itemsIndexed(artists, key = { _, a -> a.id }) { i, artist ->
                val prev = artists.getOrNull(i - 1)
                val isNewLetter =
                        i == 0 ||
                                (prev?.name?.firstOrNull()?.uppercaseChar() !=
                                        artist.name.firstOrNull()?.uppercaseChar())
                if (isNewLetter) {
                    val letter = artist.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                    Text(
                            text = letter,
                            style = text.libraryLetter64.copy(fontStyle = FontStyle.Italic),
                            color = colors.accent,
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(start = 24.dp, top = 14.dp, bottom = 2.dp),
                    )
                }
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clickable { onClick(artist) }
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = artist.name,
                                style = text.trackTitle22,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                                text = "${artist.albumCount} albums \u00B7 ${artist.songCount} songs",
                                style = text.metaSmall115,
                                color = colors.textDim,
                        )
                    }
                    Text(
                            text = "\u2192",
                            style = text.trackTitle22,
                            color = colors.textMute,
                    )
                }
                Hairline(modifier = Modifier.padding(horizontal = 24.dp))
            }
        }

        // A-Z rail
        VerticalHairline()
        Column(
                modifier =
                        Modifier.width(28.dp)
                                .fillMaxHeight()
                                .padding(top = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Compute active letter from the first visible item's letter.
            val activeLetter =
                    artists.getOrNull(listState.firstVisibleItemIndex)
                            ?.name
                            ?.firstOrNull()
                            ?.uppercaseChar()
            letterIndex.keys.forEach { letter ->
                val active = letter == activeLetter
                Text(
                        text = letter.toString(),
                        style = text.kickerMono10,
                        color = if (active) colors.accent else colors.textMute,
                        modifier =
                                Modifier.clickable {
                                    letterIndex[letter]?.let { idx ->
                                        scope.launch { listState.scrollToItem(idx) }
                                    }
                                },
                )
            }
        }
    }
}

// ── Playlists ─────────────────────────────────────────────────────────────
@Composable
private fun VinylPlaylistsList(
        playlists: List<PlaylistEntity>,
        onClick: (PlaylistEntity) -> Unit,
        onCreate: (String) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
                modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionKicker(
                    label = "${playlists.size} Playlists",
                    color = colors.textMute,
            )
            FilledTonalButton(onClick = { showDialog = true }, shape = RoundedCornerShape(100.dp)) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("New", style = text.pillLabel12)
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                Hairline(modifier = Modifier.padding(horizontal = 24.dp))
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clickable { onClick(playlist) }
                                        .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                            text = playlist.name,
                            style = text.trackTitle22,
                            color = colors.text,
                            modifier = Modifier.weight(1f),
                    )
                    Text(
                            text = "\u2192",
                            style = text.trackTitle22,
                            color = colors.textMute,
                    )
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("New playlist", style = text.editorialHeadline32) },
                text = {
                    OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Name") },
                            singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                onCreate(newName)
                                newName = ""
                                showDialog = false
                            },
                    ) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        )
    }
}

// ── Genres ────────────────────────────────────────────────────────────────
@Composable
private fun VinylGenresList(genres: List<GenreEntity>) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(genres, key = { _, g -> g.id }) { _, genre ->
            Hairline(modifier = Modifier.padding(horizontal = 24.dp))
            Row(
                    modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                        text = genre.name,
                        style = text.trackTitle22,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                )
                MonoText(
                        text = "${genre.songCount} songs",
                        color = colors.textMute,
                        size = MonoSize.S10,
                )
            }
        }
    }
}

// Legacy exported name kept so other files (if any) don't break while the
// codebase is migrated off of it. Unused callers will be tree-shaken.
@Composable
fun SongsList(
        songs: List<SongEntity>,
        onSongClick: (Int) -> Unit,
        onMoreClick: ((SongEntity) -> Unit)? = null,
        modifier: Modifier = Modifier,
) {
    VinylSongsList(
            songs = songs,
            onPlayAll = {},
            onShuffleAll = {},
            onSongClick = onSongClick,
            onMore = { onMoreClick?.invoke(it) },
    )
}
