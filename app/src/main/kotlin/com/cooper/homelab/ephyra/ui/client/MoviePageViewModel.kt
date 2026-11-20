package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageUrls
import com.cooper.homelab.ephyra.browse.JellyfinCollectionRepository
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.browse.JellyfinUserDataRepository
import com.cooper.homelab.ephyra.client.title.MovieEvent
import com.cooper.homelab.ephyra.client.title.MovieUiState
import com.cooper.homelab.ephyra.client.title.MovieViewModel
import com.cooper.homelab.ephyra.client.title.TitleImage
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the host has read of the session so far. */
private sealed interface MovieSource {
    data object Starting : MovieSource

    data object NotConnected : MovieSource

    class Ready(
        val model: MovieViewModel,
        val images: ImageUrls,
    ) : MovieSource
}

/**
 * Holds a movie page's [MovieViewModel] for the life of the page, so a rotation keeps what it shows. The signed-in client is read once
 * ([Connection.signedIn]) and the page's model is built on it, with the repositories for the user and the server's [profile]. [socket]
 * is the server's `UserDataChanged` messages, so a mark made in another app shows here. With no session the page is a "not connected"
 * failure, and a retry reads the session again. The access token is the client's own, in its header: pictures are named through
 * [ImageUrls], which never carries it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoviePageViewModel(
    private val connection: Connection,
    private val dispatcher: CoroutineDispatcher,
    private val profile: JellyfinServerProfile,
    private val socket: Flow<List<UserDataChange>>,
) : ViewModel() {
    private val source = MutableStateFlow<MovieSource>(MovieSource.Starting)
    private var itemId: String? = null
    private var starting: Job? = null

    /** What the page shows. */
    val state: StateFlow<MovieUiState> =
        source
            .flatMapLatest {
                when (it) {
                    MovieSource.Starting -> flowOf(MovieUiState.Loading)
                    MovieSource.NotConnected -> flowOf(MovieUiState.Failed(ServerFailure.NotConnected))
                    is MovieSource.Ready -> it.model.state
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, MovieUiState.Loading)

    /** What the page tells the user once, as a snackbar. */
    val events: Flow<MovieEvent> = source.flatMapLatest { (it as? MovieSource.Ready)?.model?.events ?: emptyFlow() }

    /** Opens the page for the film [id]. Does nothing once it has, or while the read of the session still runs. */
    fun start(id: String) {
        itemId = id
        if (source.value is MovieSource.Ready || starting?.isActive == true) return
        starting =
            viewModelScope.launch(dispatcher) {
                val signedIn = attempt { connection.signedIn() }.getOrNull()
                if (signedIn == null) {
                    source.value = MovieSource.NotConnected
                    return@launch
                }
                val (session, client) = signedIn
                val model =
                    MovieViewModel(
                        scope = viewModelScope,
                        dispatcher = dispatcher,
                        titles = JellyfinTitleRepository(client, session.userId, profile),
                        userData = JellyfinUserDataRepository(client, session.userId, profile, socket),
                        collections = JellyfinCollectionRepository(client, session.userId, profile),
                        classify = { it.serverFailure() },
                    )
                source.value = MovieSource.Ready(model, ImageUrls(client))
                model.start(id)
            }
    }

    /** Reads the film again after a failure; with no session read yet, it reads the session again. */
    fun retry() {
        val ready = source.value as? MovieSource.Ready
        if (ready != null) ready.model.retry() else itemId?.let(::start)
    }

    fun toggleWatched() = (source.value as? MovieSource.Ready)?.model?.toggleWatched()

    fun toggleFavourite() = (source.value as? MovieSource.Ready)?.model?.toggleFavourite()

    /** Turns a picture the page asks for into a URL at [width] pixels, or null before the client is read. */
    fun imageUrl(
        image: TitleImage,
        width: Int,
    ): String? = (source.value as? MovieSource.Ready)?.images?.of(image.itemId, image.kind, image.tag, width)
}
