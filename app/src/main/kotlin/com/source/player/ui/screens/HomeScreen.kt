package com.source.player.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.data.db.entity.SongEntity
import com.source.player.ui.components.EditorialDivider
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.components.SectionKicker
import com.source.player.ui.navigation.Routes
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
        navController: NavController,
        vm: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val currentSong by vm.currentSong.collectAsStateWithLifecycle()
    val songCount by vm.songCount.collectAsStateWithLifecycle()
    val scanProgress by vm.scanProgress.collectAsStateWithLifecycle()
    val quickPicks by vm.quickPicks.collectAsStateWithLifecycle()
    val recentlyAdded by vm.recentlyAdded.collectAsStateWithLifecycle()

    val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

    var permissionGranted by remember {
        mutableStateOf(
                ContextCompat.checkSelfPermission(context, permission) ==
                        PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                permissionGranted = granted
                if (granted) vm.scanLibrary()
            }

    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText

    if (songCount == 0) {
        EmptyLibrary(
                permissionGranted = permissionGranted,
                scanProgress = scanProgress,
                onRequestPermission = { permissionLauncher.launch(permission) },
                onScan = { vm.scanLibrary() },
        )
        return
    }

    // Feature track: currently playing (or loaded), falling back to the most
    // recently added song so the hero is never empty.
    val featureSong: SongEntity? =
            remember(currentSong, recentlyAdded) {
                val currentId = currentSong?.mediaId?.toLongOrNull()
                recentlyAdded.firstOrNull { it.id == currentId } ?: recentlyAdded.firstOrNull()
            }

    val rotationAlbums =
            remember(recentlyAdded) { recentlyAdded.distinctBy { it.albumId }.take(6) }
    val lateListens = remember(recentlyAdded) { recentlyAdded.drop(1).take(5) }

    LazyColumn(modifier = Modifier.fillMaxSize().systemBarsPadding().background(colors.bg)) {
        // Masthead -------------------------------------------------------
        item { Masthead() }

        // Feature track -------------------------------------------------
        item {
            if (featureSong != null) {
                val featureIndex =
                        remember(quickPicks, featureSong) {
                            quickPicks.indexOf(featureSong).coerceAtLeast(0)
                        }
                FeatureTrack(
                        song = featureSong,
                        index = featureIndex,
                        onPlay = { vm.playSongs(listOf(featureSong)) },
                        onOpen = { navController.navigate(Routes.PLAYER) },
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        // Editorial card (continuing album) -----------------------------
        if (featureSong != null) {
            item {
                EditorialDivider(
                        label = "Feature",
                        counter = "01 / 03",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                                .padding(top = 20.dp),
                )
                EditorialCard(
                        song = featureSong,
                        onClick = { vm.playSongs(listOf(featureSong)) },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // ON ROTATION — recently played albums ---------------------------
        if (rotationAlbums.isNotEmpty()) {
            item {
                EditorialDivider(
                        label = "On Rotation",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                )
                LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(rotationAlbums, key = { it.id }) { song ->
                        RotationTile(
                                song = song,
                                onClick = {
                                    val albumSongs =
                                            recentlyAdded.filter { it.albumId == song.albumId }
                                    vm.playSongs(albumSongs)
                                },
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }

        // LATE LISTENS — recent tracks -----------------------------------
        if (lateListens.isNotEmpty()) {
            item {
                EditorialDivider(
                        label = "Late Listens",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
            itemsIndexed(lateListens, key = { _, s -> s.id }) { index, song ->
                LateListenRow(
                        position = index + 1,
                        song = song,
                        onClick = { vm.playSongs(lateListens, index) },
                )
                if (index < lateListens.lastIndex) {
                    Hairline(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

// ── Masthead: "Source" + live date/time ───────────────────────────────────
@Composable
private fun Masthead() {
    val text = MaterialTheme.sourceText
    val colors = MaterialTheme.sourceColors

    // Tick once per minute to refresh the clock.
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = Date()
        }
    }
    val fmt = remember { SimpleDateFormat("EEE dd.MM \u00B7 HH:mm", Locale.getDefault()) }
    val dateStamp = remember(now) { fmt.format(now).uppercase(Locale.getDefault()) }

    Column {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                    text = "Source",
                    style = text.masthead30,
                    color = colors.text,
            )
            MonoText(text = dateStamp, color = colors.textMute, size = MonoSize.S10)
        }
        Hairline()
    }
}

// ── Feature track: SIDE kicker, 96px italic title, art dot + play ─────────
@Composable
private fun FeatureTrack(song: SongEntity, index: Int, onPlay: () -> Unit, onOpen: () -> Unit) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    val kicker = remember(index) { "SIDE A \u00B7 TRACK %02d".format((index + 1).coerceAtLeast(1)) }

    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp)) {
        SectionKicker(label = kicker, color = colors.accent)
        Spacer(Modifier.height(10.dp))
        Text(
                text = song.title,
                style = text.homeFeature96,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onOpen),
        )
        Spacer(Modifier.height(18.dp))
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                    modifier =
                            Modifier.size(50.dp)
                                    .clip(CircleShape)
                                    .background(colors.surface2),
            ) {
                song.albumArtUri?.let { uri ->
                    AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = song.artist,
                        style = text.bodyMedium14,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                )
                Text(
                        text =
                                song.album.ifBlank { "\u2014" } +
                                        if (song.year > 0) " \u2014 ${song.year}" else "",
                        style = text.metaSmall115,
                        color = colors.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                )
            }
            FilledIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors =
                            IconButtonDefaults.filledIconButtonColors(containerColor = colors.accent),
            ) {
                Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = colors.bg,
                        modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

// ── Editorial "album" card: 3:2 art + italic headline + body + CTA ────────
@Composable
private fun EditorialCard(song: SongEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .aspectRatio(3f / 2f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.surface2),
        ) {
            song.albumArtUri?.let { uri ->
                AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
                text = buildAlbumHeadline(song.album, song.artist),
                style = text.editorialHeadline32,
                color = colors.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
                text = "${song.artist} \u2014 tap to play the album.",
                style = text.editorialBody13,
                color = colors.textDim,
        )
        Spacer(Modifier.height(14.dp))
        SectionKicker(label = "Play the album \u2192", color = colors.accent)
    }
}

/** Album name in italic, the word "revisited" in roman, period. */
private fun buildAlbumHeadline(album: String, artist: String): AnnotatedString =
        buildAnnotatedString {
            withStyle(SpanStyle()) {
                append(album.ifBlank { artist })
            }
            append(", revisited.")
        }

// ── 160×160 rotation tile ─────────────────────────────────────────────────
@Composable
private fun RotationTile(song: SongEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Column(modifier = Modifier.width(160.dp).clickable(onClick = onClick)) {
        Box(
                modifier =
                        Modifier.size(160.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.surface2),
        ) {
            song.albumArtUri?.let { uri ->
                AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
                text = song.album.ifBlank { song.title },
                style = text.rotationTitle18,
                color = colors.text,
                maxLines = 2,
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

// ── Late-listens row: 01 number, 22px title, duration ─────────────────────
@Composable
private fun LateListenRow(position: Int, song: SongEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
    ) {
        MonoText(
                text = "%02d".format(position),
                color = colors.textMute,
                size = MonoSize.S10,
                modifier = Modifier.padding(top = 4.dp),
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
            Spacer(Modifier.height(3.dp))
            Text(
                    text = song.artist,
                    style = text.metaSmall115,
                    color = colors.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(14.dp))
        MonoText(
                text = formatDuration(song.duration),
                color = colors.textMute,
                size = MonoSize.S10,
                modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ── Empty state / scan CTA (kept Vinyl-lite) ──────────────────────────────
@Composable
private fun EmptyLibrary(
        permissionGranted: Boolean,
        scanProgress: String?,
        onRequestPermission: () -> Unit,
        onScan: () -> Unit,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().background(colors.bg).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
                text = "Nothing\nhere, yet.",
                style = text.queueHeadline56,
                color = colors.textDim,
                textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        SectionKicker(label = "Add music", color = colors.accent, align = TextAlign.Center)

        Spacer(Modifier.height(30.dp))

        when {
            !permissionGranted -> {
                OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
                    Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                                Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                                "Source needs permission to read your audio files.",
                                style = text.bodyMedium14,
                                color = colors.textDim,
                                textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onRequestPermission, shape = RoundedCornerShape(100.dp)) {
                            Text("Grant permission")
                        }
                    }
                }
            }
            scanProgress != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = colors.accent)
                    Spacer(Modifier.height(12.dp))
                    Text(
                            text = scanProgress,
                            style = text.bodyMedium14,
                            color = colors.textDim,
                    )
                }
            }
            else -> {
                Button(onClick = onScan, shape = RoundedCornerShape(100.dp)) {
                    Text("Scan library")
                }
            }
        }
    }
}

