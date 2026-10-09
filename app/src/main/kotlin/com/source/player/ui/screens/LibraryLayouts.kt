package com.source.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.source.player.data.db.entity.AlbumEntity
import com.source.player.data.db.entity.ArtistEntity
import com.source.player.data.db.entity.SongEntity
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText

/*
 * Gallery and Details renderings of the Library tabs (the List renderings stay in
 * LibraryScreen.kt). Every item keeps the click behaviour of its List counterpart:
 * tap = play / open detail, "…" = song options.
 */

private val GalleryPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 96.dp)

@Composable
private fun OptionsButton(onClick: () -> Unit) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Text(
            text = "…",
            style = text.trackTitle22,
            color = colors.textMute,
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp),
    )
}

// ── Songs ─────────────────────────────────────────────────────────────────
@Composable
internal fun SongsGallery(
        songs: List<SongEntity>,
        query: String,
        columns: Int,
        onSongClick: (Int) -> Unit,
        onMore: (SongEntity) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = GalleryPadding,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
    ) {
        items(songs.size, key = { songs[it].id }, contentType = { "song" }) { i ->
            val song = songs[i]
            Column(modifier = Modifier.animateItem().clickable { onSongClick(i) }) {
                AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                                Modifier.fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(colors.surface2),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = highlightMatch(song.title, query, colors.accent),
                                style = text.rotationTitle18,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                                text = highlightMatch(song.artist, query, colors.accent),
                                style = text.metaSmall115,
                                color = colors.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OptionsButton { onMore(song) }
                }
            }
        }
    }
}

@Composable
internal fun SongsDetailsList(
        songs: List<SongEntity>,
        query: String,
        onSongClick: (Int) -> Unit,
        onMore: (SongEntity) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(songs.size, key = { songs[it].id }, contentType = { "song" }) { i ->
            val song = songs[i]
            Hairline(modifier = Modifier.animateItem().padding(horizontal = 24.dp))
            Row(
                    modifier =
                            Modifier.animateItem()
                                    .fillMaxWidth()
                                    .clickable { onSongClick(i) }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                                Modifier.size(72.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(colors.surface2),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = highlightMatch(song.title, query, colors.accent),
                            style = text.trackTitle22,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                            text = highlightMatch(song.artist, query, colors.accent),
                            style = text.metaSmall115,
                            color = colors.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                    )
                    if (song.album.isNotBlank()) {
                        Text(
                                text = song.album,
                                style = text.metaSmall115,
                                color = colors.textMute,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                    }
                    MonoText(
                            text =
                                    listOfNotNull(
                                                    formatDuration(song.duration),
                                                    song.year.takeIf { it > 0 }?.toString(),
                                            )
                                            .joinToString(" · "),
                            color = colors.textMute,
                            size = MonoSize.S10,
                    )
                }
                OptionsButton { onMore(song) }
            }
        }
    }
}

// ── Albums (the Gallery rendering lives in LibraryScreen.kt) ──────────────
/** List (48dp cover, title + artist) and Details (96dp cover, + year / track count). */
@Composable
internal fun AlbumsRows(
        albums: List<AlbumEntity>,
        query: String,
        detailed: Boolean,
        onClick: (AlbumEntity) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            Hairline(modifier = Modifier.animateItem().padding(horizontal = 24.dp))
            Row(
                    modifier =
                            Modifier.animateItem()
                                    .fillMaxWidth()
                                    .clickable { onClick(album) }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                        model = album.artUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                                Modifier.size(if (detailed) 96.dp else 48.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(colors.surface2),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = highlightMatch(album.title, query, colors.accent),
                            style = if (detailed) text.trackTitle22 else text.rotationTitle18,
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
                    if (detailed) {
                        MonoText(
                                text =
                                        listOfNotNull(
                                                        album.year.takeIf { it > 0 }?.toString(),
                                                        "${album.songCount} " +
                                                                if (album.songCount == 1) "track"
                                                                else "tracks",
                                                )
                                                .joinToString(" · "),
                                color = colors.textMute,
                                size = MonoSize.S10,
                        )
                    }
                }
            }
        }
    }
}

// ── Artists (List and Details live in LibraryScreen.kt) ───────────────────
/** Tiles with the artist's initial in large type (the app stores no artist images). */
@Composable
internal fun ArtistsGallery(
        artists: List<ArtistEntity>,
        query: String,
        columns: Int,
        onClick: (ArtistEntity) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = GalleryPadding,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
    ) {
        items(artists, key = { it.id }, contentType = { "artist" }) { artist ->
            Column(modifier = Modifier.animateItem().clickable { onClick(artist) }) {
                Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                                Modifier.fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(colors.surface2),
                ) {
                    Text(
                            text = artist.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#",
                            style = text.libraryLetter64,
                            color = colors.accent,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                        text = highlightMatch(artist.name, query, colors.accent),
                        style = text.rotationTitle18,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                )
            }
        }
    }
}
