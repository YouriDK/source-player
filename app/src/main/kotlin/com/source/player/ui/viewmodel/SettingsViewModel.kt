package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.lastfm.LastFmException
import com.source.player.data.lastfm.LastFmRepository
import com.source.player.data.preferences.AppPreferences
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.SOURCE_SMB
import com.source.player.data.scanner.MediaScanner
import com.source.player.data.smb.SmbAuthenticationException
import com.source.player.data.smb.SmbClient
import com.source.player.data.smb.SmbConfig
import com.source.player.data.smb.SmbScanner
import java.io.FileNotFoundException
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

sealed interface SmbTestState {
        object Idle : SmbTestState
        object Testing : SmbTestState
        data class Success(val entryCount: Int) : SmbTestState
        data class Error(val message: String) : SmbTestState
}

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
        private val prefs: AppPreferences,
        private val lastFmRepo: LastFmRepository,
        private val scanner: MediaScanner,
        private val smbClient: SmbClient,
        private val smbScanner: SmbScanner,
        private val songDao: SongDao,
) : ViewModel() {

        val isDarkMode = prefs.isDarkMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
        val accentHue =
                prefs.accentHue.stateIn(
                        viewModelScope,
                        SharingStarted.WhileSubscribed(5_000),
                        AppPreferences.DEFAULT_ACCENT_HUE,
                )
        val gapless = prefs.gapless.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        val audioDucking = prefs.audioDucking.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
        val restoreState = prefs.restoreState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
        val scrobbling = prefs.scrobbling.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        val artDownloadPolicy =
                prefs.artDownloadPolicy.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "WIFI")
        val searchBarAtBottom =
                prefs.searchBarAtBottom.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        val rememberLastTab =
                prefs.rememberLastTab.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
        val fontFamily =
                prefs.fontFamily.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "PlusJakartaSans")
        val lastFmUser: StateFlow<String?> =
                prefs.lastFmUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val scanProgress =
                scanner.progressMessage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val _loginState = MutableStateFlow<LastFmLoginState>(LastFmLoginState.Idle)
        val loginState = _loginState.asStateFlow()

        val smbConfig: StateFlow<SmbConfig?> =
                prefs.smbConfig.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        val smbSongCount: StateFlow<Int> =
                songDao.countBySourceFlow(SOURCE_SMB)
                        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
        val smbScanProgress: StateFlow<String?> =
                smbScanner.progressMessage.stateIn(
                        viewModelScope,
                        SharingStarted.WhileSubscribed(5_000),
                        null,
                )
        val smbLastError: StateFlow<String?> = smbScanner.lastError

        private val _smbTestState = MutableStateFlow<SmbTestState>(SmbTestState.Idle)
        val smbTestState = _smbTestState.asStateFlow()

        fun setDarkMode(v: Boolean) = viewModelScope.launch { prefs.setDarkMode(v) }
        fun setAccentHue(v: Float) = viewModelScope.launch { prefs.setAccentHue(v) }
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

        fun scanLibrary() = viewModelScope.launch { scanner.scan() }

        fun testSmb(config: SmbConfig) {
                if (config.host.isBlank() || config.share.isBlank()) {
                        _smbTestState.value = SmbTestState.Error("Host and share name are required")
                        return
                }
                viewModelScope.launch {
                        _smbTestState.value = SmbTestState.Testing
                        val result = smbClient.testConnection(config)
                        _smbTestState.value =
                                result.fold(
                                        onSuccess = { SmbTestState.Success(it) },
                                        onFailure = { e ->
                                                SmbTestState.Error(
                                                        when (e) {
                                                                is SmbAuthenticationException ->
                                                                        "Access denied \u2014 check username and password"
                                                                is FileNotFoundException ->
                                                                        "Share or folder not found"
                                                                else ->
                                                                        "Can't reach server: ${e.message ?: e.javaClass.simpleName}"
                                                        }
                                                )
                                        },
                                )
                }
        }

        fun saveSmb(config: SmbConfig) {
                if (config.host.isBlank() || config.share.isBlank()) {
                        _smbTestState.value = SmbTestState.Error("Host and share name are required")
                        return
                }
                viewModelScope.launch {
                        prefs.setSmbConfig(config)
                        smbScanner.scan()
                }
        }

        fun removeSmb() {
                viewModelScope.launch {
                        smbScanner.clear()
                        prefs.clearSmbConfig()
                }
        }

        fun scanSmb() {
                viewModelScope.launch { smbScanner.scan() }
        }

        fun resetSmbTestState() {
                _smbTestState.value = SmbTestState.Idle
        }
}
