package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageUrls
import com.cooper.homelab.ephyra.browse.JellyfinPersonRepository
import com.cooper.homelab.ephyra.browse.JellyfinUserDataRepository
import com.cooper.homelab.ephyra.client.title.PersonUiState
import com.cooper.homelab.ephyra.client.title.PersonViewModel
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the host has read of the session so far. */
private sealed interface PersonSource {
    data object Starting : PersonSource

    data object NotConnected : PersonSource

    class Ready(
        val model: PersonViewModel,
        val images: ImageUrls,
    ) : PersonSource
}

/**
 * Holds a person page's [PersonViewModel] for the life of the page, so a rotation keeps what it shows. The signed-in client is read once
 * ([Connection.signedIn]) and the page's model is built on it, with the repositories for the user and the server's [profile]. [socket]
 * is the server's `UserDataChanged` messages, so a mark made in another app shows here. With no session the page is a "not connected"
 * failure, and a retry reads the session again. The access token is the client's own, in its header: pictures are named through
 * [ImageUrls], which never carries it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonPageViewModel(
    private val connection: Connection,
    private val dispatcher: CoroutineDispatcher,
    private val profile: JellyfinServerProfile,
    private val socket: Flow<List<UserDataChange>>,
) : ViewModel() {
    private val source = MutableStateFlow<PersonSource>(PersonSource.Starting)
    private var itemId: String? = null
    private var starting: Job? = null

    /** What the page shows. */
    val state: StateFlow<PersonUiState> =
        source
            .flatMapLatest {
                when (it) {
                    PersonSource.Starting -> flowOf(PersonUiState.Loading)
                    PersonSource.NotConnected -> flowOf(PersonUiState.Failed(ServerFailure.NotConnected))
                    is PersonSource.Ready -> it.model.state
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, PersonUiState.Loading)

    /** Opens the page for the person [id]. Does nothing once it has, or while the read of the session still runs. */
    fun start(id: String) {
        itemId = id
        if (source.value is PersonSource.Ready || starting?.isActive == true) return
        starting =
            viewModelScope.launch(dispatcher) {
                val signedIn = attempt { connection.signedIn() }.getOrNull()
                if (signedIn == null) {
                    source.value = PersonSource.NotConnected
                    return@launch
                }
                val (session, client) = signedIn
                val model =
                    PersonViewModel(
                        scope = viewModelScope,
                        dispatcher = dispatcher,
                        people = JellyfinPersonRepository(client, session.userId, profile),
                        userData = JellyfinUserDataRepository(client, session.userId, profile, socket),
                        classify = { it.serverFailure() },
                    )
                source.value = PersonSource.Ready(model, ImageUrls(client))
                model.start(id)
            }
    }

    /** Reads the person again after a failure; with no session read yet, it reads the session again. */
    fun retry() {
        val ready = source.value as? PersonSource.Ready
        if (ready != null) ready.model.retry() else itemId?.let(::start)
    }

    /** Asks for the next page of titles, and is also the retry of a page that failed. */
    fun loadMore() = (source.value as? PersonSource.Ready)?.model?.loadMore()

    /** Turns a picture the page asks for into a URL at [width] pixels, or null before the client is read. */
    fun imageUrl(
        image: TitleImage,
        width: Int,
    ): String? = (source.value as? PersonSource.Ready)?.images?.of(image.itemId, image.kind, image.tag, width)
}
