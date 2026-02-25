package com.source.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
import com.source.player.ui.theme.AppFont
import com.source.player.ui.viewmodel.LastFmLoginState
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
  val fontFamilyName by vm.fontFamily.collectAsState()
  val lastFmUser by vm.lastFmUser.collectAsState()
  val loginState by vm.loginState.collectAsState()
  val currentAppFont =
          AppFont.entries.firstOrNull { it.name == fontFamilyName } ?: AppFont.PlusJakartaSans

  var showLastFmModal by remember { mutableStateOf(false) }
  var showColorPicker by remember { mutableStateOf(false) }
  var showFontPicker by remember { mutableStateOf(false) }

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
      SettingsItem(
              "Font Style",
              Icons.Rounded.TextFields,
              subtitle = currentAppFont.label,
      ) { showFontPicker = true }
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
      SettingsItem(
              "Account",
              Icons.Rounded.AccountCircle,
              subtitle = if (!lastFmUser.isNullOrBlank()) "@$lastFmUser" else "Connect Last.fm",
      ) { showLastFmModal = true }
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
    LastFmLoginModal(
            connectedUser = lastFmUser,
            loginState = loginState,
            onLogin = { u, p -> vm.loginLastFm(u, p) },
            onLogout = {
              vm.logoutLastFm()
              showLastFmModal = false
            },
            onDismiss = {
              showLastFmModal = false
              vm.resetLoginState()
            },
    )
  }

  if (showFontPicker) {
    FontPickerSheet(
            currentFont = currentAppFont,
            onFontSelected = {
              vm.setFontFamily(it.name)
              showFontPicker = false
            },
            onDismiss = { showFontPicker = false },
    )
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
fun LastFmLoginModal(
        connectedUser: String?,
        loginState: LastFmLoginState,
        onLogin: (String, String) -> Unit,
        onLogout: () -> Unit,
        onDismiss: () -> Unit,
) {
  // Auto-dismiss on success
  LaunchedEffect(loginState) { if (loginState is LastFmLoginState.Success) onDismiss() }

  var user by remember { mutableStateOf("") }
  var pass by remember { mutableStateOf("") }
  val isLoading = loginState is LastFmLoginState.Loading
  val errorMsg = (loginState as? LastFmLoginState.Error)?.message
  val isConnected = !connectedUser.isNullOrBlank()

  AlertDialog(
          onDismissRequest = { if (!isLoading) onDismiss() },
          title = { Text("Last.fm Account") },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              if (isConnected) {
                // Connected state
                Text(
                        "Connected as @$connectedUser",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                )
                Text(
                        "Tap Disconnect to remove your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                // Login form
                OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !isLoading,
                        visualTransformation =
                                androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                )
                if (errorMsg != null) {
                  Text(
                          errorMsg,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.error,
                  )
                }
                if (isLoading) {
                  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                  }
                }
              }
            }
          },
          confirmButton = {
            if (isConnected) {
              TextButton(onClick = onLogout) {
                Text("Disconnect", color = MaterialTheme.colorScheme.error)
              }
            } else {
              TextButton(onClick = { onLogin(user, pass) }, enabled = !isLoading) {
                Text("Connect")
              }
            }
          },
          dismissButton = {
            TextButton(onClick = { if (!isLoading) onDismiss() }) { Text("Cancel") }
          },
  )
}

@Composable
private fun BorderStroke(
        width: androidx.compose.ui.unit.Dp,
        color: Color
): androidx.compose.foundation.BorderStroke = androidx.compose.foundation.BorderStroke(width, color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontPickerSheet(
        currentFont: AppFont,
        onFontSelected: (AppFont) -> Unit,
        onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
    ) {
      Text(
              "Font Style",
              style = MaterialTheme.typography.titleLarge,
              modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
      )
      Text(
              "Choose your preferred typeface",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
      )
      HorizontalDivider()
      AppFont.entries.forEach { font ->
        val isSelected = font == currentFont
        ListItem(
                headlineContent = {
                  Text(
                          font.label,
                          style = MaterialTheme.typography.bodyLarge,
                  )
                },
                leadingContent = {
                  Text(
                          "Aa",
                          style = MaterialTheme.typography.titleMedium,
                          color =
                                  if (isSelected) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                },
                trailingContent = {
                  if (isSelected) {
                    Icon(
                            Icons.Rounded.Check,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                    )
                  }
                },
                modifier = Modifier.clickable { onFontSelected(font) },
                colors =
                        ListItemDefaults.colors(
                                containerColor =
                                        if (isSelected)
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.3f
                                                )
                                        else MaterialTheme.colorScheme.surface,
                        ),
        )
      }
    }
  }
}
