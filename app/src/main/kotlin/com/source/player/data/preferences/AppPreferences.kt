package com.source.player.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("source_prefs")

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val ctx: Context) {

  private object Keys {
    val DARK_MODE = booleanPreferencesKey("dark_mode")
    val ACCENT_COLOR = intPreferencesKey("accent_color") // legacy, migrated to ACCENT_HUE
    val ACCENT_HUE = floatPreferencesKey("accent_hue")
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
    val FONT_FAMILY = stringPreferencesKey("font_family")
    val SONOS_ACTIVE_ID = stringPreferencesKey("sonos_active_id")
    // MediaStore change-detection stamps — see MediaScanner.scanIfStale()
    val MEDIASTORE_VERSION = stringPreferencesKey("mediastore_version")
    val MEDIASTORE_GENERATION = longPreferencesKey("mediastore_generation")
    val LAST_SCAN_AT = longPreferencesKey("last_scan_at")
  }

  val isDarkMode: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.DARK_MODE] ?: true }
  /**
   * Vinyl accent hue in degrees (0..360). On first read after upgrading from
   * the old `accent_color` RGB-int storage, the hue is extracted from the
   * stored RGB and persisted, then the legacy key is dropped.
   */
  val accentHue: Flow<Float> =
          ctx.dataStore.data.map { prefs ->
            prefs[Keys.ACCENT_HUE]
                    ?: prefs[Keys.ACCENT_COLOR]?.let { rgb -> hueFromRgbInt(rgb) }
                    ?: DEFAULT_ACCENT_HUE
          }
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
  val fontFamily: Flow<String> =
          ctx.dataStore.data.map { it[Keys.FONT_FAMILY] ?: "PlusJakartaSans" }
  val sonosActiveId: Flow<String?> = ctx.dataStore.data.map { it[Keys.SONOS_ACTIVE_ID] }

  /**
   * Stamp of the MediaStore state at the end of the last successful scan:
   * (version, generation, wall-clock). [MediaScanner.scanIfStale] compares the
   * live values against it to decide whether a rescan is worth running.
   */
  suspend fun mediaStoreStamp(): Triple<String?, Long, Long> {
    val prefs = ctx.dataStore.data.first()
    return Triple(
            prefs[Keys.MEDIASTORE_VERSION],
            prefs[Keys.MEDIASTORE_GENERATION] ?: -1L,
            prefs[Keys.LAST_SCAN_AT] ?: 0L,
    )
  }

  suspend fun setMediaStoreStamp(version: String, generation: Long, scannedAt: Long) =
          ctx.dataStore.edit {
            it[Keys.MEDIASTORE_VERSION] = version
            it[Keys.MEDIASTORE_GENERATION] = generation
            it[Keys.LAST_SCAN_AT] = scannedAt
          }

  suspend fun setDarkMode(v: Boolean) = ctx.dataStore.edit { it[Keys.DARK_MODE] = v }
  suspend fun setAccentHue(v: Float) =
          ctx.dataStore.edit {
            it[Keys.ACCENT_HUE] = ((v % 360f) + 360f) % 360f
            it.remove(Keys.ACCENT_COLOR)
          }
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
  suspend fun setFontFamily(v: String) = ctx.dataStore.edit { it[Keys.FONT_FAMILY] = v }
  suspend fun setSonosActiveId(v: String?) =
          ctx.dataStore.edit {
            if (v == null) it.remove(Keys.SONOS_ACTIVE_ID) else it[Keys.SONOS_ACTIVE_ID] = v
          }
  suspend fun saveQueueState(json: String, index: Int, positionMs: Long) =
          ctx.dataStore.edit {
            it[Keys.QUEUE_JSON] = json
            it[Keys.QUEUE_INDEX] = index
            it[Keys.QUEUE_POSITION] = positionMs
          }

  /**
   * Drop the persisted queue entirely. Used when the user dismisses playback:
   * without this the next launch would restore the queue they just swiped away.
   */
  suspend fun clearQueueState() =
          ctx.dataStore.edit {
            it.remove(Keys.QUEUE_JSON)
            it.remove(Keys.QUEUE_INDEX)
            it.remove(Keys.QUEUE_POSITION)
          }

  companion object {
    const val DEFAULT_ACCENT_HUE = 60f // Amber
  }
}

/**
 * Extract the HSL hue (0..360) from a packed RGB int. Used once to migrate the
 * legacy `accent_color` stored value into the new hue-based setting.
 */
private fun hueFromRgbInt(rgb: Int): Float {
  val r = ((rgb shr 16) and 0xFF) / 255f
  val g = ((rgb shr 8) and 0xFF) / 255f
  val b = (rgb and 0xFF) / 255f
  val max = maxOf(r, g, b)
  val min = minOf(r, g, b)
  val delta = max - min
  if (delta == 0f) return AppPreferences.DEFAULT_ACCENT_HUE
  val h =
          when (max) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
          }
  return ((h % 360f) + 360f) % 360f
}
