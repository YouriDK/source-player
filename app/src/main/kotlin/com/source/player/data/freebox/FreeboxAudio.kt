package com.source.player.data.freebox

/**
 * File-type filters for the Freebox music folder, shared by the library scanner and
 * the live browser. AUDIO_EXTENSIONS matches `FreeboxViewModel.AUDIO_EXTENSIONS`.
 */
object FreeboxAudio {
    val AUDIO_EXTENSIONS: Set<String> =
        setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma", "aiff", "aif")

    /** Folder cover candidates (cover.jpg, folder.png…). */
    val IMAGE_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp")

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun isAudio(name: String): Boolean = extension(name) in AUDIO_EXTENSIONS

    fun isImage(name: String): Boolean = extension(name) in IMAGE_EXTENSIONS
}
