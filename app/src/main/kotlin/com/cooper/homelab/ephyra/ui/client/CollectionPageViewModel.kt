package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.viewModelScope
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.BoxSetMemberships
import com.cooper.homelab.ephyra.browse.JellyfinCollectionRepository
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.browse.JellyfinUserDataRepository
import com.cooper.homelab.ephyra.client.title.CollectionUiState
import com.cooper.homelab.ephyra.client.title.CollectionViewModel
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Holds a collection page's [CollectionViewModel] for the life of the page, so a rotation keeps what it shows. The signed-in client is read once
 * ([Connection.signedIn]) and the page's model is built on it, with the repositories for the user and the server's [profile]. [socket]
 * is the server's `UserDataChanged` messages, so a mark made in another app shows here. The host is [TitlePageViewModel]'s: this adds the
 * model the page is built on and its own actions.
 */
class CollectionPageViewModel(
    connection: Connection,
    dispatcher: CoroutineDispatcher,
    profile: JellyfinServerProfile,
    socket: Flow<List<UserDataChange>>,
    private val boxSets: BoxSetMemberships = BoxSetMemberships(),
) : TitlePageViewModel<CollectionViewModel, CollectionUiState>(
        connection,
        dispatcher,
        profile,
        socket,
        loading = CollectionUiState.Loading,
        notConnected = CollectionUiState.Failed(ServerFailure.NotConnected),
    ) {
    override fun create(
        client: JellyfinClient,
        userId: String,
    ) = CollectionViewModel(
        scope = viewModelScope,
        dispatcher = dispatcher,
        titles = JellyfinTitleRepository(client, userId, profile),
        userData = JellyfinUserDataRepository(client, userId, profile, socket),
        collections = JellyfinCollectionRepository(client, userId, profile, boxSets),
        classify = { it.serverFailure() },
    )

    override fun stateOf(model: CollectionViewModel) = model.state

    override fun open(
        model: CollectionViewModel,
        id: String,
    ) = model.start(id)

    override fun reload(model: CollectionViewModel) = model.retry()
}
