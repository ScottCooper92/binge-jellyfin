package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.search.QueryHistory
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.JellyfinSearchRepository
import com.cooper.homelab.ephyra.client.search.SearchBody
import com.cooper.homelab.ephyra.client.search.SearchHit
import com.cooper.homelab.ephyra.client.search.SearchModel
import com.cooper.homelab.ephyra.client.search.SearchType
import com.cooper.homelab.ephyra.client.search.SearchUiState
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Holds the Search tab's [SearchModel] for the life of the tab, so a rotation or a switch of tab keeps the field and the results
 * ([SignedInTabViewModel]). [initialQuery] is what the field held before a process death. The repository is built for the user and the
 * server's [profile], and [history] is this server's recent queries. With no session the tab is a "not connected" failure.
 */
class SearchTabViewModel(
    connection: Connection,
    dispatcher: CoroutineDispatcher,
    private val profile: JellyfinServerProfile,
    private val history: QueryHistory,
    private val initialQuery: String,
) : SignedInTabViewModel<SearchUiState, SearchModel>(
        connection,
        dispatcher,
        loading = SearchUiState(initialQuery, SearchType.All, SearchBody.Idle(emptyList())),
        notConnected = SearchUiState(initialQuery, SearchType.All, SearchBody.Failed(ServerFailure.NotConnected)),
    ) {
    override fun build(
        scope: CoroutineScope,
        session: JellyfinSession,
        client: JellyfinClient,
    ) = SearchModel(
        scope = scope,
        search = JellyfinSearchRepository(client, session.userId, profile),
        history = history,
        classify = { it.serverFailure() },
        initialQuery = initialQuery,
    )

    /** The URL of [hit]'s picture at [width] pixels, or null before the client is read or for a result with no picture. */
    fun imageUrl(
        hit: SearchHit,
        width: Int,
    ): String? =
        when (hit) {
            is SearchHit.Title -> images?.of(hit.item, ImageKind.Primary, width)
            is SearchHit.Person -> hit.person.imageTag?.let { images?.of(hit.person.id, ImageKind.Primary, it, width) }
        }

    fun setText(text: String) = model?.setText(text)

    fun clear() = model?.clear()

    fun submit() = model?.submit()

    fun loadMore() = model?.loadMore()

    fun retry() = model?.retry() ?: start()

    fun selectType(type: SearchType) = model?.selectType(type)

    fun pickRecent(text: String) = model?.pickRecent(text)

    fun clearRecents() = model?.clearRecents()
}
