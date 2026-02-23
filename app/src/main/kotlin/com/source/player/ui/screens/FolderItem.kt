package com.source.player.ui.screens

/** Shared folder data class used by FoldersScreen and FoldersViewModel */
data class FolderItem(
    val path: String,
    val name: String,
    val songCount: Int,
    val subFolderCount: Int = 0,
    val totalSongCount: Int = songCount,
)
