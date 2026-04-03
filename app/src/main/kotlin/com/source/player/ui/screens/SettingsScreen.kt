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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.source.player.service.AudioOutputDevice
import com.source.player.service.DeviceCategory
import com.source.player.ui.viewmodel.AudioOutputViewModel
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
        audioVm: AudioOutputViewModel = hiltViewModel(),
) {
  val isDark by vm.isDarkMode.collectAsState()
  val gapless by vm.gapless.collectAsState()
  val ducking by vm.audioDucking.collectAsState()
  val restore by vm.restoreState.collectAsState()
  val scrobble by vm.scrobbling.collectAsState()
  val artPolicy by vm.artDownloadPolicy.collectAsState()
  val rememberTab by vm.rememberLastTab.collectAsState()
  val accentColor by vm.accentColor.collectAsState()
  val lastFmUser by vm.lastFmUser.collectAsState()
  val loginState by vm.loginState.collectAsState()
  val scanProgress by vm.scanProgress.collectAsState()

  val audioDevices by audioVm.availableDevices.collectAsState()
  val activeAudioDevice by audioVm.activeDevice.collectAsState()
  val isScanning by audioVm.isScanning.collectAsState()

  var showLastFmModal by remember { mutableStateOf(false) }
  var showColorPicker by remember { mutableStateOf(false) }
  var showAudioOutput by remember { mutableStateOf(false) }

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
      SettingsItem(
              "Audio Output",
              Icons.Rounded.Speaker,
              subtitle = activeAudioDevice?.name ?: "System Default",
      ) { showAudioOutput = true }
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
      SettingsSwitch("Remember Last Tab", Icons.Rounded.Bookmark, rememberTab) {
        vm.setRememberLastTab(it)
      }
      SettingsItem(
              "Scan Library",
              Icons.Rounded.Refresh,
              subtitle = scanProgress ?: "Tap to rescan your music library",
      ) { if (scanProgress == null) vm.scanLibrary() }
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

  if (showAudioOutput) {
    LaunchedEffect(Unit) { audioVm.scanAll() }
    AudioOutputSheet(
            devices = audioDevices,
            activeDevice = activeAudioDevice,
            isScanning = isScanning,
            onScanClicked = { audioVm.scanAll() },
            onDeviceSelected = { audioVm.selectDevice(it) },
            onDismiss = { showAudioOutput = false },
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
                        label = { Text("Last.fm Username (not email)") },
                        supportingText = { Text("Use your display name, not your email") },
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

// ---- Audio Output Bottom Sheet ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioOutputSheet(
        devices: List<AudioOutputDevice>,
        activeDevice: AudioOutputDevice?,
        isScanning: Boolean,
        onScanClicked: () -> Unit,
        onDeviceSelected: (AudioOutputDevice) -> Unit,
        onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState()
  ModalBottomSheet(
          onDismissRequest = onDismiss,
          sheetState = sheetState,
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Column(
            Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
    ) {
      // Header with scan button
      Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("Audio Output", style = MaterialTheme.typography.titleLarge)
          Spacer(Modifier.height(4.dp))
          Text(
                  if (isScanning) "Scanning for devices…"
                  else "Select where to play audio",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = onScanClicked, enabled = !isScanning) {
          if (isScanning) {
            CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
            )
          } else {
            Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "Scan for devices",
                    tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      Spacer(Modifier.height(16.dp))

      // Group devices by category
      val grouped = devices.groupBy { it.type }
      val categoryOrder = listOf(
              DeviceCategory.BUILTIN,
              DeviceCategory.WIRED,
              DeviceCategory.BLUETOOTH,
              DeviceCategory.WIFI,
              DeviceCategory.USB,
              DeviceCategory.HDMI,
              DeviceCategory.OTHER,
      )

      categoryOrder.forEach { category ->
        val group = grouped[category]
        if (!group.isNullOrEmpty()) {
          Text(
                  category.label,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(vertical = 8.dp),
          )
          group.forEach { device ->
            val isActive = device.id == activeDevice?.id
            ListItem(
                    headlineContent = {
                      Text(
                              device.name,
                              style = MaterialTheme.typography.bodyLarge,
                              fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                      )
                    },
                    supportingContent = {
                      Text(
                              deviceSubtitle(device),
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    },
                    leadingContent = {
                      Icon(
                              deviceIcon(device.type),
                              contentDescription = null,
                              tint =
                                      if (isActive) MaterialTheme.colorScheme.primary
                                      else MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    },
                    trailingContent = {
                      if (isActive) {
                        Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.primary,
                        )
                      }
                    },
                    modifier = Modifier.clickable {
                      onDeviceSelected(device)
                    },
                    colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                    ),
            )
          }
        }
      }

      if (devices.isEmpty() && !isScanning) {
        Box(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
        ) {
          Text(
                  "No audio devices found",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (devices.isEmpty() && isScanning) {
        Box(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(Modifier.size(32.dp))
        }
      }
    }
  }
}

/** Short description for each device based on connection type. */
private fun deviceSubtitle(device: AudioOutputDevice): String {
  if (device.routeId != null) {
    return when {
      device.routeId.startsWith("sonos:") -> "Sonos · UPnP"
      else -> "Network · MediaRouter"
    }
  }
  return when (device.type) {
    DeviceCategory.BUILTIN -> "Phone speaker"
    DeviceCategory.WIRED -> "Wired connection"
    DeviceCategory.BLUETOOTH -> "Bluetooth audio"
    DeviceCategory.WIFI -> "Wi-Fi"
    DeviceCategory.USB -> "USB audio"
    DeviceCategory.HDMI -> "HDMI output"
    DeviceCategory.OTHER -> "External device"
  }
}

@Composable
private fun deviceIcon(category: DeviceCategory): ImageVector =
        when (category) {
          DeviceCategory.BUILTIN -> Icons.Rounded.SpeakerPhone
          DeviceCategory.WIRED -> Icons.Rounded.Headphones
          DeviceCategory.BLUETOOTH -> Icons.Rounded.Bluetooth
          DeviceCategory.WIFI -> Icons.Rounded.Wifi
          DeviceCategory.USB -> Icons.Rounded.Usb
          DeviceCategory.HDMI -> Icons.Rounded.SettingsInputHdmi
          DeviceCategory.OTHER -> Icons.Rounded.SpeakerGroup
        }
