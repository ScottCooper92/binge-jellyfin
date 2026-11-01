package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ServerFolder
import com.cooper.binge.companion.jellyfin.server.drives
import com.cooper.binge.companion.jellyfin.server.folders
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FolderBrowseState {
    data object Loading : FolderBrowseState

    data class Failed(
        val failure: ConsoleFailure,
    ) : FolderBrowseState

    /** The folders to choose from, inside the last of [trail] (the server's roots when it is empty). */
    data class Ready(
        val trail: List<ServerFolder>,
        val folders: List<ServerFolder>,
    ) : FolderBrowseState {
        /** The folder the user is in, which Choose picks; null at the roots, where nothing can be picked. */
        val current: ServerFolder? get() = trail.lastOrNull()
    }
}

/**
 * Browsing the server's own file system for a library's folder: its drives, then the folders in each.
 * One walk at a time; it starts over at the roots each time [start] is called.
 */
class FolderBrowserViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<FolderBrowseState>(FolderBrowseState.Loading)
    val state: StateFlow<FolderBrowseState> = _state.asStateFlow()

    private var trail = emptyList<ServerFolder>()
    private var reading: Job? = null

    /** Goes to the roots. */
    fun start() = go(emptyList())

    /** Goes into [folder]. */
    fun enter(folder: ServerFolder) = go(trail + folder)

    /** Goes up one folder, or to the roots from a drive. */
    fun up() {
        if (trail.isNotEmpty()) go(trail.dropLast(1))
    }

    /** Reads the folder again, after a failure. */
    fun retry() = go(trail)

    private fun go(to: List<ServerFolder>) {
        reading?.cancel()
        trail = to
        _state.value = FolderBrowseState.Loading
        reading =
            viewModelScope.launch {
                _state.value =
                    connection
                        .consoleCall(TAG) { _, client -> to.lastOrNull()?.let { client.folders(it.path) } ?: client.drives() }
                        .fold(
                            onSuccess = { FolderBrowseState.Ready(to, it) },
                            onFailure = { FolderBrowseState.Failed(it.consoleFailure()) },
                        )
            }
    }

    private companion object {
        const val TAG = "JellyfinFolders"
    }
}
