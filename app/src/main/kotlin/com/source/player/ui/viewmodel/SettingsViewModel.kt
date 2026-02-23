package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
        private val prefs: AppPreferences,
) : ViewModel() {

  val isDarkMode = prefs.isDarkMode.stateIn(viewModelScope, SharingStarted.Eagerly, true)
  val accentColor = prefs.accentColor.stateIn(viewModelScope, SharingStarted.Eagerly, 0x0D33F2)
  val gapless = prefs.gapless.stateIn(viewModelScope, SharingStarted.Eagerly, false)
  val audioDucking = prefs.audioDucking.stateIn(viewModelScope, SharingStarted.Eagerly, true)
  val restoreState = prefs.restoreState.stateIn(viewModelScope, SharingStarted.Eagerly, true)
  val scrobbling = prefs.scrobbling.stateIn(viewModelScope, SharingStarted.Eagerly, false)
  val artDownloadPolicy =
          prefs.artDownloadPolicy.stateIn(viewModelScope, SharingStarted.Eagerly, "WIFI")
  val searchBarAtBottom =
          prefs.searchBarAtBottom.stateIn(viewModelScope, SharingStarted.Eagerly, false)
  val rememberLastTab = prefs.rememberLastTab.stateIn(viewModelScope, SharingStarted.Eagerly, true)

  fun setDarkMode(v: Boolean) = viewModelScope.launch { prefs.setDarkMode(v) }
  fun setAccentColor(v: Int) = viewModelScope.launch { prefs.setAccentColor(v) }
  fun setGapless(v: Boolean) = viewModelScope.launch { prefs.setGapless(v) }
  fun setAudioDucking(v: Boolean) = viewModelScope.launch { prefs.setAudioDucking(v) }
  fun setRestoreState(v: Boolean) = viewModelScope.launch { prefs.setRestoreState(v) }
  fun setScrobbling(v: Boolean) = viewModelScope.launch { prefs.setScrobbling(v) }
  fun setArtDownloadPolicy(v: String) = viewModelScope.launch { prefs.setArtDownloadPolicy(v) }
  fun setSearchBarAtBottom(v: Boolean) = viewModelScope.launch { prefs.setSearchBarAtBottom(v) }
  fun setRememberLastTab(v: Boolean) = viewModelScope.launch { prefs.setRememberLastTab(v) }
}
