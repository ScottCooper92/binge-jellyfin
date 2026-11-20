package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.viewModelScope
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.browse.JellyfinUserDataRepository
import com.cooper.homelab.ephyra.client.title.SeasonUiState
import com.cooper.homelab.ephyra.client.title.SeasonViewModel
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Holds a season page's [SeasonViewModel] for the life of the page, so a rotation keeps what it shows. The signed-in client is read once
 * ([Connection.signedIn]) and the page's model is built on it, with the repositories for the user and the server's [profile]. [socket]
 * is the server's `UserDataChanged` messages, so a mark made in another app shows here. The host is [TitlePageViewModel]'s: this adds the
 * model the page is built on and its own actions.
 */
class SeasonPageViewModel(
    connection: Connection,
    dispatcher: CoroutineDispatcher,
    profile: JellyfinServerProfile,
    socket: Flow<List<UserDataChange>>,
) : TitlePageViewModel<SeasonViewModel, SeasonUiState>(
        connection,
        dispatcher,
        profile,
        socket,
        loading = SeasonUiState.Loading,
        notConnected = SeasonUiState.Failed(ServerFailure.NotConnected),
    ) {
    override fun create(
        client: JellyfinClient,
        userId: String,
    ) = SeasonViewModel(
        scope = viewModelScope,
        dispatcher = dispatcher,
        titles = JellyfinTitleRepository(client, userId, profile),
        userData = JellyfinUserDataRepository(client, userId, profile, socket),
        classify = { it.serverFailure() },
    )

    override fun stateOf(model: SeasonViewModel) = model.state

    override fun eventsOf(model: SeasonViewModel) = model.events

    override fun open(
        model: SeasonViewModel,
        id: String,
    ) = model.start(id)

    override fun reload(model: SeasonViewModel) = model.retry()

    /** Marks the whole season watched, or not watched when it is. */
    fun toggleWatched() = withModel { it.toggleWatched() }

    /** Reads the season's episodes again after they failed. */
    fun retryEpisodes() = withModel { it.retryEpisodes() }

    /** Marks one episode watched, or not watched when it is. */
    fun toggleEpisodeWatched(episodeId: String) = withModel { it.toggleEpisodeWatched(episodeId) }
}
