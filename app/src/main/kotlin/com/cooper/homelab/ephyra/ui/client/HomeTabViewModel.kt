package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.server.connection.ConnectionHealth
import com.cooper.homelab.ephyra.browse.ImageUrls
import com.cooper.homelab.ephyra.browse.JellyfinLatestRepository
import com.cooper.homelab.ephyra.browse.JellyfinLibraryRepository
import com.cooper.homelab.ephyra.browse.JellyfinNextUpRepository
import com.cooper.homelab.ephyra.browse.JellyfinResumeRepository
import com.cooper.homelab.ephyra.client.home.HomeImage
import com.cooper.homelab.ephyra.client.home.HomeUiState
import com.cooper.homelab.ephyra.client.home.HomeViewModel
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.SAVED_SERVER
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the tab has read of the session so far. */
private sealed interface Source {
    data object Starting : Source

    data object NotConnected : Source

    class Ready(
        val home: HomeViewModel,
        val images: ImageUrls,
    ) : Source
}

/** [read] with the monitor's word on the server laid over a Home that is still loading. */
internal fun homeShown(
    read: HomeUiState,
    reach: ConnectionHealth,
): HomeUiState =
    if (read is HomeUiState.Loading && reach == ConnectionHealth.Unreachable) HomeUiState.Error(ServerFailure.Unreachable()) else read

/**
 * Holds Home's [HomeViewModel] for the life of the tab, so a rotation or a switch of tab keeps the rows. The signed-in client is read
 * once ([Connection.signedIn]) and the repositories are built on it for the user and the server's [profile]. With no session, Home is
 * a "not connected" failure. The access token is the client's own, in its header: [imageUrl] builds picture URLs through [ImageUrls],
 * which never carries it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeTabViewModel(
    private val connection: Connection,
    private val dispatcher: CoroutineDispatcher,
    private val profile: JellyfinServerProfile,
) : ViewModel() {
    private val source = MutableStateFlow<Source>(Source.Starting)

    /** What the rows say, before the connection's own word on the server is laid over it. */
    private val read: Flow<HomeUiState> =
        source.flatMapLatest {
            when (it) {
                Source.Starting -> flowOf(HomeUiState.Loading)
                Source.NotConnected -> flowOf(HomeUiState.Error(ServerFailure.NotConnected))
                is Source.Ready -> it.home.state
            }
        }

    /**
     * What Home shows. While the rows are still loading, a server the connection monitor reads as unreachable shows "No
     * connection" at once, as the console's home does, instead of waiting for every read to time out. Rows that have landed stay.
     */
    val state: StateFlow<HomeUiState> =
        combine(read, connection.monitor.health(SAVED_SERVER), ::homeShown)
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.Loading)

    /** Whether a refresh is still running, for the pull-to-refresh indicator. */
    val refreshing: StateFlow<Boolean> =
        source
            .flatMapLatest { (it as? Source.Ready)?.home?.refreshing ?: flowOf(false) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Turns a picture Home asks for into a URL at [width] pixels, or null before the client is read. */
    fun imageUrl(
        image: HomeImage,
        width: Int,
    ): String? = (source.value as? Source.Ready)?.images?.of(image.itemId, image.kind, image.tag, width)

    /** The read of the client that is running, so a second [start] while it runs builds no second Home. */
    private var starting: Job? = null

    /**
     * Reads the signed-in client and starts Home. Does nothing once it has, or while a read is still running (the tab leaving and
     * re-entering composition calls it again), and a retry after no session reads it again.
     */
    fun start() {
        if (source.value is Source.Ready || starting?.isActive == true) return
        starting =
            viewModelScope.launch(dispatcher) {
                val signedIn = attempt { connection.signedIn() }.getOrNull()
                if (signedIn == null) {
                    source.value = Source.NotConnected
                    return@launch
                }
                val (session, client) = signedIn
                val home =
                    HomeViewModel(
                        scope = viewModelScope,
                        dispatcher = dispatcher,
                        libraries = JellyfinLibraryRepository(client, session.userId, profile),
                        resume = JellyfinResumeRepository(client, session.userId, profile),
                        nextUp = JellyfinNextUpRepository(client, session.userId),
                        latest = JellyfinLatestRepository(client, session.userId, profile),
                        classify = { it.serverFailure() },
                    )
                source.value = Source.Ready(home, ImageUrls(client))
                home.start()
            }
    }

    /** Reads everything again; with no client read yet, it is the retry of [start]. */
    fun refresh() {
        val ready = source.value as? Source.Ready
        if (ready == null) start() else ready.home.refresh()
    }
}
