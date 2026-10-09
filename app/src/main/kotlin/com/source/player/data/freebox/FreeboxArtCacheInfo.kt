package com.source.player.data.freebox

import kotlinx.coroutines.flow.Flow

/**
 * Settings view of the on-phone cache of Freebox covers (Réglages → Freebox →
 * "Clear cover cache"). Implemented by `FreeboxArtCache` (data/freebox/library,
 * filesDir/freebox_art), bound in di/LibraryModeModule.
 */
interface FreeboxArtCacheInfo {
    /** Bytes used on disk by cached covers. */
    val sizeBytes: Flow<Long>

    /** Deletes every cached cover; [sizeBytes] then emits the new size. */
    suspend fun clear()
}
