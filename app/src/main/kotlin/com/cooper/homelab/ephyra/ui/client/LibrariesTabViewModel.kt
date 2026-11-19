package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ImageUrls
import com.cooper.homelab.ephyra.browse.JellyfinLibraryRepository
import com.cooper.homelab.ephyra.client.libraries.LibrariesUiState
import com.cooper.homelab.ephyra.client.libraries.LibrariesViewModel
import com.cooper.homelab.ephyra.client.libraries.LibraryTile
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the tab has read of the session so far. */
private sealed interface LibrariesSource {
    data object Starting : LibrariesSource

    data object NotConnected : LibrariesSource

    class Ready(
        val libraries: LibrariesViewModel,
        val images: ImageUrls,
    ) : LibrariesSource
}

/**
 * Holds the Libraries tab's [LibrariesViewModel] for the life of the tab, so a rotation or a switch of tab keeps the tiles. The signed-in
 * client is read once ([Connection.signedIn]) and the repository is built on it for the user and the server's [profile]. With no session
 * the tab is a "not connected" failure. The access token is the client's own, in its header: [imageUrl] builds picture URLs through
 * [ImageUrls], which never carries it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibrariesTabViewModel(
    private val connection: Connection,
    private val dispatcher: CoroutineDispatcher,
    private val profile: JellyfinServerProfile,
) : ViewModel() {
    private val source = MutableStateFlow<LibrariesSource>(LibrariesSource.Starting)

    /** What the tab shows. */
    val state: StateFlow<LibrariesUiState> =
        source
            .flatMapLatest {
                when (it) {
                    LibrariesSource.Starting -> flowOf(LibrariesUiState.Loading)
                    LibrariesSource.NotConnected -> flowOf(LibrariesUiState.Error(ServerFailure.NotConnected))
                    is LibrariesSource.Ready -> it.libraries.state
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, LibrariesUiState.Loading)

    /** Whether a refresh is still running, for the pull-to-refresh indicator. */
    val refreshing: StateFlow<Boolean> =
        source
            .flatMapLatest { (it as? LibrariesSource.Ready)?.libraries?.refreshing ?: flowOf(false) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The URL of [tile]'s picture at [width] pixels, or null before the client is read or for a library with no picture. */
    fun imageUrl(
        tile: LibraryTile,
        width: Int,
    ): String? = tile.imageTag?.let { (source.value as? LibrariesSource.Ready)?.images?.of(tile.id, ImageKind.Primary, it, width) }

    /** Reads the signed-in client and starts the tab. Does nothing once it has, and a retry after no session reads it again. */
    fun start() {
        if (source.value is LibrariesSource.Ready) return
        viewModelScope.launch(dispatcher) {
            val signedIn = attempt { connection.signedIn() }.getOrNull()
            if (signedIn == null) {
                source.value = LibrariesSource.NotConnected
                return@launch
            }
            val (session, client) = signedIn
            val libraries =
                LibrariesViewModel(
                    scope = viewModelScope,
                    dispatcher = dispatcher,
                    libraries = JellyfinLibraryRepository(client, session.userId, profile),
                    classify = { it.serverFailure() },
                )
            source.value = LibrariesSource.Ready(libraries, ImageUrls(client))
            libraries.start()
        }
    }

    /** Reads everything again; with no client read yet, it is the retry of [start]. */
    fun refresh() {
        val ready = source.value as? LibrariesSource.Ready
        if (ready == null) start() else ready.libraries.refresh()
    }
}
