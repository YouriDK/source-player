package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.source.player.data.mode.LibraryModeRepository
import com.source.player.data.mode.LibraryModeStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Library mode + Stream banner for screens that only need to render them. */
@HiltViewModel
class LibraryModeViewModel @Inject constructor(repo: LibraryModeRepository) : ViewModel() {
  /** null until the preferences have been read. */
  val status: StateFlow<LibraryModeStatus?> = repo.status
}
