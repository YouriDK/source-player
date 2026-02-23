package com.source.player.startup

import android.content.Context
import androidx.startup.Initializer
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import okio.Path.Companion.toPath

/**
 * CoilInitializer runs at app startup before any Activity is created. Configures Coil with 20% RAM
 * memory cache + 100MB disk cache.
 */
class CoilInitializer : Initializer<Unit> {
  override fun create(context: Context) {
    val imageLoader =
            ImageLoader.Builder(context)
                    .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
                    .diskCache {
                      DiskCache.Builder()
                              .directory(
                                      context.cacheDir.resolve("image_cache").absolutePath.toPath()
                              )
                              .maxSizeBytes(100L * 1024 * 1024)
                              .build()
                    }
                    .components { add(KtorNetworkFetcherFactory()) }
                    .build()

    SingletonImageLoader.setSafe { imageLoader }
  }

  override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
