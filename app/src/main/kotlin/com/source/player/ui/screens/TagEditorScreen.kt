package com.source.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.source.player.ui.viewmodel.TagEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorScreen(
        navController: NavController,
        songId: Long,
        vm: TagEditorViewModel = hiltViewModel(),
) {
  val song by vm.song.collectAsState()
  val saved by vm.saved.collectAsState()
  val title by vm.title.collectAsState()
  val artist by vm.artist.collectAsState()
  val album by vm.album.collectAsState()
  val year by vm.year.collectAsState()
  val genre by vm.genre.collectAsState()
  val folderSongs by vm.folderSongs.collectAsState()
  val applyArtist by vm.applyArtistToFolder.collectAsState()
  val applyAlbum by vm.applyAlbumToFolder.collectAsState()
  val applyYear by vm.applyYearToFolder.collectAsState()
  val applyGenre by vm.applyGenreToFolder.collectAsState()
  val toastMsg by vm.toastMessage.collectAsState()
  val context = androidx.compose.ui.platform.LocalContext.current

  LaunchedEffect(toastMsg) {
    toastMsg?.let {
      android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
      vm.clearToast()
    }
  }

  LaunchedEffect(saved) { if (saved) navController.popBackStack() }

  val hasFolderSongs = folderSongs.isNotEmpty()
  val batchActive = hasFolderSongs && (applyArtist || applyAlbum || applyYear || applyGenre)

  Scaffold(
          topBar = {
            TopAppBar(
                    title = {
                      Text(
                              "Edit Tags",
                              fontWeight = FontWeight.Bold,
                      )
                    },
                    navigationIcon = {
                      IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                      }
                    },
                    actions = {
                      Button(
                              onClick = { vm.save() },
                              shape = RoundedCornerShape(50),
                              contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                              modifier = Modifier.padding(end = 8.dp),
                      ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                    },
                    colors =
                            TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                            ),
            )
          }
  ) { padding ->
    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {

      // ── Title Row (with album art circle) ─────────────────────────────────
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel("Title")
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // Album art circle
          Box(
                  modifier =
                          Modifier.size(44.dp)
                                  .clip(CircleShape)
                                  .background(MaterialTheme.colorScheme.surfaceVariant),
                  contentAlignment = Alignment.Center,
          ) {
            if (song?.albumArtUri != null) {
              AsyncImage(
                      model = song?.albumArtUri,
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize(),
              )
            } else {
              Icon(
                      Icons.Rounded.MusicNote,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(20.dp),
              )
            }
          }
          TagTextField(
                  value = title,
                  onValueChange = { vm.title.value = it },
                  trailingIcon = Icons.Rounded.Title,
                  modifier = Modifier.weight(1f),
          )
        }
      }

      // ── Artist ────────────────────────────────────────────────────────────
      TagFieldWithFolderToggle(
              label = "Artist",
              value = artist,
              onValueChange = { vm.artist.value = it },
              trailingIcon = Icons.Rounded.Person,
              applyToFolder = applyArtist,
              onToggleChange = { vm.applyArtistToFolder.value = it },
              hasFolderSongs = hasFolderSongs,
      )

      // ── Album ─────────────────────────────────────────────────────────────
      TagFieldWithFolderToggle(
              label = "Album",
              value = album,
              onValueChange = { vm.album.value = it },
              trailingIcon = Icons.Rounded.Album,
              applyToFolder = applyAlbum,
              onToggleChange = { vm.applyAlbumToFolder.value = it },
              hasFolderSongs = hasFolderSongs,
      )

      // ── Year + Genre ──────────────────────────────────────────────────────
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TagFieldWithFolderToggle(
                label = "Year",
                value = year,
                onValueChange = { vm.year.value = it },
                trailingIcon = Icons.Rounded.CalendarMonth,
                applyToFolder = applyYear,
                onToggleChange = { vm.applyYearToFolder.value = it },
                hasFolderSongs = hasFolderSongs,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
        )
        TagFieldWithFolderToggle(
                label = "Genre",
                value = genre,
                onValueChange = { vm.genre.value = it },
                trailingIcon = Icons.Rounded.MusicNote,
                applyToFolder = applyGenre,
                onToggleChange = { vm.applyGenreToFolder.value = it },
                hasFolderSongs = hasFolderSongs,
                modifier = Modifier.weight(1f),
        )
      }

      // ── Impacted Songs ────────────────────────────────────────────────────
      if (hasFolderSongs && batchActive) {
        ImpactedSongsSection(
                songs = folderSongs,
                count = folderSongs.size,
        )
      }
    }
  }
}

// ── Reusable Composables ─────────────────────────────────────────────────────

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
  Text(
          text = text.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          letterSpacing = 1.2.sp,
          modifier = modifier,
  )
}

@Composable
private fun TagTextField(
        value: String,
        onValueChange: (String) -> Unit,
        trailingIcon: ImageVector,
        modifier: Modifier = Modifier,
        keyboardType: KeyboardType = KeyboardType.Text,
) {
  TextField(
          value = value,
          onValueChange = onValueChange,
          singleLine = true,
          trailingIcon = {
            Icon(
                    trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
            )
          },
          keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
          colors =
                  TextFieldDefaults.colors(
                          focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                          focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                          unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                          disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                  ),
          shape = RoundedCornerShape(10.dp),
          modifier = modifier.fillMaxWidth(),
  )
}

@Composable
private fun TagFieldWithFolderToggle(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
        trailingIcon: ImageVector,
        applyToFolder: Boolean,
        onToggleChange: (Boolean) -> Unit,
        hasFolderSongs: Boolean,
        modifier: Modifier = Modifier,
        keyboardType: KeyboardType = KeyboardType.Text,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      FieldLabel(label)
      if (hasFolderSongs) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
                  "APPLY TO FOLDER",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  letterSpacing = 0.8.sp,
          )
          Switch(
                  checked = applyToFolder,
                  onCheckedChange = onToggleChange,
                  modifier = Modifier.height(20.dp).padding(0.dp),
          )
        }
      }
    }
    TagTextField(
            value = value,
            onValueChange = onValueChange,
            trailingIcon = trailingIcon,
            keyboardType = keyboardType,
    )
  }
}

@Composable
private fun ImpactedSongsSection(
        songs: List<com.source.player.data.db.entity.SongEntity>,
        count: Int,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
              "IMPACTED SONGS ($count)".uppercase(),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              letterSpacing = 1.2.sp,
      )
      Text(
              "Batch Edit Active",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      songs.forEach { s ->
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
                  modifier =
                          Modifier.size(36.dp)
                                  .clip(CircleShape)
                                  .background(MaterialTheme.colorScheme.surface),
                  contentAlignment = Alignment.Center,
          ) {
            Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
                    s.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
            )
            Text(
                    "${s.artist} • ${s.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
            )
          }
          Icon(
                  Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}
