package com.source.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.source.player.ui.viewmodel.SettingsViewModel

// Preset accent colors from Stitch theme palette
private val presetAccentColors =
        listOf(
                0x0D33F2,
                0xE91E63,
                0xFF5722,
                0xFF9800,
                0x4CAF50,
                0x00BCD4,
                0x9C27B0,
                0xFFFFFF,
        )

@Composable
fun SettingsScreen(
        navController: NavController,
        vm: SettingsViewModel = hiltViewModel(),
) {
  val isDark by vm.isDarkMode.collectAsState()
  val gapless by vm.gapless.collectAsState()
  val ducking by vm.audioDucking.collectAsState()
  val restore by vm.restoreState.collectAsState()
  val scrobble by vm.scrobbling.collectAsState()
  val artPolicy by vm.artDownloadPolicy.collectAsState()
  val searchBottom by vm.searchBarAtBottom.collectAsState()
  val rememberTab by vm.rememberLastTab.collectAsState()
  val accentColor by vm.accentColor.collectAsState()

  var showLastFmModal by remember { mutableStateOf(false) }
  var showColorPicker by remember { mutableStateOf(false) }

  Column(
          Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()),
  ) {
    TopAppBar(
            title = { Text("Settings", style = MaterialTheme.typography.headlineLarge) },
            colors =
                    TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                    ),
    )

    SettingsSection("Appearance") {
      SettingsSwitch("Dark Mode", Icons.Rounded.DarkMode, isDark) { vm.setDarkMode(it) }
      SettingsItem(
              "Accent Color",
              Icons.Rounded.Palette,
              subtitle = "#${accentColor.toString(16).uppercase()}"
      ) { showColorPicker = true }
    }

    SettingsSection("Playback") {
      SettingsSwitch("Gapless Playback", Icons.Rounded.GraphicEq, gapless) { vm.setGapless(it) }
      SettingsSwitch("Audio Ducking", Icons.Rounded.VolumeDown, ducking) { vm.setAudioDucking(it) }
      SettingsSwitch("Restore Playback", Icons.Rounded.RestartAlt, restore) {
        vm.setRestoreState(it)
      }
    }

    SettingsSection("Last.fm") {
      SettingsSwitch("Scrobbling", Icons.Rounded.Radio, scrobble) { vm.setScrobbling(it) }
      SettingsItem("Account", Icons.Rounded.AccountCircle, subtitle = "Connect Last.fm") {
        showLastFmModal = true
      }
      SettingsItem("Image Download", Icons.Rounded.Image, subtitle = artPolicy) {
        val next =
                when (artPolicy) {
                  "NEVER" -> "WIFI"
                  "WIFI" -> "ALWAYS"
                  else -> "NEVER"
                }
        vm.setArtDownloadPolicy(next)
      }
    }

    SettingsSection("Library & UX") {
      SettingsSwitch("Search Bar at Bottom", Icons.Rounded.KeyboardArrowDown, searchBottom) {
        vm.setSearchBarAtBottom(it)
      }
      SettingsSwitch("Remember Last Tab", Icons.Rounded.Bookmark, rememberTab) {
        vm.setRememberLastTab(it)
      }
    }
  }

  if (showColorPicker) {
    ColorPickerSheet(
            currentColor = accentColor,
            presets = presetAccentColors,
            onColorSelected = {
              vm.setAccentColor(it)
              showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
    )
  }

  if (showLastFmModal) {
    LastFmLoginModal(onDismiss = { showLastFmModal = false })
  }
}

// ---- Reusable setting rows ----

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column {
    Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    content()
    HorizontalDivider(
            Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outline.copy(0.3f),
            thickness = 0.5.dp
    )
    Spacer(Modifier.height(4.dp))
  }
}

@Composable
fun SettingsSwitch(
        title: String,
        icon: ImageVector,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
) {
  ListItem(
          headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
          leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
          trailingContent = { Switch(checked = checked, onCheckedChange = onToggle) },
          colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
  )
}

@Composable
fun SettingsItem(title: String, icon: ImageVector, subtitle: String? = null, onClick: () -> Unit) {
  ListItem(
          headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
          supportingContent =
                  subtitle?.let {
                    {
                      Text(
                              it,
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  },
          leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
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

// ---- Color Picker Bottom Sheet ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
        currentColor: Int,
        presets: List<Int>,
        onColorSelected: (Int) -> Unit,
        onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState()
  ModalBottomSheet(
          onDismissRequest = onDismiss,
          sheetState = sheetState,
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Column(Modifier.navigationBarsPadding().padding(24.dp)) {
      Text("Accent Color", style = MaterialTheme.typography.titleLarge)
      Spacer(Modifier.height(20.dp))
      androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
              columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.wrapContentHeight(),
      ) {
        items(presets.size) { i ->
          val color = presets[i]
          val selected = color == currentColor
          Surface(
                  onClick = { onColorSelected(color) },
                  shape = MaterialTheme.shapes.large,
                  color = Color(0xFF000000.or(color.toLong())),
                  modifier = Modifier.size(64.dp),
                  border =
                          if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                          else null,
          ) {
            if (selected) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Rounded.Check, null, tint = Color.White)
              }
            }
          }
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

// ---- Last.fm Login Modal ----

@Composable
fun LastFmLoginModal(onDismiss: () -> Unit) {
  var user by remember { mutableStateOf("") }
  var pass by remember { mutableStateOf("") }
  AlertDialog(
          onDismissRequest = onDismiss,
          title = { Text("Last.fm Account") },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              OutlinedTextField(
                      value = user,
                      onValueChange = { user = it },
                      label = { Text("Username") },
                      singleLine = true,
                      modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                      value = pass,
                      onValueChange = { pass = it },
                      label = { Text("Password") },
                      singleLine = true,
                      visualTransformation =
                              androidx.compose.ui.text.input.PasswordVisualTransformation(),
                      modifier = Modifier.fillMaxWidth(),
              )
            }
          },
          confirmButton = {
            TextButton(
                    onClick = { /* TODO: authenticate */
                      onDismiss()
                    }
            ) { Text("Connect") }
          },
          dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun BorderStroke(
        width: androidx.compose.ui.unit.Dp,
        color: Color
): androidx.compose.foundation.BorderStroke = androidx.compose.foundation.BorderStroke(width, color)
