package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.preferences.AppPreferences
import com.source.player.service.PlaybackController
import com.source.player.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Picks the tab the app opens on: Player when a queue is there (still loaded, or restored
 * from the last session), Library otherwise. The splash screen stays up until [startRoute]
 * is known, so the app never shows one tab and then jumps to the other.
 */
@HiltViewModel
class StartDestinationViewModel
@Inject
constructor(
        private val controller: PlaybackController,
        private val prefs: AppPreferences,
) : ViewModel() {

    private val _startRoute = MutableStateFlow<String?>(null)

    /** null until decided. */
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            _startRoute.value = if (hasQueue()) Routes.PLAYER else Routes.LIBRARY
        }
    }

    private suspend fun hasQueue(): Boolean {
        // Process still alive, or the controller already hydrated / synced its queue.
        if (controller.queueItems.value.isNotEmpty()) return true
        // Same preconditions as PlaybackController's cold-start hydration: nothing
        // saved (or restore turned off) means nothing will be restored.
        if (!prefs.restoreState.first()) return false
        if (prefs.savedQueueJson.first().isNullOrBlank()) return false
        // A queue was saved: the controller publishes it once its songs are read
        // back from the DB. Wait for that, bounded, since saved songs may be gone.
        return withTimeoutOrNull(RESTORE_WAIT_MS) {
            controller.queueItems.first { it.isNotEmpty() }
        } != null
    }

    private companion object {
        const val RESTORE_WAIT_MS = 1_500L
    }
}
