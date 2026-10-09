package com.source.player.data.scanner

import android.content.Context
import com.source.player.data.db.dao.AlbumDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.AlbumEntity
import com.source.player.data.lastfm.LastFmRepository
import com.source.player.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull

/**
 * Post-scan Last.fm album art enrichment, shared by every library source
 * (MediaStore scanner, SMB scanner).
 */
@Singleton
class AlbumArtEnricher
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val lastFm: LastFmRepository,
        private val prefs: AppPreferences,
        private val songDao: SongDao,
        private val albumDao: AlbumDao,
) {
        /**
         * For each album in [albums] for which [hasLocalArt] is false, fetch art from
         * Last.fm. Respects the artDownloadPolicy pref (NEVER skips everything). Makes
         * exactly one API call per album — not per song. Updates both songs and albums
         * tables with the fetched URL. [hasLocalArt] may do blocking IO; call from a
         * background dispatcher.
         */
        suspend fun enrich(
                albums: Collection<AlbumEntity>,
                hasLocalArt: (AlbumEntity) -> Boolean,
        ) {
                val policy = prefs.artDownloadPolicy.firstOrNull() ?: "NEVER"
                if (policy == "NEVER") return
                if (policy == "WIFI" && !isOnUnmeteredNetwork()) return

                val albumsNeedingArt = albums.filter { !hasLocalArt(it) }

                val fetched = mutableMapOf<Long, String>() // albumId → art URL
                for (album in albumsNeedingArt) {
                        val artist = album.artist.takeIf { it != "<Unknown>" } ?: continue
                        val title = album.title.takeIf { it != "<Unknown>" } ?: continue

                        val url = lastFm.fetchAlbumArt(artist, title) ?: continue
                        fetched[album.id] = url

                        android.util.Log.d(
                                "MediaScanner",
                                "Fetched Last.fm art for \"$title\" by $artist"
                        )
                }
                if (fetched.isEmpty()) return

                // Two batched writes (one per table) → two Room invalidations total,
                // instead of one per song plus one per album. Every live library Flow
                // re-queries on each invalidation, so this is what keeps a post-scan
                // enrichment from turning into hundreds of full-library re-emissions.
                songDao.updateAlbumArtBatch(fetched)
                albumDao.upsertAll(
                        albumsNeedingArt.mapNotNull { album ->
                                fetched[album.id]?.let { album.copy(artUri = it) }
                        }
                )
        }

        private fun isOnUnmeteredNetwork(): Boolean {
                val cm =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE)
                                as? android.net.ConnectivityManager
                                ?: return false
                val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
                return caps.hasCapability(
                        android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED
                )
        }
}
