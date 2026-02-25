package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.lastfm.LastFmException
import com.source.player.data.lastfm.LastFmRepository
import com.source.player.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LastFmLoginState {
        object Idle : LastFmLoginState
        object Loading : LastFmLoginState
        data class Error(val message: String) : LastFmLoginState
        object Success : LastFmLoginState
}

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
        private val prefs: AppPreferences,
        private val lastFmRepo: LastFmRepository,
) : ViewModel() {

        val isDarkMode = prefs.isDarkMode.stateIn(viewModelScope, SharingStarted.Eagerly, true)
        val accentColor =
                prefs.accentColor.stateIn(viewModelScope, SharingStarted.Eagerly, 0x0D33F2)
        val gapless = prefs.gapless.stateIn(viewModelScope, SharingStarted.Eagerly, false)
        val audioDucking = prefs.audioDucking.stateIn(viewModelScope, SharingStarted.Eagerly, true)
        val restoreState = prefs.restoreState.stateIn(viewModelScope, SharingStarted.Eagerly, true)
        val scrobbling = prefs.scrobbling.stateIn(viewModelScope, SharingStarted.Eagerly, false)
        val artDownloadPolicy =
                prefs.artDownloadPolicy.stateIn(viewModelScope, SharingStarted.Eagerly, "WIFI")
        val searchBarAtBottom =
                prefs.searchBarAtBottom.stateIn(viewModelScope, SharingStarted.Eagerly, false)
        val rememberLastTab =
                prefs.rememberLastTab.stateIn(viewModelScope, SharingStarted.Eagerly, true)
        val fontFamily =
                prefs.fontFamily.stateIn(viewModelScope, SharingStarted.Eagerly, "PlusJakartaSans")
        val lastFmUser: StateFlow<String?> =
                prefs.lastFmUser.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val _loginState = MutableStateFlow<LastFmLoginState>(LastFmLoginState.Idle)
        val loginState = _loginState.asStateFlow()

        fun setDarkMode(v: Boolean) = viewModelScope.launch { prefs.setDarkMode(v) }
        fun setAccentColor(v: Int) = viewModelScope.launch { prefs.setAccentColor(v) }
        fun setGapless(v: Boolean) = viewModelScope.launch { prefs.setGapless(v) }
        fun setAudioDucking(v: Boolean) = viewModelScope.launch { prefs.setAudioDucking(v) }
        fun setRestoreState(v: Boolean) = viewModelScope.launch { prefs.setRestoreState(v) }
        fun setScrobbling(v: Boolean) = viewModelScope.launch { prefs.setScrobbling(v) }
        fun setArtDownloadPolicy(v: String) =
                viewModelScope.launch { prefs.setArtDownloadPolicy(v) }
        fun setSearchBarAtBottom(v: Boolean) =
                viewModelScope.launch { prefs.setSearchBarAtBottom(v) }
        fun setRememberLastTab(v: Boolean) = viewModelScope.launch { prefs.setRememberLastTab(v) }
        fun setFontFamily(v: String) = viewModelScope.launch { prefs.setFontFamily(v) }

        fun loginLastFm(username: String, password: String) {
                if (username.isBlank() || password.isBlank()) {
                        _loginState.value =
                                LastFmLoginState.Error("Username and password required.")
                        return
                }
                viewModelScope.launch {
                        _loginState.value = LastFmLoginState.Loading
                        try {
                                lastFmRepo.login(username, password)
                                _loginState.value = LastFmLoginState.Success
                        } catch (e: LastFmException) {
                                _loginState.value =
                                        LastFmLoginState.Error(
                                                e.msg
                                                        ?: "Authentication failed. Check your credentials."
                                        )
                        } catch (e: Exception) {
                                _loginState.value =
                                        LastFmLoginState.Error(
                                                "Network error. Check your internet connection."
                                        )
                        }
                }
        }

        fun logoutLastFm() =
                viewModelScope.launch {
                        lastFmRepo.clearCredentials()
                        _loginState.value = LastFmLoginState.Idle
                }

        fun resetLoginState() {
                _loginState.value = LastFmLoginState.Idle
        }
}
