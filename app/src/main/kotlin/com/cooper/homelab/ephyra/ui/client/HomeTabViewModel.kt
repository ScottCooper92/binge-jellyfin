package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.server.connection.ConnectionHealth
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
import com.cooper.homelab.ephyra.server.SAVED_SERVER
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine

/** [read] with the monitor's word on the server laid over a Home that is still loading. */
internal fun homeShown(
    read: HomeUiState,
    reach: ConnectionHealth,
): HomeUiState =
    if (read is HomeUiState.Loading && reach == ConnectionHealth.Unreachable) HomeUiState.Error(ServerFailure.Unreachable()) else read

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
        shown = { read -> combine(read, connection.monitor.health(SAVED_SERVER), ::homeShown) },
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
