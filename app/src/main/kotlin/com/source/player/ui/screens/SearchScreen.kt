package com.source.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.data.db.entity.AlbumEntity
import com.source.player.data.db.entity.ArtistEntity
import com.source.player.data.db.entity.SongEntity
import com.source.player.ui.components.EditorialDivider
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.components.SectionKicker
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.navigation.Routes
import com.source.player.ui.theme.oklchToColor
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.SearchViewModel

private data class CategoryTile(val label: String, val hue: Float)

private val DefaultCategories =
        listOf(
                CategoryTile("Ambient", 40f),
                CategoryTile("Folk", 80f),
                CategoryTile("Electronic", 260f),
                CategoryTile("Classical", 340f),
                CategoryTile("Jazz", 20f),
                CategoryTile("Post-rock", 200f),
        )

@Composable
fun SearchScreen(
        navController: NavController,
        vm: SearchViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val songResults by vm.songResults.collectAsStateWithLifecycle()
    val albumResults by vm.albumResults.collectAsStateWithLifecycle()
    val artistResults by vm.artistResults.collectAsStateWithLifecycle()

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    Column(Modifier.fillMaxSize().systemBarsPadding().background(colors.bg)) {
        // Headline
        Text(
                text = "Find.",
                style = text.findHeadline72,
                color = colors.text,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        )

        // Search input — pill, hair border, surface bg
        TextField(
                value = query,
                onValueChange = vm::onQueryChange,
                placeholder = {
                    Text("What are you after?", style = text.bodyMedium14, color = colors.textMute)
                },
                leadingIcon = {
                    Icon(
                            HugeIcons.Search,
                            null,
                            tint = colors.textDim,
                            modifier = Modifier.size(16.dp),
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(HugeIcons.Close, "Clear", tint = colors.textDim)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = text.bodyMedium14.copy(color = colors.text),
                shape = CircleShape,
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 18.dp)
                                .clip(CircleShape)
                                .border(1.dp, colors.hair, CircleShape),
                colors =
                        TextFieldDefaults.colors(
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = colors.accent,
                        ),
        )

        if (query.isBlank()) {
            // Categories grid
            SectionKicker(
                    label = "Categories",
                    color = colors.textMute,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp),
            )
            CategoriesGrid(
                    categories = DefaultCategories,
                    onClick = { vm.onQueryChange(it.label) },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                if (songResults.isNotEmpty()) {
                    item {
                        EditorialDivider(
                                label = "Songs",
                                counter = songResults.size.toString(),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        )
                    }
                    items(songResults, key = { it.id }) { song ->
                        SongRow(song = song, onClick = { vm.playSong(song, songResults) })
                    }
                }
                if (albumResults.isNotEmpty()) {
                    item {
                        EditorialDivider(
                                label = "Albums",
                                counter = albumResults.size.toString(),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        )
                    }
                    items(albumResults, key = { it.id }) { album ->
                        AlbumRow(
                                album = album,
                                onClick = {
                                    navController.navigate(Routes.albumDetail(album.id))
                                },
                        )
                    }
                }
                if (artistResults.isNotEmpty()) {
                    item {
                        EditorialDivider(
                                label = "Artists",
                                counter = artistResults.size.toString(),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        )
                    }
                    items(artistResults, key = { it.id }) { artist ->
                        ArtistRow(
                                artist = artist,
                                onClick = {
                                    navController.navigate(Routes.artistDetail(artist.id))
                                },
                        )
                    }
                }
                if (songResults.isEmpty() && albumResults.isEmpty() && artistResults.isEmpty()) {
                    item {
                        Box(
                                Modifier.fillMaxWidth().padding(40.dp),
                                contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                    "Nothing for \u201C$query\u201D.",
                                    style = text.editorialHeadline32,
                                    color = colors.textDim,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Categories ────────────────────────────────────────────────────────────
@Composable
private fun CategoriesGrid(
        categories: List<CategoryTile>,
        onClick: (CategoryTile) -> Unit,
        modifier: Modifier = Modifier,
) {
    val text = MaterialTheme.sourceText
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        categories.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { index, tile ->
                    val globalIndex = rowIndex * 2 + index
                    val tileColor = remember(tile.hue) { oklchToColor(0.28f, 0.07f, tile.hue) }
                    Box(
                            modifier =
                                    Modifier.weight(1f)
                                            .heightIn(min = 110.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(tileColor)
                                            .clickable { onClick(tile) }
                                            .padding(horizontal = 16.dp, vertical = 18.dp),
                    ) {
                        Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MonoText(
                                    text = "N\u00B0 %02d".format(globalIndex + 1),
                                    color = Color.White.copy(alpha = 0.5f),
                                    size = MonoSize.S10,
                            )
                            Text(
                                    text = tile.label,
                                    style =
                                            if (globalIndex % 2 == 1)
                                                    text.categoryLabel24.copy(
                                                    )
                                            else text.categoryLabel24,
                                    color = Color.White,
                            )
                        }
                    }
                }
                // Pad the final row if it's a single item (odd count).
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── Result rows ───────────────────────────────────────────────────────────
@Composable
private fun SongRow(song: SongEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Column {
        Hairline(modifier = Modifier.padding(horizontal = 24.dp))
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable(onClick = onClick)
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
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
        }
    }
}

@Composable
private fun AlbumRow(album: AlbumEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Column {
        Hairline(modifier = Modifier.padding(horizontal = 24.dp))
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable(onClick = onClick)
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                    model = album.artUri,
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
                        text = album.title,
                        style = text.rotationTitle18,
                        color = colors.text,
                        maxLines = 1,
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
            Text(text = "\u2192", style = text.trackTitle22, color = colors.textMute)
        }
    }
}

@Composable
private fun ArtistRow(artist: ArtistEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Column {
        Hairline(modifier = Modifier.padding(horizontal = 24.dp))
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clickable(onClick = onClick)
                                .padding(horizontal = 24.dp, vertical = 14.dp),
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
                        text = "${artist.songCount} songs",
                        style = text.metaSmall115,
                        color = colors.textDim,
                )
            }
            Text(text = "\u2192", style = text.trackTitle22, color = colors.textMute)
        }
    }
}
