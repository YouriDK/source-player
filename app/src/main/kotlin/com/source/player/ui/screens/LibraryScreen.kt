package com.source.player.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.data.db.entity.*
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.components.PillTab
import com.source.player.ui.components.SectionKicker
import com.source.player.ui.components.VerticalHairline
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.navigation.Routes
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.LibraryTab
import com.source.player.ui.viewmodel.LibraryViewModel
import com.source.player.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
        navController: NavController,
        vm: LibraryViewModel = hiltViewModel(),
        playerVm: PlayerViewModel = hiltViewModel(),
) {
    val filteredSongs by vm.filteredSongs.collectAsStateWithLifecycle()
    val filteredAlbums by vm.filteredAlbums.collectAsStateWithLifecycle()
    val filteredArtists by vm.filteredArtists.collectAsStateWithLifecycle()
    val filteredPlaylists by vm.filteredPlaylists.collectAsStateWithLifecycle()
    val filteredGenres by vm.filteredGenres.collectAsStateWithLifecycle()

    val query by vm.query.collectAsStateWithLifecycle()
    val selectedTab by vm.activeTab.collectAsStateWithLifecycle()
    val nowPlaying by playerVm.currentSong.collectAsStateWithLifecycle()
    // Mini player sits above the tab bar when a track is playing — the
    // floating search bar must stack above it, never be occluded.
    // Matches the "Library / w/ mini player" artboard and README §Screens/2.4.
    val miniPlayerVisible = nowPlaying != null

    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }
    var flashLetter by remember { mutableStateOf<Char?>(null) }

    selectedSong?.let { song ->
        SongOptionsSheet(
                song = song,
                navController = navController,
                onDismiss = { selectedSong = null },
        )
    }

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    val typing = query.isNotEmpty()
    // Kept as State objects (no `by`) so the animated values are read only
    // inside graphicsLayer blocks — never in composition — and the fade
    // animation doesn't recompose the screen every frame.
    val chromeAlpha =
            animateFloatAsState(
                    targetValue = if (typing) 0.35f else 1f,
                    animationSpec = tween(250),
                    label = "libraryChromeAlpha",
            )
    val railAlpha =
            animateFloatAsState(
                    targetValue = if (typing) 0f else 1f,
                    animationSpec = tween(250),
                    label = "libraryRailAlpha",
            )

    Box(Modifier.fillMaxSize().systemBarsPadding().background(colors.bg)) {
        Column(Modifier.fillMaxSize()) {
            // Header --------------------------------------------------
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "Library", style = text.editorialHeadline32, color = colors.text)
                LibraryCountLabel(
                        selectedTab = selectedTab,
                        typing = typing,
                        visibleCount =
                                visibleCount(
                                        selectedTab,
                                        filteredSongs,
                                        filteredAlbums,
                                        filteredArtists,
                                        filteredPlaylists,
                                        filteredGenres,
                                ),
                        vm = vm,
                )
            }

            // Tabs ----------------------------------------------------
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .graphicsLayer { alpha = chromeAlpha.value }
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LibraryTab.entries.forEach { tab ->
                    PillTab(
                            label = tab.name,
                            selected = tab == selectedTab,
                            onClick = { if (!typing) vm.onTabChange(tab) },
                    )
                }
            }
            Hairline(modifier = Modifier.graphicsLayer { alpha = chromeAlpha.value })

            // Body ----------------------------------------------------
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    LibraryTab.Songs ->
                            VinylSongsList(
                                    songs = filteredSongs,
                                    query = query,
                                    onPlayAll = { vm.playAllSongs() },
                                    onShuffleAll = { vm.shuffleAllSongs() },
                                    onSongClick = { idx ->
                                        vm.playSongsFromIndex(filteredSongs, idx)
                                    },
                                    onMore = { selectedSong = it },
                            )
                    LibraryTab.Albums ->
                            VinylAlbumsGrid(
                                    albums = filteredAlbums,
                                    query = query,
                                    onClick = {
                                        navController.navigate(Routes.albumDetail(it.id))
                                    },
                            )
                    LibraryTab.Artists ->
                            VinylArtistsList(
                                    artists = filteredArtists,
                                    query = query,
                                    railAlpha = { railAlpha.value },
                                    flashLetter = flashLetter,
                                    onJump = { c -> flashLetter = c },
                                    onClick = {
                                        navController.navigate(Routes.artistDetail(it.id))
                                    },
                            )
                    LibraryTab.Playlists ->
                            VinylPlaylistsList(
                                    playlists = filteredPlaylists,
                                    query = query,
                                    onCreate = { vm.createPlaylist(it) },
                                    onClick = {
                                        navController.navigate(Routes.playlistDetail(it.id))
                                    },
                            )
                    LibraryTab.Genres ->
                            VinylGenresList(genres = filteredGenres, query = query)
                }
            }
        }

        // Floating search bar — overlays, pinned above the system nav bar.
        // When the mini player is visible it stacks directly on top of the tab
        // bar, so the search bar must clear it (+66dp). Matches the design's
        // bottom-stack order: tab bar → mini player → search bar.
        FloatingSearchBar(
                query = query,
                placeholder = placeholderFor(selectedTab),
                onChange = vm::onQueryChange,
                onClear = { vm.onQueryChange("") },
                modifier =
                        Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 12.dp,
                                        bottom = if (miniPlayerVisible) 78.dp else 12.dp,
                                )
                                .imePadding(),
        )
    }

    // Letter-flash auto-clear after 600ms (SEARCH_MERGE.md §2b).
    LaunchedEffect(flashLetter) {
        if (flashLetter != null) {
            delay(600)
            flashLetter = null
        }
    }
}

// "N / M" header label. Collects the unfiltered flow of the selected tab
// here so that collection only ever recomposes this label, never the screen.
@Composable
private fun LibraryCountLabel(
        selectedTab: LibraryTab,
        typing: Boolean,
        visibleCount: Int,
        vm: LibraryViewModel,
) {
    val colors = MaterialTheme.sourceColors
    val counter =
            if (typing) {
                val total =
                        when (selectedTab) {
                            LibraryTab.Songs ->
                                    vm.songs.collectAsStateWithLifecycle().value.size
                            LibraryTab.Albums ->
                                    vm.albums.collectAsStateWithLifecycle().value.size
                            LibraryTab.Artists ->
                                    vm.artists.collectAsStateWithLifecycle().value.size
                            LibraryTab.Playlists ->
                                    vm.playlists.collectAsStateWithLifecycle().value.size
                            LibraryTab.Genres ->
                                    vm.genres.collectAsStateWithLifecycle().value.size
                        }
                "$visibleCount / $total"
            } else "A–Z"
    MonoText(
            text = counter,
            color = colors.textMute,
            size = MonoSize.S10,
    )
}

private fun visibleCount(
        tab: LibraryTab,
        songs: List<*>,
        albums: List<*>,
        artists: List<*>,
        playlists: List<*>,
        genres: List<*>,
): Int =
        when (tab) {
            LibraryTab.Songs -> songs.size
            LibraryTab.Albums -> albums.size
            LibraryTab.Artists -> artists.size
            LibraryTab.Playlists -> playlists.size
            LibraryTab.Genres -> genres.size
        }

private fun placeholderFor(tab: LibraryTab): String =
        when (tab) {
            LibraryTab.Songs -> "Filter songs…"
            LibraryTab.Albums -> "Filter albums…"
            LibraryTab.Artists -> "Filter artists…"
            LibraryTab.Playlists -> "Filter playlists…"
            LibraryTab.Genres -> "Filter genres…"
        }

// ── Songs: play-all / shuffle-all + 22px serif rows ───────────────────────
@Composable
private fun VinylSongsList(
        songs: List<SongEntity>,
        query: String,
        onPlayAll: () -> Unit,
        onShuffleAll: () -> Unit,
        onSongClick: (Int) -> Unit,
        onMore: (SongEntity) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    Column(Modifier.fillMaxSize()) {
        if (songs.isNotEmpty() && query.isBlank()) {
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
        if (songs.isEmpty() && query.isNotBlank()) {
            NoResultsState(query = query)
            return@Column
        }
        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                // animateItem: rows fade and slide into place when the list changes —
                // search filtering, a rescan adding tracks. Handled by the lazy layout
                // itself, so it costs nothing while the list is static.
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
                                    Modifier.size(48.dp)
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
                    }
                    Spacer(Modifier.width(10.dp))
                    MonoText(
                            text = formatDuration(song.duration),
                            color = colors.textMute,
                            size = MonoSize.S10,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                            text = "…",
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
        query: String,
        onClick: (AlbumEntity) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    if (albums.isEmpty() && query.isNotBlank()) {
        NoResultsState(query = query)
        return
    }
    LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            Column(modifier = Modifier.animateItem().clickable { onClick(album) }) {
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
                        text = highlightMatch(album.title, query, colors.accent),
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

// ── Artists: 64px accent italic jump letters + A-Z rail w/ flash ──────────
@Composable
private fun VinylArtistsList(
        artists: List<ArtistEntity>,
        query: String,
        railAlpha: () -> Float,
        flashLetter: Char?,
        onJump: (Char) -> Unit,
        onClick: (ArtistEntity) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    if (artists.isEmpty() && query.isNotBlank()) {
        NoResultsState(query = query)
        return
    }

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

    // Scroll-driven active letter. Kept as State — its value is only read
    // inside RailLetter, so scrolling recomposes the rail labels, not this
    // whole list.
    val activeLetter =
            remember(artists, letterIndex) {
                derivedStateOf {
                    artists.getOrNull(listState.firstVisibleItemIndex)
                            ?.name
                            ?.firstOrNull()
                            ?.uppercaseChar()
                }
            }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            itemsIndexed(artists, key = { _, a -> a.id }) { i, artist ->
                val prev = artists.getOrNull(i - 1)
                val thisLetter = artist.name.firstOrNull()?.uppercaseChar()
                val isNewLetter =
                        i == 0 ||
                                (prev?.name?.firstOrNull()?.uppercaseChar() != thisLetter)
                if (isNewLetter && thisLetter != null) {
                    val flashing = flashLetter == thisLetter
                    // 600ms accent-tinted gradient flash on the jumped letter.
                    val flashBg by
                            animateColorAsState(
                                    targetValue =
                                            if (flashing) colors.accent.copy(alpha = 0.14f)
                                            else Color.Transparent,
                                    animationSpec = tween(200),
                                    label = "letterFlash",
                            )
                    Text(
                            text = thisLetter.toString(),
                            style = text.libraryLetter64,
                            color =
                                    if (query.isBlank() || flashing) colors.accent
                                    else colors.textMute,
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .background(flashBg)
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
                                text = highlightMatch(artist.name, query, colors.accent),
                                style = text.trackTitle22,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                                text = "${artist.albumCount} albums · ${artist.songCount} songs",
                                style = text.metaSmall115,
                                color = colors.textDim,
                        )
                    }
                    Text(
                            text = "→",
                            style = text.trackTitle22,
                            color = colors.textMute,
                    )
                }
                Hairline(modifier = Modifier.padding(horizontal = 24.dp))
            }
        }

        // A-Z rail — fades out when typing, disables pointer input too. The
        // animated alpha is read only inside graphicsLayer blocks; composition
        // only sees the derived visible/hidden flip, never per-frame values.
        val railVisible by remember(railAlpha) { derivedStateOf { railAlpha() > 0f } }
        if (railVisible) {
            val letters = remember(letterIndex) { letterIndex.keys.toList() }
            val density = LocalDensity.current
            val topPadPx = with(density) { 20.dp.toPx() }
            val bottomPadPx = with(density) { 20.dp.toPx() }
            var columnHeightPx by remember { mutableStateOf(0) }

            VerticalHairline(modifier = Modifier.graphicsLayer { alpha = railAlpha() })
            Column(
                    modifier =
                            Modifier.width(28.dp)
                                    .fillMaxHeight()
                                    .graphicsLayer { alpha = railAlpha() }
                                    .padding(top = 20.dp, bottom = 20.dp)
                                    .onSizeChanged { columnHeightPx = it.height }
                                    .pointerInput(letters) {
                                        if (letters.isEmpty()) return@pointerInput
                                        awaitEachGesture {
                                            var lastLetter: Char? = null
                                            fun dispatch(y: Float) {
                                                val h = columnHeightPx
                                                if (h <= 0) return
                                                val inner =
                                                        (h - topPadPx - bottomPadPx)
                                                                .coerceAtLeast(1f)
                                                val slot =
                                                        (((y - topPadPx) / inner) *
                                                                        letters.size)
                                                                .toInt()
                                                                .coerceIn(0, letters.size - 1)
                                                val letter = letters[slot]
                                                if (letter != lastLetter) {
                                                    lastLetter = letter
                                                    letterIndex[letter]?.let { idx ->
                                                        scope.launch {
                                                            listState.scrollToItem(idx)
                                                        }
                                                        onJump(letter)
                                                    }
                                                }
                                            }
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            dispatch(down.position.y)
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change =
                                                        event.changes.firstOrNull() ?: break
                                                if (!change.pressed) break
                                                dispatch(change.position.y)
                                                change.consume()
                                            }
                                        }
                                    },
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                letters.forEach { letter -> RailLetter(letter, activeLetter) }
            }
        }
    }
}

// One A-Z rail label. Reads the scroll-derived active letter here so scroll
// updates recompose only these labels, not VinylArtistsList.
@Composable
private fun RailLetter(letter: Char, activeLetter: State<Char?>) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    val active = letter == activeLetter.value
    Box {
        Text(
                text = letter.toString(),
                style = text.kickerMono10,
                color = if (active) colors.accent else colors.textMute,
        )
    }
}

// ── Playlists ─────────────────────────────────────────────────────────────
@Composable
private fun VinylPlaylistsList(
        playlists: List<PlaylistEntity>,
        query: String,
        onClick: (PlaylistEntity) -> Unit,
        onCreate: (String) -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    if (playlists.isEmpty() && query.isNotBlank()) {
        NoResultsState(query = query)
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (query.isBlank()) {
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
                FilledTonalButton(
                        onClick = { showDialog = true },
                        shape = RoundedCornerShape(100.dp),
                ) {
                    Icon(HugeIcons.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("New", style = text.pillLabel12)
                }
            }
        }
        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                Hairline(modifier = Modifier.animateItem().padding(horizontal = 24.dp))
                Row(
                        modifier =
                                Modifier.animateItem()
                                        .fillMaxWidth()
                                        .clickable { onClick(playlist) }
                                        .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                            text = highlightMatch(playlist.name, query, colors.accent),
                            style = text.trackTitle22,
                            color = colors.text,
                            modifier = Modifier.weight(1f),
                    )
                    Text(
                            text = "→",
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
private fun VinylGenresList(genres: List<GenreEntity>, query: String) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    if (genres.isEmpty() && query.isNotBlank()) {
        NoResultsState(query = query)
        return
    }
    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        itemsIndexed(genres, key = { _, g -> g.id }) { _, genre ->
            Hairline(modifier = Modifier.animateItem().padding(horizontal = 24.dp))
            Row(
                    modifier =
                            Modifier.animateItem()
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                        text = highlightMatch(genre.name, query, colors.accent),
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

// ── No-results editorial message ──────────────────────────────────────────
@Composable
private fun NoResultsState(query: String) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val msg = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.textDim)) {
                append("Nothing for “")
            }
            withStyle(SpanStyle(color = colors.text)) {
                append(query)
            }
            withStyle(SpanStyle(color = colors.textDim)) {
                append("”.")
            }
        }
        Text(
                text = msg,
                style = text.editorialHeadline32,
                textAlign = TextAlign.Center,
        )
    }
}

// ── Floating search bar (above system nav bar) ────────────────────────────
@Composable
private fun FloatingSearchBar(
        query: String,
        placeholder: String,
        onChange: (String) -> Unit,
        onClear: () -> Unit,
        modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val borderColor by
            animateColorAsState(
                    targetValue =
                            if (focused || query.isNotEmpty()) colors.accent else colors.hair,
                    animationSpec = tween(200),
                    label = "searchBarBorder",
            )

    Row(
            modifier =
                    modifier.clip(RoundedCornerShape(14.dp))
                            .background(colors.surface.copy(alpha = 0.92f))
                            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                            .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                            ) { focusRequester.requestFocus() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
                HugeIcons.Search,
                contentDescription = null,
                tint = if (focused || query.isNotEmpty()) colors.accent else colors.textDim,
                modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty() && !focused) {
                Text(
                        text = placeholder,
                        style =
                                text.rotationTitle18
                                        .copy(fontSize = 20.sp),
                        color = colors.textMute,
                )
            }
            BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    modifier =
                            Modifier.fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    textStyle =
                            text.rotationTitle18.copy(
                                    color = colors.text,
                            ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        if (query.isEmpty()) {
            MonoText(
                    text = "⌘ K",
                    color = colors.textMute,
                    size = MonoSize.S95,
            )
        } else {
            Box(
                    modifier =
                            Modifier.size(22.dp)
                                    .clip(CircleShape)
                                    .background(colors.surface2)
                                    .border(1.dp, colors.hair, CircleShape)
                                    .clickable { onClear() },
                    contentAlignment = Alignment.Center,
            ) {
                Icon(
                        HugeIcons.Close,
                        contentDescription = "Clear",
                        tint = colors.textDim,
                        modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ── Match highlighting ────────────────────────────────────────────────────
private fun highlightMatch(source: String, query: String, accent: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(source)
    val idx = source.lowercase().indexOf(query.lowercase())
    if (idx < 0) return AnnotatedString(source)
    return buildAnnotatedString {
        append(source.substring(0, idx))
        withStyle(SpanStyle(color = accent)) {
            append(source.substring(idx, idx + query.length))
        }
        append(source.substring(idx + query.length))
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
            query = "",
            onPlayAll = {},
            onShuffleAll = {},
            onSongClick = onSongClick,
            onMore = { onMoreClick?.invoke(it) },
    )
}
