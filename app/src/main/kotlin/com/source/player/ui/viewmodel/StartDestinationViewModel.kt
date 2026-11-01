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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Decides where the app reopens, before the first frame (the splash screen stays up until
 * [startRoute] is known, so the app never shows one screen and then jumps to another).
 *
 * With "Restore last state" on, the app reopens on the screen the user left ([rememberRoute]
 * records it on every route change):
 * - a tab → that tab; Player only if a queue is actually there, Library otherwise;
 * - a detail screen (album / artist / playlist) → Library as [startRoute], plus the detail
 *   as [initialDetailRoute] for the NavHost to open on top, so Back returns to Library;
 * - nothing saved yet (first run) → Player when a queue was restored, Library otherwise.
 * With it off: Player when a queue is still loaded in the running process, Library otherwise.
 */
@HiltViewModel
class StartDestinationViewModel
@Inject
constructor(
        private val controller: PlaybackController,
        private val prefs: AppPreferences,
) : ViewModel() {

    private val _startRoute = MutableStateFlow<String?>(null)

    /** Tab the NavHost starts on; null until decided. */
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    /**
     * Detail screen to open over [startRoute] once the NavHost is up, or null. Set before
     * [startRoute], so it is final by the time the NavHost reads it.
     */
    var initialDetailRoute: String? = null
        private set

    /** Last route reported by the NavHost; written to prefs one at a time, in order. */
    private val visitedRoute = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            decide()
            // Only after deciding, so the value being read is never the one being written.
            visitedRoute.filterNotNull().collect { route ->
                if (prefs.restoreState.first()) prefs.setLastRoute(route)
            }
        }
    }

    private suspend fun decide() {
        val saved = if (prefs.restoreState.first()) prefs.lastRoute.first() else null
        _startRoute.value =
                when {
                    saved == null || saved == Routes.PLAYER ->
                            if (hasQueue()) Routes.PLAYER else Routes.LIBRARY
                    saved in RESTORABLE_TABS -> saved
                    OVERLAY_ROUTE.matches(saved) -> {
                        initialDetailRoute = saved
                        Routes.LIBRARY
                    }
                    else -> Routes.LIBRARY // unknown / stale value
                }
    }

    /**
     * Records the concrete route the user is on (e.g. "album/42") so the next launch can
     * reopen it. Transient screens (tag editor, Freebox picker) are skipped: the screen
     * under them was already recorded. No-op when "Restore last state" is off.
     */
    fun rememberRoute(route: String) {
        if (route in RESTORABLE_TABS || OVERLAY_ROUTE.matches(route)) visitedRoute.value = route
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

        /** Tabs the NavHost can start on directly. */
        val RESTORABLE_TABS = setOf(Routes.LIBRARY, Routes.PLAYER, Routes.FOLDERS, Routes.SETTINGS)

        /** Screens reopened over Library: detail pages, and Search (not a bottom tab). */
        val OVERLAY_ROUTE = Regex("""^((album|artist|playlist)/\d+|search)$""")
    }
}
