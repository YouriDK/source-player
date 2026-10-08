package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.freebox.FreeboxClient
import com.source.player.data.freebox.FreeboxAuthException
import com.source.player.data.freebox.FreeboxError
import com.source.player.data.freebox.FreeboxFolder
import com.source.player.data.freebox.FreeboxStatus
import com.source.player.data.freebox.FreeboxUnreachableException
import com.source.player.data.freebox.auth.FreeboxPairingManager.PairingEvent
import com.source.player.data.lastfm.LastFmException
import com.source.player.data.lastfm.LastFmRepository
import com.source.player.data.preferences.AppPreferences
import com.source.player.data.scanner.MediaScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

sealed interface FreeboxPairingState {
        object Idle : FreeboxPairingState
        /** [step]: user-facing description of where pairing is at. */
        data class Pairing(val step: String) : FreeboxPairingState
        data class Error(val message: String) : FreeboxPairingState
}

sealed interface FreeboxStatusUi {
        object Unknown : FreeboxStatusUi
        object Checking : FreeboxStatusUi
        data class Ok(val status: FreeboxStatus) : FreeboxStatusUi
        data class Failed(val message: String) : FreeboxStatusUi
}

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
        private val prefs: AppPreferences,
        private val lastFmRepo: LastFmRepository,
        private val scanner: MediaScanner,
        private val freebox: FreeboxClient,
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

        // ---- Freebox ----

        val freeboxPaired: StateFlow<Boolean> =
                freebox.isPaired.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        val freeboxBoxName: StateFlow<String?> =
                freebox.boxName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        val freeboxStartFolder: StateFlow<FreeboxFolder?> =
                freebox.startFolder.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val _freeboxPairing = MutableStateFlow<FreeboxPairingState>(FreeboxPairingState.Idle)
        val freeboxPairing: StateFlow<FreeboxPairingState> = _freeboxPairing.asStateFlow()

        private val _freeboxStatus = MutableStateFlow<FreeboxStatusUi>(FreeboxStatusUi.Unknown)
        val freeboxStatus: StateFlow<FreeboxStatusUi> = _freeboxStatus.asStateFlow()

        private var pairingJob: Job? = null
        private var checkJob: Job? = null

        fun startPairing(host: String?) {
                pairingJob?.cancel()
                _freeboxPairing.value = FreeboxPairingState.Pairing("Looking for your Freebox\u2026")
                pairingJob =
                        viewModelScope.launch {
                                try {
                                        freebox.pair(host?.trim()?.takeIf { it.isNotEmpty() }).collect { event ->
                                                when (event) {
                                                        PairingEvent.Discovering ->
                                                                _freeboxPairing.value =
                                                                        FreeboxPairingState.Pairing(
                                                                                "Looking for your Freebox\u2026"
                                                                        )
                                                        PairingEvent.AwaitingValidation ->
                                                                _freeboxPairing.value =
                                                                        FreeboxPairingState.Pairing(
                                                                                "Waiting for validation on the Freebox\u2026"
                                                                        )
                                                        PairingEvent.Granted -> {
                                                                _freeboxPairing.value = FreeboxPairingState.Idle
                                                                checkFreebox()
                                                        }
                                                        is PairingEvent.Failed ->
                                                                _freeboxPairing.value =
                                                                        FreeboxPairingState.Error(event.error.toMessage())
                                                }
                                        }
                                } catch (e: CancellationException) {
                                        throw e
                                } catch (e: Exception) {
                                        _freeboxPairing.value =
                                                FreeboxPairingState.Error(
                                                        "Can't reach the Freebox \u2014 are you on your home Wi-Fi?"
                                                )
                                }
                        }
        }

        fun cancelPairing() {
                pairingJob?.cancel()
                pairingJob = null
                _freeboxPairing.value = FreeboxPairingState.Idle
        }

        fun checkFreebox() {
                checkJob?.cancel()
                _freeboxStatus.value = FreeboxStatusUi.Checking
                checkJob =
                        viewModelScope.launch {
                                _freeboxStatus.value =
                                        freebox.checkConnection()
                                                .fold(
                                                        onSuccess = { FreeboxStatusUi.Ok(it) },
                                                        onFailure = { e ->
                                                                FreeboxStatusUi.Failed(
                                                                        when (e) {
                                                                                is FreeboxAuthException ->
                                                                                        "Access refused \u2014 pair again"
                                                                                is FreeboxUnreachableException ->
                                                                                        "Unreachable"
                                                                                else -> e.message ?: "Unreachable"
                                                                        }
                                                                )
                                                        },
                                                )
                        }
        }

        fun unpairFreebox() {
                checkJob?.cancel()
                viewModelScope.launch {
                        freebox.unpair()
                        _freeboxStatus.value = FreeboxStatusUi.Unknown
                }
        }

        fun resetFreeboxPairingState() {
                if (_freeboxPairing.value !is FreeboxPairingState.Pairing) {
                        _freeboxPairing.value = FreeboxPairingState.Idle
                }
        }
}

/** English messages for pairing failures (mirrors Boxpix's ErrorMessages). */
private fun FreeboxError.toMessage(): String =
        when (this) {
                is FreeboxError.Network, FreeboxError.BoxNotFound ->
                        "Can't reach the Freebox \u2014 are you on your home Wi-Fi?"
                FreeboxError.PairingDenied -> "Pairing refused on the Freebox"
                FreeboxError.PairingTimeout ->
                        "Pairing timed out \u2014 tap Pair and validate on the Freebox screen"
                FreeboxError.NotPaired -> "Freebox error: not_paired"
                is FreeboxError.Http -> "Freebox error: HTTP $status"
                is FreeboxError.Api ->
                        if (code == "denied_from_external_ip") "Pairing must be done from your home Wi-Fi"
                        else "Freebox error: $code"
        }
