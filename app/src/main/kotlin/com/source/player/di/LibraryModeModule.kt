package com.source.player.di

import com.source.player.data.freebox.FreeboxArtCacheInfo
import com.source.player.data.freebox.NoFreeboxArtCache
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModeModule {

  /** TODO(lot 3): bind `FreeboxArtCache` (data/freebox/library) instead. */
  @Binds abstract fun bindFreeboxArtCacheInfo(impl: NoFreeboxArtCache): FreeboxArtCacheInfo
}
