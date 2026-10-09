package com.source.player.di

import com.source.player.data.freebox.FreeboxArtCacheInfo
import com.source.player.data.freebox.library.FreeboxArtCache
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModeModule {

  @Binds abstract fun bindFreeboxArtCacheInfo(impl: FreeboxArtCache): FreeboxArtCacheInfo
}
