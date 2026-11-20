package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.viewModelScope
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.JellyfinPersonRepository
import com.cooper.homelab.ephyra.browse.JellyfinUserDataRepository
import com.cooper.homelab.ephyra.client.title.PersonUiState
import com.cooper.homelab.ephyra.client.title.PersonViewModel
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Holds a person page's [PersonViewModel] for the life of the page, so a rotation keeps what it shows. The signed-in client is read once
 * ([Connection.signedIn]) and the page's model is built on it, with the repositories for the user and the server's [profile]. [socket]
 * is the server's `UserDataChanged` messages, so a mark made in another app shows here. The host is [TitlePageViewModel]'s: this adds the
 * model the page is built on and its own actions.
 */
class PersonPageViewModel(
    connection: Connection,
    dispatcher: CoroutineDispatcher,
    profile: JellyfinServerProfile,
    socket: Flow<List<UserDataChange>>,
) : TitlePageViewModel<PersonViewModel, PersonUiState>(
        connection,
        dispatcher,
        profile,
        socket,
        loading = PersonUiState.Loading,
        notConnected = PersonUiState.Failed(ServerFailure.NotConnected),
    ) {
    override fun create(
        client: JellyfinClient,
        userId: String,
    ) = PersonViewModel(
        scope = viewModelScope,
        dispatcher = dispatcher,
        people = JellyfinPersonRepository(client, userId, profile),
        userData = JellyfinUserDataRepository(client, userId, profile, socket),
        classify = { it.serverFailure() },
    )

    override fun stateOf(model: PersonViewModel) = model.state

    override fun open(
        model: PersonViewModel,
        id: String,
    ) = model.start(id)

    override fun reload(model: PersonViewModel) = model.retry()

    /** Asks for the next page of titles, and is also the retry of a page that failed. */
    fun loadMore() = withModel { it.loadMore() }
}
