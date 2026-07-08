package com.source.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.data.db.entity.AlbumEntity
import com.source.player.data.db.entity.SongEntity
import com.source.player.ui.components.EditorialDivider
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.components.SectionKicker
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.AlbumDetailViewModel
import com.source.player.ui.viewmodel.ArtistDetailViewModel
import com.source.player.ui.viewmodel.PlaylistDetailViewModel

// ── Album Detail ──────────────────────────────────────────────────────────
@Composable
fun AlbumDetailScreen(
        navController: NavController,
        albumId: Long,
        vm: AlbumDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(albumId) { vm.load(albumId) }
    val album by vm.album.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    LazyColumn(Modifier.fillMaxSize().systemBarsPadding().background(colors.bg)) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                AsyncImage(
                        model = album?.artUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(colors.surface2),
                )
                Box(
                        Modifier.fillMaxSize()
                                .background(
                                        Brush.verticalGradient(
                                                listOf(Color.Transparent, colors.bg),
                                                startY = 300f,
                                        )
                                ),
                )
                IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Back", tint = Color.White)
                }
            }
        }
        item {
            Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                SectionKicker(label = "Side A \u00B7 Album", color = colors.textMute)
                Spacer(Modifier.height(6.dp))
                Text(
                        text =
                                (album?.title ?: "\u2014").let {
                                    it + if (!it.endsWith(".")) "." else ""
                                },
                        style = text.queueHeadline56,
                        color = colors.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                        text =
                                "${album?.artist ?: "\u2014"}" +
                                        ((album?.year ?: 0).takeIf { it > 0 }?.let { " \u00B7 $it" }
                                                ?: ""),
                        style = text.editorialBody13,
                        color = colors.textDim,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                            onClick = { vm.playAll(songs) },
                            shape = RoundedCornerShape(100.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play", style = text.pillLabel12)
                    }
                    FilledTonalButton(
                            onClick = { vm.shuffle(songs) },
                            shape = RoundedCornerShape(100.dp),
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle", style = text.pillLabel12)
                    }
                }
            }
        }

        itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
            Hairline(modifier = Modifier.padding(horizontal = 24.dp))
            AlbumTrackRow(
                    position = song.trackNumber.takeIf { it > 0 } ?: (i + 1),
                    song = song,
                    onClick = { vm.playSongFromAlbum(song, songs) },
            )
        }
    }
}

@Composable
private fun AlbumTrackRow(position: Int, song: SongEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
    ) {
        MonoText(
                text = "%02d".format(position),
                color = colors.textMute.copy(alpha = 0.6f),
                size = MonoSize.S10,
                modifier = Modifier.width(28.dp).padding(top = 4.dp),
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
        }
        Spacer(Modifier.width(14.dp))
        MonoText(
                text = formatDuration(song.duration),
                color = colors.textMute.copy(alpha = 0.6f),
                size = MonoSize.S10,
                modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ── Artist Detail ─────────────────────────────────────────────────────────
@Composable
fun ArtistDetailScreen(
        navController: NavController,
        artistId: Long,
        vm: ArtistDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(artistId) { vm.load(artistId) }
    val artist by vm.artist.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    LazyColumn(Modifier.fillMaxSize().systemBarsPadding().background(colors.bg)) {
        item {
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(start = 8.dp, end = 24.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            "Back",
                            tint = colors.text,
                    )
                }
                Spacer(Modifier.weight(1f))
                MonoText(text = "ARTIST", color = colors.textMute, size = MonoSize.S10)
            }
        }
        item {
            val initial = artist?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "\u2014"
            Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                        text = initial,
                        style = text.homeFeature96,
                        color = colors.accent,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                        text = artist?.name ?: "\u2014",
                        style = text.editorialHeadline32,
                        color = colors.text,
                )
                Spacer(Modifier.height(8.dp))
                MonoText(
                        text =
                                "${albums.size} ALBUMS \u00B7 ${songs.size} SONGS",
                        color = colors.textMute,
                        size = MonoSize.S10,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { vm.playAll() }, shape = RoundedCornerShape(100.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play", style = text.pillLabel12)
                    }
                    FilledTonalButton(
                            onClick = { vm.shuffle() },
                            shape = RoundedCornerShape(100.dp),
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle", style = text.pillLabel12)
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item {
                EditorialDivider(
                        label = "Albums",
                        counter = albums.size.toString(),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                )
                LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) { items(albums, key = { it.id }) { album -> ArtistAlbumTile(album) } }
                Spacer(Modifier.height(20.dp))
            }
        }

        if (songs.isNotEmpty()) {
            item {
                EditorialDivider(
                        label = "Songs",
                        counter = songs.size.toString(),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
            items(songs, key = { it.id }) { song ->
                Hairline(modifier = Modifier.padding(horizontal = 24.dp))
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clickable { vm.playSong(song) }
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = song.title,
                                style = text.trackTitle22,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                                text = song.album,
                                style = text.metaSmall115,
                                color = colors.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                    }
                    MonoText(
                            text = formatDuration(song.duration),
                            color = colors.textMute.copy(alpha = 0.6f),
                            size = MonoSize.S10,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistAlbumTile(album: AlbumEntity) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Column(modifier = Modifier.width(140.dp)) {
        Box(
                modifier =
                        Modifier.size(140.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.surface2),
        ) {
            album.artUri?.let {
                AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
                text = album.title,
                style = text.rotationTitle18,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
        )
        if (album.year > 0) {
            Text(
                    text = album.year.toString(),
                    style = text.metaSmall115,
                    color = colors.textDim,
            )
        }
    }
}

// ── Playlist Detail ───────────────────────────────────────────────────────
@Composable
fun PlaylistDetailScreen(navController: NavController, playlistId: Long) {
    val vm: PlaylistDetailViewModel = hiltViewModel()
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val filteredSongs by vm.filteredSongs.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val toastMsg by vm.toastMessage.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.clearToast()
        }
    }

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    val totalMs = remember(songs) { songs.sumOf { it.duration } }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().background(colors.bg)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(start = 8.dp, end = 24.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                "Back",
                                tint = colors.text,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    MonoText(text = "PLAYLIST", color = colors.textMute, size = MonoSize.S10)
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                            text = playlist?.name ?: "\u2014",
                            style = text.queueHeadline56,
                            color = colors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    MonoText(
                            text =
                                    "${songs.size} TRACKS \u00B7 ${
                                        formatDurationLong(totalMs)
                                    }",
                            color = colors.textMute,
                            size = MonoSize.S10,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                                onClick = { vm.playSongs() },
                                shape = RoundedCornerShape(100.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play", style = text.pillLabel12)
                        }
                        FilledTonalButton(
                                onClick = { vm.shuffleSongs() },
                                shape = RoundedCornerShape(100.dp),
                        ) {
                            Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle", style = text.pillLabel12)
                        }
                        FilledTonalButton(
                                onClick = { showAddSheet = true },
                                shape = RoundedCornerShape(100.dp),
                        ) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add", style = text.pillLabel12)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            if (songs.isEmpty()) {
                item {
                    Column(
                            Modifier.fillMaxWidth().padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                                "No songs yet.",
                                style = text.editorialHeadline32,
                                color = colors.textDim,
                        )
                        Spacer(Modifier.height(8.dp))
                        SectionKicker(label = "Tap + to add", color = colors.accent)
                    }
                }
            } else {
                itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                    Hairline(modifier = Modifier.padding(horizontal = 24.dp))
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .clickable { vm.playSongs(i) }
                                            .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MonoText(
                                text = "%02d".format(i + 1),
                                color = colors.textMute.copy(alpha = 0.55f),
                                size = MonoSize.S10,
                                modifier = Modifier.width(28.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                        Modifier.size(44.dp)
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
                        IconButton(onClick = { vm.removeSongFromPlaylist(song.id) }) {
                            Icon(
                                    Icons.Rounded.RemoveCircleOutline,
                                    "Remove",
                                    tint = colors.textMute,
                                    modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
                onDismissRequest = {
                    showAddSheet = false
                    vm.setSearchQuery("")
                },
                containerColor = colors.surface,
        ) {
            Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                Text(
                        text = "Add songs.",
                        style = text.editorialHeadline32,
                        color = colors.text,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
                OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { vm.setSearchQuery(it) },
                        placeholder = { Text("Search songs\u2026") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textDim) },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(10.dp))
                Hairline()

                if (filteredSongs.isEmpty()) {
                    Box(
                            modifier = Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center,
                    ) {
                        Text(
                                if (searchQuery.isNotBlank()) "No matching songs"
                                else "All songs already added",
                                style = text.bodyMedium14,
                                color = colors.textDim,
                        )
                    }
                } else {
                    val limited = remember(filteredSongs) { filteredSongs.take(50) }
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(limited, key = { it.id }) { song ->
                            Row(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .clickable { vm.addSongToPlaylist(song.id) }
                                                    .padding(
                                                            horizontal = 24.dp,
                                                            vertical = 12.dp,
                                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                            ) {
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
                                    )
                                }
                                Icon(
                                        Icons.Rounded.Add,
                                        null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────
private fun formatDurationLong(totalMs: Long): String {
    val totalSecs = totalMs / 1000L
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    return if (h > 0) "${h}H ${m}M" else "${m}M"
}

@Suppress("unused")
private fun albumHeadline(album: String): AnnotatedString =
        buildAnnotatedString {
            withStyle(SpanStyle()) { append(album) }
            append(".")
        }
