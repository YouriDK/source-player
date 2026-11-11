package com.source.player.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText

/*
 * Shared pieces of the Folders tab's multi-selection (Local and Stream): the action bar
 * that replaces the header, the destination folder picker, the delete confirmation.
 */

/** Header shown while items are selected: count, Move, Delete, Cancel. */
@Composable
fun SelectionActionBar(
        count: Int,
        busy: Boolean,
        onMove: () -> Unit,
        onDelete: () -> Unit,
        onCancel: () -> Unit,
) {
        val colors = MaterialTheme.sourceColors
        val text = MaterialTheme.sourceText
        Column(Modifier.fillMaxWidth()) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                        IconButton(onClick = onCancel) {
                                Icon(HugeIcons.Close, "Cancel selection", tint = colors.text)
                        }
                        Text(
                                text = if (count == 1) "1 selected" else "$count selected",
                                style = text.editorialHeadline32,
                                color = colors.text,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onMove, enabled = count > 0 && !busy) {
                                Text("Move", style = text.pillLabel12)
                        }
                        TextButton(onClick = onDelete, enabled = count > 0 && !busy) {
                                Text("Delete", style = text.pillLabel12, color = if (count > 0 && !busy) colors.accent else colors.textMute)
                        }
                }
                if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                }
        }
}

/** Confirmation of a Stream delete (Local relies on the system trash dialog instead). */
@Composable
fun ConfirmTrashDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
        val text = MaterialTheme.sourceText
        AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(if (count == 1) "Delete 1 item?" else "Delete $count items?", style = text.editorialHeadline32) },
                text = {
                        Text(
                                "They'll be moved to the “.corbeille” folder at the root of your music " +
                                        "folder on the Freebox. Nothing is erased: empty it from Freebox OS.",
                        )
                },
                confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
}

/** One folder offered by the destination picker. */
data class PickerFolder(val key: String, val name: String, val enabled: Boolean = true)

/** Breadcrumb of the destination picker. */
data class PickerCrumb(val key: String, val name: String)

/** What the destination picker shows; null = closed. */
data class FolderPickerState(
        val crumbs: List<PickerCrumb>,
        val folders: List<PickerFolder>,
        val loading: Boolean = false,
        val error: String? = null,
        /** Whether "Move here" is allowed for the folder shown. */
        val canChoose: Boolean = false,
        /** Why "Move here" is disabled, if it is. */
        val hint: String? = null,
)

/** Destination picker for Move: browse folders, then "Move here". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
        state: FolderPickerState,
        count: Int,
        onOpen: (PickerFolder) -> Unit,
        onCrumb: (Int) -> Unit,
        onChoose: () -> Unit,
        onDismiss: () -> Unit,
) {
        val colors = MaterialTheme.sourceColors
        val text = MaterialTheme.sourceText
        ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                        Text(
                                if (count == 1) "Move 1 item to…" else "Move $count items to…",
                                style = text.editorialHeadline32,
                                color = colors.text,
                                modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                                state.crumbs.forEachIndexed { index, crumb ->
                                        val isLast = index == state.crumbs.lastIndex
                                        BreadcrumbChip(
                                                label = crumb.name,
                                                selected = isLast,
                                                leading = if (index == 0) HugeIcons.Folder else null,
                                                onClick = { if (!isLast) onCrumb(index) },
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
                        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp)) {
                                when {
                                        state.loading ->
                                                CircularProgressIndicator(
                                                        Modifier.size(28.dp).align(Alignment.Center)
                                                )
                                        state.error != null ->
                                                Text(
                                                        state.error,
                                                        style = text.metaSmall115,
                                                        color = colors.textDim,
                                                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                                )
                                        state.folders.isEmpty() ->
                                                Text(
                                                        "No sub-folders",
                                                        style = text.metaSmall115,
                                                        color = colors.textMute,
                                                        modifier = Modifier.align(Alignment.Center),
                                                )
                                        else ->
                                                LazyColumn {
                                                        items(state.folders, key = { it.key }) { folder ->
                                                                BrowserFolderRow(
                                                                        name = folder.name,
                                                                        meta = if (folder.enabled) null else "Being moved",
                                                                        onClick = { onOpen(folder) },
                                                                        enabled = folder.enabled,
                                                                )
                                                        }
                                                }
                                }
                        }
                        if (state.hint != null && !state.canChoose) {
                                Text(
                                        state.hint,
                                        style = text.metaSmall115,
                                        color = colors.textMute,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                                )
                        }
                        Button(
                                onClick = onChoose,
                                enabled = state.canChoose && !state.loading,
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        ) { Text("Move here", style = text.pillLabel12) }
                }
        }
}
