package com.cooper.homelab.ephyra.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.homelab.ephyra.browse.ImageUrls
import com.cooper.homelab.ephyra.client.title.TitleEvent
import com.cooper.homelab.ephyra.client.title.TitleImage
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.UserDataChange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the host has read of the session so far. [M] is the model the page is built on once the client is read. */
private sealed interface PageSource<out M> {
    data object Starting : PageSource<Nothing>

    data object NotConnected : PageSource<Nothing>

    class Ready<out M>(
        val model: M,
        val images: ImageUrls,
    ) : PageSource<M>
}

/**
 * The signed-in host every title page shares: it holds the page's model [M] for the life of the page, so a rotation keeps what it
 * shows. The signed-in client is read once ([Connection.signedIn], under `attempt`) and the page's model is built on it by [create],
 * with the server's [profile] and [socket], the server's `UserDataChanged` messages, so a mark made in another app shows here. With no
 * session the page is a "not connected" failure, [notConnected], and a retry reads the session again. Its state is [S]: [loading]
 * until the model exists, then the model's own. The access token is the client's own, in its header: pictures are named through
 * [ImageUrls], which never carries it. A page adds only what is its own: the model it builds, how that model starts and retries, and
 * its extra actions through [withModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class TitlePageViewModel<M : Any, S : Any>(
    private val connection: Connection,
    protected val dispatcher: CoroutineDispatcher,
    protected val profile: JellyfinServerProfile,
    protected val socket: Flow<List<UserDataChange>>,
    loading: S,
    notConnected: S,
) : ViewModel() {
    private val source = MutableStateFlow<PageSource<M>>(PageSource.Starting)
    private var itemId: String? = null
    private var starting: Job? = null

    /** What the page shows. */
    val state: StateFlow<S> =
        source
            .flatMapLatest {
                when (it) {
                    PageSource.Starting -> flowOf(loading)
                    PageSource.NotConnected -> flowOf(notConnected)
                    is PageSource.Ready -> stateOf(it.model)
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, loading)

    /** What the page tells the user once, as a snackbar. A page whose model says nothing of the kind has none. */
    val events: Flow<TitleEvent> = source.flatMapLatest { (it as? PageSource.Ready)?.let { ready -> eventsOf(ready.model) } ?: emptyFlow() }

    /** Builds the page's model on the signed-in [client] for the user [userId]; it runs in [viewModelScope] on [dispatcher]. */
    protected abstract fun create(
        client: JellyfinClient,
        userId: String,
    ): M

    /** The model's state. */
    protected abstract fun stateOf(model: M): StateFlow<S>

    /** The model's one-off messages, if it has any. */
    protected open fun eventsOf(model: M): Flow<TitleEvent> = emptyFlow()

    /** Opens [model] on the item [id]. */
    protected abstract fun open(
        model: M,
        id: String,
    )

    /** Reads the item again after a failure. */
    protected abstract fun reload(model: M)

    /** Opens the page for the item [id]. Does nothing once it has, or while the read of the session still runs. */
    fun start(id: String) {
        itemId = id
        if (source.value is PageSource.Ready || starting?.isActive == true) return
        starting =
            viewModelScope.launch(dispatcher) {
                val signedIn = attempt { connection.signedIn() }.getOrNull()
                if (signedIn == null) {
                    source.value = PageSource.NotConnected
                    return@launch
                }
                val (session, client) = signedIn
                val model = create(client, session.userId)
                source.value = PageSource.Ready(model, ImageUrls(client))
                open(model, id)
            }
    }

    /** Reads the item again after a failure; with no session read yet, it reads the session again. */
    fun retry() {
        val ready = source.value as? PageSource.Ready
        if (ready != null) reload(ready.model) else itemId?.let(::start)
    }

    /** Turns a picture the page asks for into a URL at [width] pixels, or null before the client is read. */
    fun imageUrl(
        image: TitleImage,
        width: Int,
    ): String? = (source.value as? PageSource.Ready)?.images?.of(image.itemId, image.kind, image.tag, width)

    /** Runs [action] on the page's model once there is one, and does nothing before. */
    protected fun <R> withModel(action: (M) -> R): R? = (source.value as? PageSource.Ready)?.model?.let(action)
}
