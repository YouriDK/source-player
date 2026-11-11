package com.source.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.source.player.ui.icons.HugeIcons
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText

/*
 * Rows shared by the folder-style browsers (local Folders, Freebox). Same Vinyl
 * look in both: pill breadcrumbs, 32dp accent folder icon with a "→" affordance,
 * compact track rows with a mono trailing meta.
 */

@Composable
fun BreadcrumbChip(
        label: String,
        selected: Boolean,
        leading: ImageVector?,
        onClick: () -> Unit,
) {
        val colors = MaterialTheme.sourceColors
        val text = MaterialTheme.sourceText
        val bg = if (selected) colors.text else Color.Transparent
        val fg = if (selected) colors.bg else colors.textDim
        Row(
                modifier =
                        Modifier.clip(CircleShape)
                                .background(bg)
                                .clickable(onClick = onClick)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
                if (leading != null) {
                        Icon(leading, null, modifier = Modifier.size(12.dp), tint = fg)
                }
                Text(text = label, style = text.pillLabel12, color = fg, maxLines = 1)
        }
}

/**
 * [selected] non-null = the browser is in selection mode: the row shows a check mark
 * instead of its icon and is tinted when selected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserFolderRow(
        name: String,
        meta: String?,
        onClick: () -> Unit,
        icon: ImageVector = HugeIcons.Folder,
        onLongClick: (() -> Unit)? = null,
        selected: Boolean? = null,
        enabled: Boolean = true,
) {
        val colors = MaterialTheme.sourceColors
        val text = MaterialTheme.sourceText
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .selectionBackground(selected)
                                .combinedClickable(
                                        enabled = enabled,
                                        onClick = onClick,
                                        onLongClick = onLongClick,
                                )
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
                if (selected != null) {
                        SelectionMark(selected, Modifier.size(32.dp))
                } else {
                        Icon(
                                icon,
                                null,
                                tint = if (enabled) colors.accent else colors.textMute,
                                modifier = Modifier.size(32.dp),
                        )
                }
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                name,
                                style = text.folderName17,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                        if (!meta.isNullOrEmpty()) {
                                Text(
                                        meta,
                                        style = text.metaSmall115,
                                        color = colors.textDim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                )
                        }
                }
                if (selected == null) {
                        Text(text = "→", style = text.folderName17, color = colors.textMute)
                }
        }
}

@Composable
private fun Modifier.selectionBackground(selected: Boolean?): Modifier =
        if (selected == true) background(MaterialTheme.sourceColors.accent.copy(alpha = 0.14f)) else this

/** Check mark of a row in selection mode: filled accent when selected, muted otherwise. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
        val colors = MaterialTheme.sourceColors
        Icon(
                HugeIcons.CheckCircle,
                if (selected) "Selected" else "Not selected",
                tint = if (selected) colors.accent else colors.textMute.copy(alpha = 0.5f),
                modifier = modifier,
        )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserTrackRow(
        title: String,
        subtitle: String?,
        trailingMeta: String?,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null,
        selected: Boolean? = null,
) {
        val colors = MaterialTheme.sourceColors
        val text = MaterialTheme.sourceText
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .selectionBackground(selected)
                                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                                .padding(
                                        start = 24.dp,
                                        end = if (trailing != null) 12.dp else 24.dp,
                                        top = 12.dp,
                                        bottom = 12.dp,
                                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
                if (selected != null) {
                        SelectionMark(selected, Modifier.size(18.dp))
                } else {
                        Icon(
                                HugeIcons.MusicNote,
                                null,
                                tint = colors.textMute,
                                modifier = Modifier.size(18.dp),
                        )
                }
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                title,
                                style = text.trackTitle22,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                        if (!subtitle.isNullOrEmpty()) {
                                Text(
                                        subtitle,
                                        style = text.metaSmall115,
                                        color = colors.textDim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                )
                        }
                }
                if (trailingMeta != null) {
                        MonoText(text = trailingMeta, color = colors.textMute, size = MonoSize.S10)
                }
                if (selected == null) trailing?.invoke()
        }
}
