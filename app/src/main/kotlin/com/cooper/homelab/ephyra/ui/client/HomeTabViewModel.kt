package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.browse.JellyfinLatestRepository
import com.cooper.homelab.ephyra.browse.JellyfinLibraryRepository
import com.cooper.homelab.ephyra.browse.JellyfinNextUpRepository
import com.cooper.homelab.ephyra.browse.JellyfinResumeRepository
import com.cooper.homelab.ephyra.client.home.HomeImage
import com.cooper.homelab.ephyra.client.home.HomeUiState
import com.cooper.homelab.ephyra.client.home.HomeViewModel
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Holds Home's [HomeViewModel] for the life of the tab, so a rotation or a switch of tab keeps the rows ([SignedInTabViewModel]). The
 * repositories are built for the user and the server's [profile]. With no session, Home is a "not connected" failure.
 */
class HomeTabViewModel(
    connection: Connection,
    private val dispatcher: CoroutineDispatcher,
    private val profile: JellyfinServerProfile,
) : SignedInTabViewModel<HomeUiState, HomeViewModel>(
        connection,
        dispatcher,
        loading = HomeUiState.Loading,
        notConnected = HomeUiState.Error(ServerFailure.NotConnected),
    ) {
    override fun build(
        scope: CoroutineScope,
        session: JellyfinSession,
        client: JellyfinClient,
    ) = HomeViewModel(
        scope = scope,
        dispatcher = dispatcher,
        libraries = JellyfinLibraryRepository(client, session.userId, profile),
        resume = JellyfinResumeRepository(client, session.userId, profile),
        nextUp = JellyfinNextUpRepository(client, session.userId),
        latest = JellyfinLatestRepository(client, session.userId, profile),
        classify = { it.serverFailure() },
    )

    /** Turns a picture Home asks for into a URL at [width] pixels, or null before the client is read. */
    fun imageUrl(
        image: HomeImage,
        width: Int,
    ): String? = images?.of(image.itemId, image.kind, image.tag, width)
}
