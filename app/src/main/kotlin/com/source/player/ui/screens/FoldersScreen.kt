package com.source.player.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.source.player.data.db.entity.SongEntity
import com.source.player.ui.components.BreadcrumbChip
import com.source.player.ui.components.BrowserFolderRow
import com.source.player.ui.components.BrowserTrackRow
import com.source.player.ui.components.FolderPickerSheet
import com.source.player.ui.components.SelectionActionBar
import com.source.player.ui.components.Hairline
import com.source.player.ui.components.MonoSize
import com.source.player.ui.components.MonoText
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText
import com.source.player.ui.components.ChooseMusicFolderState
import com.source.player.ui.components.StreamStatusBanner
import com.source.player.ui.components.openMusicFolderPicker
import com.source.player.data.mode.StreamBanner
import com.source.player.ui.viewmodel.FoldersViewModel
import com.source.player.ui.viewmodel.LibraryModeViewModel

/**
 * The Folders tab follows the library mode: on-device folders in Local, the Freebox
 * music folder (live browser rooted there) in Stream.
 */
@Composable
fun FoldersTab(
        navController: NavController,
        modeVm: LibraryModeViewModel = hiltViewModel(),
) {
        val status by modeVm.status.collectAsStateWithLifecycle()
        val st = status
        when {
                st == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.sourceColors.bg))
                !st.isStream -> FoldersScreen(navController)
                st.musicFolder == null ->
                        Box(Modifier.fillMaxSize().systemBarsPadding().background(MaterialTheme.sourceColors.bg)) {
                                Column(Modifier.fillMaxSize()) {
                                        Text(
                                                text = "Folders",
                                                style = MaterialTheme.sourceText.editorialHeadline32,
                                                color = MaterialTheme.sourceColors.text,
                                                modifier =
                                                        Modifier.padding(
                                                                start = 24.dp,
                                                                end = 24.dp,
                                                                top = 14.dp,
                                                                bottom = 8.dp,
                                                        ),
                                        )
                                        StreamStatusBanner(st.banner, navController)
                                        ChooseMusicFolderState(
                                                onChoose = { navController.openMusicFolderPicker() }
                                        )
                                }
                        }
                else ->
                        FreeboxScreen(
                                navController,
                                banner = {
                                        // The body already says so (with the same button).
                                        StreamStatusBanner(
                                                st.banner.takeUnless {
                                                        it == StreamBanner.MusicFolderMissing
                                                },
                                                navController,
                                        )
                                },
                        )
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
        navController: NavController,
        vm: FoldersViewModel = hiltViewModel(),
) {
        val breadcrumbs by vm.breadcrumbs.collectAsStateWithLifecycle()
        val subFolders by vm.subFolders.collectAsStateWithLifecycle()
        val songsInFolder by vm.songsInFolder.collectAsStateWithLifecycle()
        val totalSongs by vm.totalSongsCount.collectAsStateWithLifecycle()
        val currentPath by vm.currentPath.collectAsStateWithLifecycle()
        val playlists by vm.playlists.collectAsStateWithLifecycle()
        val selection by vm.selection.collectAsStateWithLifecycle()
        val busy by vm.busy.collectAsStateWithLifecycle()
        val picker by vm.picker.collectAsStateWithLifecycle()
        val selecting = selection.isNotEmpty()

        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

        // System trash / write confirmation (API 30+): one dialog for the whole batch.
        val consentLauncher =
                rememberLauncherForActivityResult(
                        ActivityResultContracts.StartIntentSenderForResult()
                ) { result -> vm.onConsentResult(result.resultCode == Activity.RESULT_OK) }
        LaunchedEffect(Unit) {
                vm.consentRequests.collect { sender ->
                        consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
        }

        var showPlaylistSheet by remember { mutableStateOf(false) }
        var showNewPlaylistDialog by remember { mutableStateOf(false) }
        var newPlaylistName by remember { mutableStateOf("") }

        val colors = MaterialTheme.sourceColors
        val text = MaterialTheme.sourceText

        BackHandler(enabled = breadcrumbs.size > 1) { vm.popBack() }
        // Declared last so it wins: Back first leaves selection mode.
        BackHandler(enabled = selecting) { vm.clearSelection() }

        Box(Modifier.fillMaxSize().systemBarsPadding().background(colors.bg)) {
                Column(Modifier.fillMaxSize()) {
                        // Header ------------------------------------------------
                        if (selecting) {
                                SelectionActionBar(
                                        count = selection.size,
                                        busy = busy,
                                        onMove = { vm.openMovePicker() },
                                        onDelete = { vm.deleteSelected() },
                                        onCancel = { vm.clearSelection() },
                                )
                        } else Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(
                                                        start = 24.dp,
                                                        end = 24.dp,
                                                        top = 14.dp,
                                                        bottom = 8.dp
                                                ),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                        if (breadcrumbs.size > 1) {
                                                IconButton(
                                                        onClick = { vm.popBack() },
                                                        modifier = Modifier.size(32.dp),
                                                ) {
                                                        Icon(
                                                                HugeIcons.ArrowLeft,
                                                                "Back",
                                                                tint = colors.text,
                                                        )
                                                }
                                        }
                                        Text(
                                                text = "Folders",
                                                style = text.editorialHeadline32,
                                                color = colors.text,
                                        )
                                }
                                if (totalSongs > 0) {
                                        MonoText(
                                                text = "$totalSongs",
                                                color = colors.textMute,
                                                size = MonoSize.S10,
                                        )
                                }
                        }

                        // Breadcrumb trail --------------------------------------
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                                breadcrumbs.forEachIndexed { index, (label, _) ->
                                        val isLast = index == breadcrumbs.lastIndex
                                        BreadcrumbChip(
                                                label = label,
                                                selected = isLast,
                                                leading = if (index == 0) HugeIcons.Folder else null,
                                                onClick = {
                                                        if (!isLast) vm.navigateToBreadcrumb(index)
                                                },
                                        )
                                        if (!isLast) {
                                                Icon(
                                                        HugeIcons.ChevronRight,
                                                        null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = colors.textMute,
                                                )
                                        }
                                }
                        }

                        Hairline(modifier = Modifier.padding(horizontal = 24.dp))

                        // Body --------------------------------------------------
                        LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                                if (currentPath != null && totalSongs > 0 && !selecting) {
                                        item {
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(
                                                                                horizontal = 24.dp,
                                                                                vertical = 14.dp,
                                                                        ),
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                ) {
                                                        Button(
                                                                onClick = { vm.playAll() },
                                                                shape = RoundedCornerShape(100.dp),
                                                                modifier = Modifier.weight(1f),
                                                        ) {
                                                                Text(
                                                                        "Play all",
                                                                        style = text.pillLabel12,
                                                                )
                                                        }
                                                        FilledTonalButton(
                                                                onClick = { vm.shuffleAll() },
                                                                shape = RoundedCornerShape(100.dp),
                                                                modifier = Modifier.weight(1f),
                                                        ) {
                                                                Text(
                                                                        "Shuffle",
                                                                        style = text.pillLabel12,
                                                                )
                                                        }
                                                        IconButton(
                                                                onClick = {
                                                                        showPlaylistSheet = true
                                                                }
                                                        ) {
                                                                Icon(
                                                                        HugeIcons.PlaylistAdd,
                                                                        "Add Folder to Playlist",
                                                                        tint = colors.textDim,
                                                                )
                                                        }
                                                }
                                        }
                                }

                                if (subFolders.isNotEmpty()) {
                                        items(subFolders, key = { it.path }) { folder ->
                                                Hairline(
                                                        modifier = Modifier.padding(horizontal = 24.dp)
                                                )
                                                // Only songs are selectable in Local: a folder
                                                // can't be moved whole through MediaStore (its
                                                // covers and other files would stay behind).
                                                FolderRow(
                                                        folder = folder,
                                                        enabled = !selecting,
                                                        onClick = {
                                                                vm.navigateTo(folder.path, folder.name)
                                                        },
                                                )
                                        }
                                }

                                if (subFolders.isNotEmpty() && songsInFolder.isNotEmpty()) {
                                        item {
                                                Hairline(
                                                        modifier = Modifier.padding(horizontal = 24.dp)
                                                )
                                        }
                                }

                                items(songsInFolder, key = { it.id }) { song ->
                                        Hairline(modifier = Modifier.padding(horizontal = 24.dp))
                                        SongRowInFolder(
                                                song = song,
                                                onClick = {
                                                        if (selecting) vm.toggleSelection(song)
                                                        else vm.playSong(song)
                                                },
                                                onLongClick =
                                                        if (!vm.canEditFiles) null
                                                        else {
                                                                {
                                                                        if (selecting) vm.toggleSelection(song)
                                                                        else vm.startSelection(song)
                                                                }
                                                        },
                                                selected = if (selecting) song.id in selection else null,
                                        )
                                }

                                if (subFolders.isEmpty() && songsInFolder.isEmpty()) {
                                        item {
                                                Column(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(
                                                                                horizontal = 24.dp,
                                                                                vertical = 60.dp,
                                                                        ),
                                                        horizontalAlignment =
                                                                Alignment.CenterHorizontally,
                                                ) {
                                                        Icon(
                                                                HugeIcons.FolderOff,
                                                                null,
                                                                modifier = Modifier.size(48.dp),
                                                                tint = colors.textMute,
                                                        )
                                                        Spacer(Modifier.height(12.dp))
                                                        Text(
                                                                "No music found",
                                                                style = text.editorialHeadline32,
                                                                color = colors.textDim,
                                                        )
                                                        Text(
                                                                "Scan your library first",
                                                                style = text.metaSmall115,
                                                                color = colors.textMute,
                                                        )
                                                }
                                        }
                                }
                        }
                }

                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
        }

        picker?.let { state ->
                FolderPickerSheet(
                        state = state,
                        count = selection.size,
                        onOpen = { vm.pickerOpen(it) },
                        onCrumb = { vm.pickerCrumb(it) },
                        onChoose = { vm.confirmMove() },
                        onDismiss = { vm.dismissPicker() },
                )
        }

        if (showPlaylistSheet) {
                ModalBottomSheet(onDismissRequest = { showPlaylistSheet = false }) {
                        Column(
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                                Text(
                                        "Add folder to playlist",
                                        style = text.editorialHeadline32,
                                        color = colors.text,
                                        modifier = Modifier.padding(bottom = 16.dp),
                                )
                                ListItem(
                                        headlineContent = {
                                                Text("New playlist", style = text.trackTitle22)
                                        },
                                        leadingContent = { Icon(HugeIcons.Add, "Add") },
                                        modifier =
                                                Modifier.clickable {
                                                        showPlaylistSheet = false
                                                        showNewPlaylistDialog = true
                                                },
                                )
                                Hairline(modifier = Modifier.padding(vertical = 8.dp))
                                LazyColumn {
                                        items(playlists, key = { it.id }) { playlist ->
                                                ListItem(
                                                        headlineContent = {
                                                                Text(
                                                                        playlist.name,
                                                                        style = text.trackTitle22,
                                                                )
                                                        },
                                                        leadingContent = {
                                                                Icon(
                                                                        HugeIcons.PlaylistPlay,
                                                                        null,
                                                                )
                                                        },
                                                        modifier =
                                                                Modifier.clickable {
                                                                        vm.addFolderToPlaylist(
                                                                                playlist.id
                                                                        )
                                                                        showPlaylistSheet = false
                                                                },
                                                )
                                        }
                                }
                        }
                }
        }

        if (showNewPlaylistDialog) {
                AlertDialog(
                        onDismissRequest = { showNewPlaylistDialog = false },
                        title = { Text("New playlist", style = text.editorialHeadline32) },
                        text = {
                                OutlinedTextField(
                                        value = newPlaylistName,
                                        onValueChange = { newPlaylistName = it },
                                        label = { Text("Name") },
                                        singleLine = true,
                                )
                        },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                vm.createPlaylist(newPlaylistName)
                                                showNewPlaylistDialog = false
                                                newPlaylistName = ""
                                        }
                                ) { Text("Create") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showNewPlaylistDialog = false }) {
                                        Text("Cancel")
                                }
                        },
                )
        }
}

@Composable
private fun FolderRow(folder: FolderItem, onClick: () -> Unit, enabled: Boolean = true) {
        val meta = buildString {
                if (folder.subFolderCount > 0) append("${folder.subFolderCount} folders")
                if (folder.subFolderCount > 0 && folder.totalSongCount > 0) append(" · ")
                if (folder.totalSongCount > 0) append("${folder.totalSongCount} songs")
        }
        BrowserFolderRow(name = folder.name, meta = meta, onClick = onClick, enabled = enabled)
}

@Composable
private fun SongRowInFolder(
        song: SongEntity,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
        selected: Boolean? = null,
) {
        BrowserTrackRow(
                title = song.title,
                subtitle = song.artist,
                trailingMeta = formatDuration(song.duration),
                onClick = onClick,
                onLongClick = onLongClick,
                selected = selected,
        )
}
