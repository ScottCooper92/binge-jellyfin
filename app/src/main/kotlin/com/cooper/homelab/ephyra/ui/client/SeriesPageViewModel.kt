package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.viewModelScope
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.browse.JellyfinUserDataRepository
import com.cooper.homelab.ephyra.client.title.SeriesUiState
import com.cooper.homelab.ephyra.client.title.SeriesViewModel
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Holds a series page's [SeriesViewModel] for the life of the page, so a rotation keeps what it shows. The signed-in client is read once
 * ([Connection.signedIn]) and the page's model is built on it, with the repositories for the user and the server's [profile]. [socket]
 * is the server's `UserDataChanged` messages, so a mark made in another app shows here. The host is [TitlePageViewModel]'s: this adds the
 * model the page is built on and its own actions.
 */
class SeriesPageViewModel(
    connection: Connection,
    dispatcher: CoroutineDispatcher,
    profile: JellyfinServerProfile,
    socket: Flow<List<UserDataChange>>,
) : TitlePageViewModel<SeriesViewModel, SeriesUiState>(
        connection,
        dispatcher,
        profile,
        socket,
        loading = SeriesUiState.Loading,
        notConnected = SeriesUiState.Failed(ServerFailure.NotConnected),
    ) {
    override fun create(
        client: JellyfinClient,
        userId: String,
    ) = SeriesViewModel(
        scope = viewModelScope,
        dispatcher = dispatcher,
        titles = JellyfinTitleRepository(client, userId, profile),
        userData = JellyfinUserDataRepository(client, userId, profile, socket),
        classify = { it.serverFailure() },
    )

    override fun stateOf(model: SeriesViewModel) = model.state

    override fun eventsOf(model: SeriesViewModel) = model.events

    override fun open(
        model: SeriesViewModel,
        id: String,
    ) = model.start(id)

    override fun reload(model: SeriesViewModel) = model.retry()

    fun toggleWatched() = withModel { it.toggleWatched() }

    fun toggleFavourite() = withModel { it.toggleFavourite() }

    /** Shows the season [seasonId], reading its episodes the first time. */
    fun selectSeason(seasonId: String) = withModel { it.selectSeason(seasonId) }

    /** Reads the shown season's episodes again after they failed. */
    fun retryEpisodes() = withModel { it.retryEpisodes() }

    /** Marks one episode watched, or not watched when it is. */
    fun toggleEpisodeWatched(episodeId: String) = withModel { it.toggleEpisodeWatched(episodeId) }
}
