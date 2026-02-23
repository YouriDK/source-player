package com.source.player.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("source_prefs")

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val ctx: Context) {

  private object Keys {
    val DARK_MODE = booleanPreferencesKey("dark_mode")
    val ACCENT_COLOR = intPreferencesKey("accent_color")
    val GAPLESS = booleanPreferencesKey("gapless")
    val AUDIO_DUCKING = booleanPreferencesKey("audio_ducking")
    val RESTORE_STATE = booleanPreferencesKey("restore_state")
    val LASTFM_USER = stringPreferencesKey("lastfm_user")
    val LASTFM_TOKEN = stringPreferencesKey("lastfm_token")
    val SCROBBLING = booleanPreferencesKey("scrobbling")
    val ART_DOWNLOAD = stringPreferencesKey("art_download") // NEVER | WIFI | ALWAYS
    val SEARCH_BAR_BOTTOM = booleanPreferencesKey("search_bar_bottom")
    val REMEMBER_LAST_TAB = booleanPreferencesKey("remember_last_tab")
    val LAST_LIBRARY_TAB = intPreferencesKey("last_library_tab")
    val QUEUE_JSON = stringPreferencesKey("queue_json")
    val QUEUE_INDEX = intPreferencesKey("queue_index")
    val QUEUE_POSITION = longPreferencesKey("queue_position_ms")
  }

  val isDarkMode: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.DARK_MODE] ?: true }
  val accentColor: Flow<Int> = ctx.dataStore.data.map { it[Keys.ACCENT_COLOR] ?: 0x0D33F2 }
  val gapless: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.GAPLESS] ?: false }
  val audioDucking: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.AUDIO_DUCKING] ?: true }
  val restoreState: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.RESTORE_STATE] ?: true }
  val lastFmUser: Flow<String?> = ctx.dataStore.data.map { it[Keys.LASTFM_USER] }
  val lastFmToken: Flow<String?> = ctx.dataStore.data.map { it[Keys.LASTFM_TOKEN] }
  val scrobbling: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.SCROBBLING] ?: false }
  val artDownloadPolicy: Flow<String> = ctx.dataStore.data.map { it[Keys.ART_DOWNLOAD] ?: "WIFI" }
  val searchBarAtBottom: Flow<Boolean> =
          ctx.dataStore.data.map { it[Keys.SEARCH_BAR_BOTTOM] ?: false }
  val rememberLastTab: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.REMEMBER_LAST_TAB] ?: true }
  val lastLibraryTab: Flow<Int> = ctx.dataStore.data.map { it[Keys.LAST_LIBRARY_TAB] ?: 0 }
  val savedQueueJson: Flow<String?> = ctx.dataStore.data.map { it[Keys.QUEUE_JSON] }
  val savedQueueIndex: Flow<Int> = ctx.dataStore.data.map { it[Keys.QUEUE_INDEX] ?: 0 }
  val savedQueuePosition: Flow<Long> = ctx.dataStore.data.map { it[Keys.QUEUE_POSITION] ?: 0L }

  suspend fun setDarkMode(v: Boolean) = ctx.dataStore.edit { it[Keys.DARK_MODE] = v }
  suspend fun setAccentColor(v: Int) = ctx.dataStore.edit { it[Keys.ACCENT_COLOR] = v }
  suspend fun setGapless(v: Boolean) = ctx.dataStore.edit { it[Keys.GAPLESS] = v }
  suspend fun setAudioDucking(v: Boolean) = ctx.dataStore.edit { it[Keys.AUDIO_DUCKING] = v }
  suspend fun setRestoreState(v: Boolean) = ctx.dataStore.edit { it[Keys.RESTORE_STATE] = v }
  suspend fun setLastFmCredentials(user: String, token: String) =
          ctx.dataStore.edit {
            it[Keys.LASTFM_USER] = user
            it[Keys.LASTFM_TOKEN] = token
          }
  suspend fun setScrobbling(v: Boolean) = ctx.dataStore.edit { it[Keys.SCROBBLING] = v }
  suspend fun setArtDownloadPolicy(v: String) = ctx.dataStore.edit { it[Keys.ART_DOWNLOAD] = v }
  suspend fun setSearchBarAtBottom(v: Boolean) =
          ctx.dataStore.edit { it[Keys.SEARCH_BAR_BOTTOM] = v }
  suspend fun setRememberLastTab(v: Boolean) = ctx.dataStore.edit { it[Keys.REMEMBER_LAST_TAB] = v }
  suspend fun setLastLibraryTab(v: Int) = ctx.dataStore.edit { it[Keys.LAST_LIBRARY_TAB] = v }
  suspend fun saveQueueState(json: String, index: Int, positionMs: Long) =
          ctx.dataStore.edit {
            it[Keys.QUEUE_JSON] = json
            it[Keys.QUEUE_INDEX] = index
            it[Keys.QUEUE_POSITION] = positionMs
          }
}
