package com.source.player.di

import android.content.Context
import androidx.room.Room
import com.source.player.data.db.AppDatabase
import com.source.player.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
          Room.databaseBuilder(ctx, AppDatabase::class.java, "source_db")
                  .fallbackToDestructiveMigrationFrom()
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
            install(Logging) { level = LogLevel.BODY }
            engine {
              connectTimeout = 15_000
              socketTimeout = 30_000
            }
          }
}
