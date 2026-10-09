package com.source.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.source.player.data.mode.StreamBanner
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.navigation.Routes
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText

/**
 * Stream-mode status strip shown on top of Library, Home and Folders: box unreachable,
 * pairing lost, music folder missing, or indexing in progress. Renders nothing for null.
 */
@Composable
fun StreamStatusBanner(
        banner: StreamBanner?,
        onRepair: () -> Unit,
        onChooseFolder: () -> Unit,
        modifier: Modifier = Modifier,
) {
    if (banner == null) return
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    val problem = banner !is StreamBanner.Indexing
    val label =
            when (banner) {
                StreamBanner.Unreachable -> "Freebox unreachable"
                StreamBanner.PairingLost -> "Pairing lost"
                StreamBanner.MusicFolderMissing -> "Music folder not found"
                is StreamBanner.Indexing -> banner.message
            }
    val action: Pair<String, () -> Unit>? =
            when (banner) {
                StreamBanner.PairingLost -> "Re-pair" to onRepair
                StreamBanner.MusicFolderMissing -> "Choose your music folder" to onChooseFolder
                else -> null
            }
    val shape = RoundedCornerShape(4.dp)
    Row(
            modifier =
                    modifier.fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                            .border(1.dp, if (problem) colors.error.copy(alpha = 0.5f) else colors.hair, shape)
                            .background(colors.surface, shape)
                            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                            .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (problem) {
            Icon(
                    if (banner == StreamBanner.MusicFolderMissing) HugeIcons.FolderOff
                    else HugeIcons.Cloud,
                    null,
                    tint = colors.error,
                    modifier = Modifier.size(18.dp),
            )
        } else {
            CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.accent,
            )
        }
        Text(
                label,
                style = text.bodyMedium14,
                color = colors.textDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = action.second) {
                Text(action.first, style = text.pillLabel12, color = colors.accent)
            }
        }
    }
}

/** [StreamStatusBanner] wired to the app's navigation (Settings / music folder picker). */
@Composable
fun StreamStatusBanner(
        banner: StreamBanner?,
        navController: NavController,
        modifier: Modifier = Modifier,
) {
    StreamStatusBanner(
            banner = banner,
            onRepair = { navController.openSettingsTab() },
            onChooseFolder = { navController.openMusicFolderPicker() },
            modifier = modifier,
    )
}

/** Centered icon + headline + body + optional pill action — the browsers' empty state. */
@Composable
fun BrowserEmptyState(
        icon: ImageVector,
        title: String,
        body: String?,
        action: String? = null,
        onAction: () -> Unit = {},
        modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.sourceColors
    val text = MaterialTheme.sourceText
    Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 60.dp),
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
        if (body != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                    body,
                    style = text.metaSmall115,
                    color = colors.textMute,
                    textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(100.dp)) {
                Text(action, style = text.pillLabel12)
            }
        }
    }
}

/** Stream mode without a music folder yet (Library, Folders). */
@Composable
fun ChooseMusicFolderState(onChoose: () -> Unit, modifier: Modifier = Modifier) {
    BrowserEmptyState(
            icon = HugeIcons.Cloud,
            title = "Choose your music folder",
            body = "Stream mode plays the music folder of your Freebox. Pick it once, change it any time in Settings.",
            action = "Choose your music folder",
            onAction = onChoose,
            modifier = modifier,
    )
}

/** Same navigation as the bottom bar's Settings tab. */
fun NavController.openSettingsTab() {
    navigate(Routes.SETTINGS) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

fun NavController.openMusicFolderPicker() {
    navigate(Routes.FREEBOX_PICKER) { launchSingleTop = true }
}
