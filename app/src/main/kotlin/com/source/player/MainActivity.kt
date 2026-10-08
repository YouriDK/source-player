package com.source.player

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.source.player.data.scanner.MediaScanner
import com.source.player.ui.navigation.SourceNavHost
import com.source.player.ui.theme.SourceTheme
import com.source.player.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject lateinit var scanner: MediaScanner

  /**
   * Single launcher for all runtime permissions. The launcher is registered unconditionally
   * (before onCreate completes, per Activity Result API contract), but only launched when at
   * least one permission is actually missing — see [requestMissingPermissions].
   */
  private val permissionLauncher =
          registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // Results handled reactively — HomeScreen observes checkSelfPermission() via
            // ViewModel, and Media3 handles notification display regardless of the result.
          }

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    requestMissingPermissions()
    surfaceLastCrashLog()

    setContent {
      val settingsVm: SettingsViewModel = hiltViewModel()
      val isDark by settingsVm.isDarkMode.collectAsState()
      val accentHue by settingsVm.accentHue.collectAsState()
      SourceTheme(darkTheme = isDark, accentHue = accentHue) { SourceNavHost() }
    }
  }

  /**
   * Catches up on anything that changed while the app was away. [MediaScanner.scanIfStale]
   * compares MediaStore's version/generation against the stamp left by the last scan, so a
   * foreground with nothing new costs one cheap query and no DB writes — while files added
   * or re-tagged in another app show up without the user hitting Scan.
   *
   * Complements [com.source.player.data.scanner.MediaStoreWatcher], which covers changes
   * happening while the app is in the foreground.
   */
  override fun onStart() {
    super.onStart()
    if (!hasAudioPermission()) return
    lifecycleScope.launch { scanner.scanIfStale() }
  }

  private fun hasAudioPermission(): Boolean {
    val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              Manifest.permission.READ_MEDIA_AUDIO
            } else {
              Manifest.permission.READ_EXTERNAL_STORAGE
            }
    return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
  }

  /**
   * Requests only the runtime permissions that are not yet granted:
   * - API 33+ (Android 13+): READ_MEDIA_AUDIO + POST_NOTIFICATIONS
   * - API 26-32 : READ_EXTERNAL_STORAGE
   *
   * Launching only when something is missing avoids re-prompting on every activity recreation
   * (rotation, theme change, ...), which would burn Android 11+'s limited ask quota, and a
   * single multi-permission request avoids racing two dialogs.
   */
  private fun requestMissingPermissions() {
    val audioPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              Manifest.permission.READ_MEDIA_AUDIO
            } else {
              Manifest.permission.READ_EXTERNAL_STORAGE
            }

    val missing = buildList {
      add(audioPermission)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
    }.filter {
      ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    if (missing.isNotEmpty()) {
      permissionLauncher.launch(missing.toTypedArray())
    }
  }

  /**
   * If the previous session died with a crash (recorded by [SourceApplication]'s handler),
   * copy the stack trace to the clipboard so it can be pasted into a bug report, then clear
   * the record so it only surfaces once.
   */
  private fun surfaceLastCrashLog() {
    val crashFile = File(filesDir, SourceApplication.CRASH_FILE)
    if (!crashFile.exists()) return
    val trace = runCatching { crashFile.readText() }.getOrNull()
    crashFile.delete()
    if (trace.isNullOrBlank()) return
    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Source crash log", trace))
    Toast.makeText(
                    this,
                    "Crash log from previous session copied to clipboard",
                    Toast.LENGTH_LONG,
            )
            .show()
  }
}
