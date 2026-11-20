package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageUrls
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.browse.JellyfinUserDataRepository
import com.cooper.homelab.ephyra.client.title.SeasonUiState
import com.cooper.homelab.ephyra.client.title.SeasonViewModel
import com.cooper.homelab.ephyra.client.title.TitleEvent
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
private sealed interface SeasonSource {
    data object Starting : SeasonSource

    data object NotConnected : SeasonSource

    class Ready(
        val model: SeasonViewModel,
        val images: ImageUrls,
    ) : SeasonSource
}

/**
 * Holds a season page's [SeasonViewModel] for the life of the page, so a rotation keeps what it shows. The signed-in client is read once
 * ([Connection.signedIn]) and the page's model is built on it, with the repositories for the user and the server's [profile]. [socket]
 * is the server's `UserDataChanged` messages, so a mark made in another app shows here. With no session the page is a "not connected"
 * failure, and a retry reads the session again. The access token is the client's own, in its header: pictures are named through
 * [ImageUrls], which never carries it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeasonPageViewModel(
    private val connection: Connection,
    private val dispatcher: CoroutineDispatcher,
    private val profile: JellyfinServerProfile,
    private val socket: Flow<List<UserDataChange>>,
) : ViewModel() {
    private val source = MutableStateFlow<SeasonSource>(SeasonSource.Starting)
    private var itemId: String? = null
    private var starting: Job? = null

    /** What the page shows. */
    val state: StateFlow<SeasonUiState> =
        source
            .flatMapLatest {
                when (it) {
                    SeasonSource.Starting -> flowOf(SeasonUiState.Loading)
                    SeasonSource.NotConnected -> flowOf(SeasonUiState.Failed(ServerFailure.NotConnected))
                    is SeasonSource.Ready -> it.model.state
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, SeasonUiState.Loading)

    /** What the page tells the user once, as a snackbar. */
    val events: Flow<TitleEvent> = source.flatMapLatest { (it as? SeasonSource.Ready)?.model?.events ?: emptyFlow() }

    /** Opens the page for the season [id]. Does nothing once it has, or while the read of the session still runs. */
    fun start(id: String) {
        itemId = id
        if (source.value is SeasonSource.Ready || starting?.isActive == true) return
        starting =
            viewModelScope.launch(dispatcher) {
                val signedIn = attempt { connection.signedIn() }.getOrNull()
                if (signedIn == null) {
                    source.value = SeasonSource.NotConnected
                    return@launch
                }
                val (session, client) = signedIn
                val model =
                    SeasonViewModel(
                        scope = viewModelScope,
                        dispatcher = dispatcher,
                        titles = JellyfinTitleRepository(client, session.userId, profile),
                        userData = JellyfinUserDataRepository(client, session.userId, profile, socket),
                        classify = { it.serverFailure() },
                    )
                source.value = SeasonSource.Ready(model, ImageUrls(client))
                model.start(id)
            }
    }

    /** Reads the season again after a failure; with no session read yet, it reads the session again. */
    fun retry() {
        val ready = source.value as? SeasonSource.Ready
        if (ready != null) ready.model.retry() else itemId?.let(::start)
    }

    fun toggleWatched() = (source.value as? SeasonSource.Ready)?.model?.toggleWatched()

    /** Reads the season's episodes again after they failed. */
    fun retryEpisodes() = (source.value as? SeasonSource.Ready)?.model?.retryEpisodes()

    /** Marks one episode watched, or not watched when it is. */
    fun toggleEpisodeWatched(episodeId: String) = (source.value as? SeasonSource.Ready)?.model?.toggleEpisodeWatched(episodeId)

    /** Turns a picture the page asks for into a URL at [width] pixels, or null before the client is read. */
    fun imageUrl(
        image: TitleImage,
        width: Int,
    ): String? = (source.value as? SeasonSource.Ready)?.images?.of(image.itemId, image.kind, image.tag, width)
}
