package com.source.player.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.source.player.ui.components.BrowserEmptyState
import com.source.player.ui.components.ChooseMusicFolderState
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText

/** What the Library shows instead of its lists when there is nothing to list. */
enum class LibraryEmptyState {
    /** Mode or track count not read yet: blank body, no flash of the wrong state. */
    Loading,
    /** Stream without a music folder. */
    ChooseMusicFolder,
    /** Local without the audio permission. */
    AudioPermission,
    /** Local, no tracks, scan running. */
    Scanning,
    /** Local, no tracks, scanner idle. */
    NoLocalTracks,
    /** Stream, no tracks, indexing running. */
    Indexing,
    /** Stream, no tracks, indexer idle. */
    NoStreamTracks,
}

/** Runtime audio permission (READ_MEDIA_AUDIO on 13+, storage before), re-read on resume. */
@Stable
class AudioPermissionState internal constructor(granted: Boolean) {
    var granted by mutableStateOf(granted)
        internal set

    /** True once a request came back denied: Android may no longer show the dialog. */
    var denied by mutableStateOf(false)
        internal set

    internal var launch: () -> Unit = {}

    fun request() = launch()
}

private val audioPermission: String
    get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

private fun Context.hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission) ==
                PackageManager.PERMISSION_GRANTED

/**
 * Audio permission as Compose state. [onGranted] runs when a request from this screen
 * is accepted (the caller starts a scan). Re-checked on every resume, so a grant made in
 * the system settings shows up when the user comes back.
 */
@Composable
fun rememberAudioPermissionState(onGranted: () -> Unit): AudioPermissionState {
    val context = LocalContext.current
    val state = remember { AudioPermissionState(context.hasAudioPermission()) }
    val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                state.granted = ok
                state.denied = !ok
                if (ok) onGranted()
            }
    state.launch = { launcher.launch(audioPermission) }
    LifecycleResumeEffect(state) {
        state.granted = context.hasAudioPermission()
        onPauseOrDispose {}
    }
    return state
}

/** Body of the Library when [state] says there is nothing to list. */
@Composable
fun LibraryEmptyBody(
        state: LibraryEmptyState,
        permission: AudioPermissionState,
        scanProgress: String?,
        indexingProgress: String?,
        onChooseFolder: () -> Unit,
        onScan: () -> Unit,
        onBrowseFolder: () -> Unit,
) {
    val context = LocalContext.current
    when (state) {
        LibraryEmptyState.Loading -> Unit
        LibraryEmptyState.ChooseMusicFolder ->
                ChooseMusicFolderState(onChoose = onChooseFolder)
        LibraryEmptyState.AudioPermission ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BrowserEmptyState(
                            icon = HugeIcons.Lock,
                            title = "Allow access to your music",
                            body = "Source needs permission to read your audio files.",
                            action = "Grant permission",
                            onAction = { permission.request() },
                    )
                    // Denied twice, Android stops showing the dialog: the system
                    // settings page is then the only way to grant it.
                    if (permission.denied) {
                        TextButton(onClick = { context.openAppSettings() }) {
                            Text(
                                    "Open app settings",
                                    style = MaterialTheme.sourceText.pillLabel12,
                                    color = MaterialTheme.sourceColors.accent,
                            )
                        }
                    }
                }
        LibraryEmptyState.Scanning ->
                ProgressEmptyState(HugeIcons.MusicNote, "Scanning your music", scanProgress)
        LibraryEmptyState.NoLocalTracks ->
                BrowserEmptyState(
                        icon = HugeIcons.MusicNote,
                        title = "No tracks yet",
                        body = "Scan this device to add the audio files it holds.",
                        action = "Scan library",
                        onAction = onScan,
                )
        LibraryEmptyState.Indexing ->
                ProgressEmptyState(HugeIcons.Cloud, "Indexing your music folder", indexingProgress)
        LibraryEmptyState.NoStreamTracks ->
                BrowserEmptyState(
                        icon = HugeIcons.Cloud,
                        title = "No tracks yet",
                        body = "Tracks from your music folder show up here once indexed.",
                        action = "Browse your music folder",
                        onAction = onBrowseFolder,
                )
    }
}

/** [BrowserEmptyState] layout with a spinner and a live progress line instead of a button. */
@Composable
private fun ProgressEmptyState(icon: ImageVector, title: String, progress: String?) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, modifier = Modifier.size(48.dp), tint = colors.textMute)
        Spacer(Modifier.height(12.dp))
        Text(
                title,
                style = text.editorialHeadline32,
                color = colors.textDim,
                textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = colors.accent)
        if (progress != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                    progress,
                    style = text.metaSmall115,
                    color = colors.textMute,
                    textAlign = TextAlign.Center,
            )
        }
    }
}

private fun Context.openAppSettings() {
    startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
