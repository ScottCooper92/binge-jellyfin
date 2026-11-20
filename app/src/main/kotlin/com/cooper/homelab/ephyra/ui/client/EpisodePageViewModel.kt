package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.viewModelScope
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.browse.JellyfinUserDataRepository
import com.cooper.homelab.ephyra.client.title.EpisodeUiState
import com.cooper.homelab.ephyra.client.title.EpisodeViewModel
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Holds an episode page's [EpisodeViewModel] for the life of the page, so a rotation keeps what it shows. The signed-in client is read once
 * ([Connection.signedIn]) and the page's model is built on it, with the repositories for the user and the server's [profile]. [socket]
 * is the server's `UserDataChanged` messages, so a mark made in another app shows here. The host is [TitlePageViewModel]'s: this adds the
 * model the page is built on and its own actions.
 */
class EpisodePageViewModel(
    connection: Connection,
    dispatcher: CoroutineDispatcher,
    profile: JellyfinServerProfile,
    socket: Flow<List<UserDataChange>>,
) : TitlePageViewModel<EpisodeViewModel, EpisodeUiState>(
        connection,
        dispatcher,
        profile,
        socket,
        loading = EpisodeUiState.Loading,
        notConnected = EpisodeUiState.Failed(ServerFailure.NotConnected),
    ) {
    override fun create(
        client: JellyfinClient,
        userId: String,
    ) = EpisodeViewModel(
        scope = viewModelScope,
        dispatcher = dispatcher,
        titles = JellyfinTitleRepository(client, userId, profile),
        userData = JellyfinUserDataRepository(client, userId, profile, socket),
        classify = { it.serverFailure() },
    )

    override fun stateOf(model: EpisodeViewModel) = model.state

    override fun eventsOf(model: EpisodeViewModel) = model.events

    override fun open(
        model: EpisodeViewModel,
        id: String,
    ) = model.start(id)

    override fun reload(model: EpisodeViewModel) = model.retry()

    fun toggleWatched() = withModel { it.toggleWatched() }

    fun toggleFavourite() = withModel { it.toggleFavourite() }

    /** Reads the season's other episodes again after they failed. */
    fun retrySeason() = withModel { it.retrySeason() }
}
