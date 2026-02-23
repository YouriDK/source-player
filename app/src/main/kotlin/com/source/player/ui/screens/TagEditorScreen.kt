package com.source.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
  val trackNumber by vm.trackNumber.collectAsState()
  val genre by vm.genre.collectAsState()

  // Pop back automatically once saved
  LaunchedEffect(saved) { if (saved) navController.popBackStack() }

  Scaffold(
          topBar = {
            TopAppBar(
                    title = { Text("Edit Tags") },
                    navigationIcon = {
                      IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                      }
                    },
                    actions = {
                      TextButton(onClick = { vm.save() }) {
                        Text("Save", color = MaterialTheme.colorScheme.primary)
                      }
                    },
                    colors =
                            TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                            )
            )
          }
  ) { padding ->
    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Album art preview
      song?.albumArtUri?.let { artUri ->
        Card(
                modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally),
                shape = MaterialTheme.shapes.large
        ) {
          AsyncImage(
                  model = artUri,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
          )
        }
      }

      Spacer(Modifier.height(8.dp))

      // Editable fields
      TagField(label = "Title", value = title, onValueChange = { vm.title.value = it })
      TagField(label = "Artist", value = artist, onValueChange = { vm.artist.value = it })
      TagField(label = "Album", value = album, onValueChange = { vm.album.value = it })
      TagField(label = "Genre", value = genre, onValueChange = { vm.genre.value = it })

      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TagField(
                label = "Year",
                value = year,
                onValueChange = { vm.year.value = it },
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
        )
        TagField(
                label = "Track #",
                value = trackNumber,
                onValueChange = { vm.trackNumber.value = it },
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
        )
      }

      // Read-only info
      song?.let { s ->
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
                "File Info",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        ReadOnlyField("Path", s.path.substringAfterLast("/"))
        ReadOnlyField("Duration", formatDuration(s.duration))
        ReadOnlyField("Size", "%.1f MB".format(s.size / 1_000_000f))
      }

      Spacer(Modifier.height(24.dp))

      Button(
              onClick = { vm.save() },
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.large,
      ) {
        Icon(Icons.Rounded.Save, null)
        Spacer(Modifier.width(8.dp))
        Text("Save Changes")
      }
    }
  }
}

@Composable
private fun TagField(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier = Modifier,
        keyboardType: KeyboardType = KeyboardType.Text,
) {
  OutlinedTextField(
          value = value,
          onValueChange = onValueChange,
          label = { Text(label) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
          shape = MaterialTheme.shapes.medium,
          modifier = modifier.fillMaxWidth(),
  )
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1)
  }
}
