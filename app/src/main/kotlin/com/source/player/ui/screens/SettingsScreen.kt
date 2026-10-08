package com.source.player.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.source.player.BuildConfig
import com.source.player.data.freebox.ConnectionMode
import com.source.player.data.freebox.FreeboxFolder
import com.source.player.service.AudioOutputDevice
import com.source.player.service.DeviceCategory
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.theme.accentForHue
import com.source.player.ui.navigation.Routes
import com.source.player.ui.viewmodel.AudioOutputViewModel
import com.source.player.ui.viewmodel.FreeboxPairingState
import com.source.player.ui.viewmodel.FreeboxStatusUi
import com.source.player.ui.viewmodel.LastFmLoginState
import com.source.player.ui.viewmodel.SettingsViewModel

/** Vinyl hue presets: Amber / Rust / Terracotta / Moss / Slate Blue / Plum / Rose. */
private data class HuePreset(val label: String, val hue: Float)

private val VinylHuePresets =
        listOf(
                HuePreset("Amber", 60f),
                HuePreset("Rust", 30f),
                HuePreset("Terracotta", 15f),
                HuePreset("Moss", 140f),
                HuePreset("Slate Blue", 220f),
                HuePreset("Plum", 290f),
                HuePreset("Rose", 350f),
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
  val accentHue by vm.accentHue.collectAsState()
  val lastFmUser by vm.lastFmUser.collectAsState()
  val loginState by vm.loginState.collectAsState()
  val scanProgress by vm.scanProgress.collectAsState()
  val freeboxPaired by vm.freeboxPaired.collectAsState()
  val freeboxBoxName by vm.freeboxBoxName.collectAsState()
  val freeboxStartFolder by vm.freeboxStartFolder.collectAsState()
  val freeboxPairing by vm.freeboxPairing.collectAsState()
  val freeboxStatus by vm.freeboxStatus.collectAsState()

  val audioDevices by audioVm.availableDevices.collectAsState()
  val activeAudioDevice by audioVm.activeDevice.collectAsState()
  val isScanning by audioVm.isScanning.collectAsState()

  var showLastFmModal by remember { mutableStateOf(false) }
  var showColorPicker by remember { mutableStateOf(false) }
  var showAudioOutput by remember { mutableStateOf(false) }
  var showFreeboxDialog by remember { mutableStateOf(false) }

  // One background check per visit so the Freebox row can say Connected / Unreachable.
  LaunchedEffect(freeboxPaired) {
    if (freeboxPaired && vm.freeboxStatus.value is FreeboxStatusUi.Unknown) vm.checkFreebox()
  }

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
      SettingsSwitch("Dark Mode", HugeIcons.DarkMode, isDark) { vm.setDarkMode(it) }
      SettingsItem(
              "Accent Color",
              HugeIcons.Palette,
              subtitle =
                      VinylHuePresets.firstOrNull { kotlin.math.abs(it.hue - accentHue) < 0.5f }
                              ?.label
                              ?: "Custom hue ${accentHue.toInt()}\u00B0",
      ) { showColorPicker = true }
    }

    SettingsSection("Playback") {
      SettingsSwitch("Gapless Playback", HugeIcons.Equalizer, gapless) { vm.setGapless(it) }
      SettingsSwitch("Audio Ducking", HugeIcons.VolumeLow, ducking) { vm.setAudioDucking(it) }
      SettingsSwitch("Restore Playback", HugeIcons.Restart, restore) {
        vm.setRestoreState(it)
      }
      SettingsItem(
              "Audio Output",
              HugeIcons.Speaker,
              subtitle = activeAudioDevice?.name ?: "System Default",
      ) { showAudioOutput = true }
    }

    SettingsSection("Last.fm") {
      SettingsSwitch("Scrobbling", HugeIcons.Radio, scrobble) { vm.setScrobbling(it) }
      SettingsItem(
              "Account",
              HugeIcons.Account,
              subtitle = if (!lastFmUser.isNullOrBlank()) "@$lastFmUser" else "Connect Last.fm",
      ) { showLastFmModal = true }
      SettingsItem("Image Download", HugeIcons.Image, subtitle = artPolicy) {
        val next =
                when (artPolicy) {
                  "NEVER" -> "WIFI"
                  "WIFI" -> "ALWAYS"
                  else -> "NEVER"
                }
        vm.setArtDownloadPolicy(next)
      }
    }

    SettingsSection("Freebox") {
      SettingsItem(
              "Freebox",
              HugeIcons.Cloud,
              subtitle =
                      if (!freeboxPaired) "Pair with your Freebox"
                      else {
                        val name = freeboxBoxName ?: "Freebox"
                        when (val st = freeboxStatus) {
                          is FreeboxStatusUi.Ok -> "$name \u00B7 Connected (${st.status.mode.label()})"
                          FreeboxStatusUi.Checking -> "$name \u00B7 Checking\u2026"
                          is FreeboxStatusUi.Failed -> "$name \u00B7 Unreachable"
                          FreeboxStatusUi.Unknown -> name
                        }
                      },
      ) { showFreeboxDialog = true }
    }

    SettingsSection("Library & UX") {
      SettingsSwitch("Remember Last Tab", HugeIcons.Bookmark, rememberTab) {
        vm.setRememberLastTab(it)
      }
      SettingsItem(
              "Scan Library",
              HugeIcons.Refresh,
              subtitle = scanProgress ?: "Tap to rescan your music library",
      ) { if (scanProgress == null) vm.scanLibrary() }
    }

    Spacer(Modifier.height(24.dp))
    Text(
            // The SHA is developer-facing noise on a shipped build; release
            // shows clean semver, debug carries the commit it was cut from.
            text =
                    "Source \u2014 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                            if (BuildConfig.DEBUG) " \u00B7 ${BuildConfig.GIT_SHA}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    // Mini player floats over the bottom of the content area — same 96.dp
    // clearance the Library / Folders / Search lists reserve for it.
    Spacer(Modifier.height(96.dp))
  }

  if (showColorPicker) {
    AccentHueSheet(
            currentHue = accentHue,
            onHueChanged = { vm.setAccentHue(it) },
            onDismiss = { showColorPicker = false },
    )
  }

  if (showAudioOutput) {
    LaunchedEffect(Unit) { audioVm.scanAll() }
    // Active route discovery (Cast/mDNS scanning) only while the picker is open.
    DisposableEffect(Unit) {
      audioVm.setActiveDiscovery(true)
      onDispose { audioVm.setActiveDiscovery(false) }
    }
    AudioOutputSheet(
            devices = audioDevices,
            activeDevice = activeAudioDevice,
            isScanning = isScanning,
            onScanClicked = { audioVm.scanAll() },
            onDeviceSelected = { audioVm.selectDevice(it) },
            onDismiss = { showAudioOutput = false },
    )
  }

  if (showFreeboxDialog) {
    FreeboxDialog(
            paired = freeboxPaired,
            boxName = freeboxBoxName,
            startFolder = freeboxStartFolder,
            pairing = freeboxPairing,
            status = freeboxStatus,
            onPair = { vm.startPairing(it) },
            onCancelPairing = { vm.cancelPairing() },
            onCheck = { vm.checkFreebox() },
            onOpenBrowser = {
              showFreeboxDialog = false
              vm.resetFreeboxPairingState()
              navController.navigate(Routes.FREEBOX) { launchSingleTop = true }
            },
            onUnpair = { vm.unpairFreebox() },
            onDismiss = {
              showFreeboxDialog = false
              vm.resetFreeboxPairingState()
            },
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
          leadingContent = { Icon(icon, title, tint = MaterialTheme.colorScheme.primary) },
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
          leadingContent = { Icon(icon, title, tint = MaterialTheme.colorScheme.primary) },
          trailingContent = {
            Icon(
                    HugeIcons.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          },
          modifier = Modifier.clickable(onClick = onClick),
          colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
  )
}

// ---- Accent Hue Bottom Sheet (Vinyl) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccentHueSheet(
        currentHue: Float,
        onHueChanged: (Float) -> Unit,
        onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState()
  ModalBottomSheet(
          onDismissRequest = onDismiss,
          sheetState = sheetState,
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
      Text("Accent", style = MaterialTheme.typography.displayMedium)
      Spacer(Modifier.height(6.dp))
      Text(
              "Hue rotates — chroma and lightness are fixed.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(20.dp))

      // Preset row
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VinylHuePresets.forEach { preset ->
          val selected = kotlin.math.abs(preset.hue - currentHue) < 0.5f
          Box(
                  modifier =
                          Modifier.size(40.dp)
                                  .clip(CircleShape)
                                  .background(accentForHue(preset.hue))
                                  .clickable { onHueChanged(preset.hue) }
                                  .then(
                                          if (selected)
                                                  Modifier.border(
                                                          BorderStroke(
                                                                  2.dp,
                                                                  MaterialTheme.colorScheme
                                                                          .onSurface,
                                                          ),
                                                          CircleShape,
                                                  )
                                          else Modifier
                                  ),
                  contentAlignment = Alignment.Center,
          ) {
            if (selected) {
              Icon(
                      HugeIcons.Check,
                      null,
                      tint = Color.White,
                      modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
      Spacer(Modifier.height(20.dp))

      // Free hue slider
      Text(
              "Custom — ${currentHue.toInt()}\u00B0",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Slider(
              value = currentHue,
              onValueChange = onHueChanged,
              valueRange = 0f..360f,
              colors =
                      SliderDefaults.colors(
                              thumbColor = accentForHue(currentHue),
                              activeTrackColor = accentForHue(currentHue),
                      ),
      )
      Spacer(Modifier.height(12.dp))
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

// ---- Freebox Dialog ----

private fun ConnectionMode.label(): String =
        when (this) {
          ConnectionMode.LAN -> "LAN"
          ConnectionMode.REMOTE -> "Remote"
        }

@Composable
fun FreeboxDialog(
        paired: Boolean,
        boxName: String?,
        startFolder: FreeboxFolder?,
        pairing: FreeboxPairingState,
        status: FreeboxStatusUi,
        onPair: (String?) -> Unit,
        onCancelPairing: () -> Unit,
        onCheck: () -> Unit,
        onOpenBrowser: () -> Unit,
        onUnpair: () -> Unit,
        onDismiss: () -> Unit,
) {
  val isPairing = pairing is FreeboxPairingState.Pairing
  var advanced by remember { mutableStateOf(false) }
  var host by remember { mutableStateOf("") }
  var confirmUnpair by remember { mutableStateOf(false) }

  // Check once each time the dialog opens on a paired box (and right after pairing).
  LaunchedEffect(paired) { if (paired) onCheck() }

  AlertDialog(
          onDismissRequest = { if (!isPairing) onDismiss() },
          title = { Text("Freebox") },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              when {
                isPairing -> {
                  Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                            (pairing as FreeboxPairingState.Pairing).step,
                            style = MaterialTheme.typography.bodyMedium,
                    )
                  }
                }
                !paired -> {
                  Text(
                          "Pairing must be done at home on your Wi-Fi. When you tap Pair, " +
                                  "confirm on the Freebox's screen. Then allow file access for " +
                                  "Source in Freebox OS \u2192 Settings \u2192 Access management " +
                                  "\u2192 Applications.",
                          style = MaterialTheme.typography.bodyMedium,
                  )
                  Row(
                          modifier = Modifier.fillMaxWidth().clickable { advanced = !advanced },
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                            "Advanced",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                    )
                    Switch(checked = advanced, onCheckedChange = { advanced = it })
                  }
                  if (advanced) {
                    OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host") },
                            placeholder = { Text("mafreebox.freebox.fr") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                    )
                  }
                  (pairing as? FreeboxPairingState.Error)?.let {
                    Text(
                            it.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                    )
                  }
                }
                confirmUnpair -> {
                  Text(
                          "Unpair this Freebox? Source will lose access to its disks until " +
                                  "you pair it again.",
                          style = MaterialTheme.typography.bodyMedium,
                  )
                }
                else -> {
                  val name = boxName ?: "Freebox"
                  val statusLine =
                          when (status) {
                            is FreeboxStatusUi.Ok ->
                                    buildString {
                                      append(status.status.boxName ?: name)
                                      append(" \u00B7 Connected (${status.status.mode.label()})")
                                      status.status.latencyMs?.let { append(" \u00B7 $it ms") }
                                    }
                            FreeboxStatusUi.Checking -> "$name \u00B7 Checking\u2026"
                            is FreeboxStatusUi.Failed -> "$name \u00B7 ${status.message}"
                            FreeboxStatusUi.Unknown -> name
                          }
                  Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    if (status is FreeboxStatusUi.Checking) {
                      CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    Text(
                            statusLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                    if (status is FreeboxStatusUi.Failed)
                                            MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                    )
                  }
                  Text(
                          "Start folder: ${startFolder?.displayPath?.takeIf { it.isNotEmpty() } ?: "Disks root"}",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                            onClick = onCheck,
                            enabled = status !is FreeboxStatusUi.Checking,
                    ) { Text("Check connection") }
                    OutlinedButton(onClick = onOpenBrowser) { Text("Open browser") }
                  }
                }
              }
            }
          },
          confirmButton = {
            when {
              isPairing -> TextButton(onClick = onCancelPairing) { Text("Cancel") }
              !paired ->
                      TextButton(onClick = { onPair(host.takeIf { advanced && it.isNotBlank() }) }) {
                        Text("Pair")
                      }
              confirmUnpair ->
                      TextButton(
                              onClick = {
                                confirmUnpair = false
                                onUnpair()
                              }
                      ) { Text("Unpair", color = MaterialTheme.colorScheme.error) }
              else -> TextButton(onClick = onDismiss) { Text("Close") }
            }
          },
          dismissButton = {
            when {
              isPairing -> Unit
              !paired -> TextButton(onClick = onDismiss) { Text("Cancel") }
              confirmUnpair -> TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") }
              else ->
                      TextButton(onClick = { confirmUnpair = true }) {
                        Text("Unpair", color = MaterialTheme.colorScheme.error)
                      }
            }
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
                    HugeIcons.Refresh,
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
                              contentDescription = device.name,
                              tint =
                                      if (isActive) MaterialTheme.colorScheme.primary
                                      else MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    },
                    trailingContent = {
                      if (isActive) {
                        Icon(
                                HugeIcons.Check,
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
          DeviceCategory.BUILTIN -> HugeIcons.Phone
          DeviceCategory.WIRED -> HugeIcons.Headphones
          DeviceCategory.BLUETOOTH -> HugeIcons.Bluetooth
          DeviceCategory.WIFI -> HugeIcons.Wifi
          DeviceCategory.USB -> HugeIcons.Usb
          DeviceCategory.HDMI -> HugeIcons.Tv
          DeviceCategory.OTHER -> HugeIcons.SpeakerGroup
        }
