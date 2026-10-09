package com.source.player.di

import android.content.Context
import androidx.room.Room
import com.source.player.data.db.AppDatabase
import com.source.player.data.db.MIGRATION_2_3
import com.source.player.data.db.dao.*
import com.source.player.data.freebox.api.FreeboxApiClient
import com.source.player.data.lastfm.LastFmApi
import com.source.player.data.lastfm.LastFmRepository
import com.source.player.data.preferences.AppPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.source.player.BuildConfig
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
          // No destructive fallback: a schema bump without a registered Migration
          // must fail loudly in development, never silently wipe user playlists.
          Room.databaseBuilder(ctx, AppDatabase::class.java, "source_db")
                  .addMigrations(MIGRATION_2_3)
                  .build()

  @Provides fun provideSongDao(db: AppDatabase) = db.songDao()
  @Provides fun provideAlbumDao(db: AppDatabase) = db.albumDao()
  @Provides fun provideArtistDao(db: AppDatabase) = db.artistDao()
  @Provides fun provideGenreDao(db: AppDatabase) = db.genreDao()
  @Provides fun providePlaylistDao(db: AppDatabase) = db.playlistDao()
  @Provides fun provideBlacklistDao(db: AppDatabase) = db.blacklistDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

  @Provides
  @Singleton
  fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
  }

  @Provides
  @Singleton
  fun provideKtorClient(json: Json): HttpClient =
          HttpClient(Android) {
            install(ContentNegotiation) { json(json) }
            install(Logging) {
              level = if (BuildConfig.DEBUG) LogLevel.HEADERS else LogLevel.NONE
            }
            engine {
              connectTimeout = 15_000
              socketTimeout = 30_000
            }
          }

  /**
   * Dedicated client for the Freebox OS API. Kept apart from the shared one on
   * purpose: no Logging plugin (HEADERS level would dump the X-Fbx-App-Auth
   * session token to logcat), and its HttpTimeout must not cap Last.fm / Coil.
   * Generous request timeout: the first request after disk sleep takes 5–10 s.
   * Long /dl/ streams override requestTimeout per call (FreeboxApiClient).
   */
  @Provides
  @Singleton
  @Named(FreeboxApiClient.FREEBOX_HTTP)
  fun provideFreeboxHttpClient(): HttpClient =
          HttpClient(Android) {
            expectSuccess = false
            install(HttpTimeout) {
              requestTimeoutMillis = 30_000
              connectTimeoutMillis = 10_000
              socketTimeoutMillis = 30_000
            }
          }

  @Provides @Singleton fun provideLastFmApi(client: HttpClient): LastFmApi = LastFmApi(client)

  @Provides
  @Singleton
  fun provideLastFmRepository(api: LastFmApi, prefs: AppPreferences): LastFmRepository =
          LastFmRepository(api, prefs)
}
