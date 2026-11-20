package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.browse.ImageUrls
import com.cooper.homelab.ephyra.client.TabModel
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a tab has read of the session so far. */
private sealed interface Source<out M> {
    data object Starting : Source<Nothing>

    data object NotConnected : Source<Nothing>

    class Ready<M>(
        val model: M,
        val images: ImageUrls,
    ) : Source<M>
}

/**
 * The host of a client tab's view model, for the life of the tab, so a rotation or a switch of tab keeps what it shows. The signed-in
 * client is read once ([Connection.signedIn]) and the tab's own model is built on it by [build]. With no session the tab is [loading]
 * until the read answers, then [notConnected]. The access token is the client's own, in its header: [images] builds picture URLs
 * through [ImageUrls], which never carries it. A tab keeps only what is its own: its [build] and how it names a picture.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class SignedInTabViewModel<S, M : TabModel<S>>(
    private val connection: Connection,
    private val dispatcher: CoroutineDispatcher,
    loading: S,
    notConnected: S,
) : ViewModel() {
    private val source = MutableStateFlow<Source<M>>(Source.Starting)

    /** What the tab shows. */
    val state: StateFlow<S> =
        source
            .flatMapLatest {
                when (it) {
                    Source.Starting -> flowOf(loading)
                    Source.NotConnected -> flowOf(notConnected)
                    is Source.Ready -> it.model.state
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, loading)

    /** Whether a refresh is still running, for the pull-to-refresh indicator. */
    val refreshing: StateFlow<Boolean> =
        source
            .flatMapLatest { (it as? Source.Ready)?.model?.refreshing ?: flowOf(false) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Builds the tab's model on the signed-in [client] for [session]. [scope] is the host's, for the model to launch in. */
    protected abstract fun build(
        scope: CoroutineScope,
        session: JellyfinSession,
        client: JellyfinClient,
    ): M

    /** The URL builder for the client that was read, or null before it is (and with no session). */
    protected val images: ImageUrls? get() = (source.value as? Source.Ready)?.images

    /** The read of the client that is running, so a second [start] while it runs builds no second model. */
    private var starting: Job? = null

    /**
     * Reads the signed-in client and starts the tab. Does nothing once it has, or while a read is still running (the tab leaving and
     * re-entering composition calls it again), and a retry after no session reads it again.
     */
    fun start() {
        if (source.value is Source.Ready || starting?.isActive == true) return
        starting =
            viewModelScope.launch(dispatcher) {
                val signedIn = attempt { connection.signedIn() }.getOrNull()
                if (signedIn == null) {
                    source.value = Source.NotConnected
                    return@launch
                }
                val (session, client) = signedIn
                val model = build(viewModelScope, session, client)
                source.value = Source.Ready(model, ImageUrls(client))
                model.start()
            }
    }

    /** Reads everything again; with no client read yet, it is the retry of [start]. */
    fun refresh() {
        val ready = source.value as? Source.Ready
        if (ready == null) start() else ready.model.refresh()
    }
}
