package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.viewModelScope
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.client.title.TitlePeopleUiState
import com.cooper.homelab.ephyra.client.title.TitlePeopleViewModel
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Holds a title's cast and crew page's [TitlePeopleViewModel] for the life of the page, so a rotation keeps what it shows. The host is
 * [TitlePageViewModel]'s: this adds the model the page is built on. The page marks nothing, so [socket] is only the host's.
 */
class TitlePeoplePageViewModel(
    connection: Connection,
    dispatcher: CoroutineDispatcher,
    profile: JellyfinServerProfile,
    socket: Flow<List<UserDataChange>>,
) : TitlePageViewModel<TitlePeopleViewModel, TitlePeopleUiState>(
        connection,
        dispatcher,
        profile,
        socket,
        loading = TitlePeopleUiState.Loading,
        notConnected = TitlePeopleUiState.Failed(ServerFailure.NotConnected),
    ) {
    override fun create(
        client: JellyfinClient,
        userId: String,
    ) = TitlePeopleViewModel(
        scope = viewModelScope,
        dispatcher = dispatcher,
        titles = JellyfinTitleRepository(client, userId, profile),
        classify = { it.serverFailure() },
    )

    override fun stateOf(model: TitlePeopleViewModel) = model.state

    override fun open(
        model: TitlePeopleViewModel,
        id: String,
    ) = model.start(id)

    override fun reload(model: TitlePeopleViewModel) = model.retry()
}
