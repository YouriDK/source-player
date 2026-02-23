package com.source.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.source.player.ui.navigation.SourceNavHost
import com.source.player.ui.theme.SourceTheme
import com.source.player.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  /**
   * The correct permission to request at runtime:
   * - API 33+ (Android 13+): READ_MEDIA_AUDIO
   * - API 26-32 : READ_EXTERNAL_STORAGE
   *
   * We register the launcher unconditionally — it's a no-op if permission is already granted. The
   * launcher is created before onCreate per Activity Result API contract.
   */
  private val audioPermission =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
          } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
          }

  private val permissionLauncher =
          registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Result handled reactively — HomeScreen observes checkSelfPermission() via ViewModel
          }

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Request on first launch — no-op if already granted
    permissionLauncher.launch(audioPermission)

    setContent {
      val settingsVm: SettingsViewModel = hiltViewModel()
      val isDark by settingsVm.isDarkMode.collectAsState()
      val accentInt by settingsVm.accentColor.collectAsState()
      SourceTheme(
              darkTheme = isDark,
              accentColor = Color(0xFF000000.or(accentInt.toLong())),
      ) { SourceNavHost() }
    }
  }
}
