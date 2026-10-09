package com.source.player.data.freebox

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings view of the on-phone cache of Freebox covers (Réglages → Freebox →
 * "Clear cover cache").
 *
 * TODO(lot 3): implemented by `FreeboxArtCache` (data/freebox/library, filesDir/freebox_art);
 * swap the binding in di/LibraryModeModule and delete [NoFreeboxArtCache].
 */
interface FreeboxArtCacheInfo {
    /** Bytes used on disk by cached covers. */
    val sizeBytes: Flow<Long>

    /** Deletes every cached cover; [sizeBytes] then emits the new size. */
    suspend fun clear()
}

/** Placeholder until the Freebox library scanner caches covers: always empty. */
@Singleton
class NoFreeboxArtCache @Inject constructor() : FreeboxArtCacheInfo {
    private val size = MutableStateFlow(0L)
    override val sizeBytes: Flow<Long> = size.asStateFlow()

    override suspend fun clear() = Unit
}
