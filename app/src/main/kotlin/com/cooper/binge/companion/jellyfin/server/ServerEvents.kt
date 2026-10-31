package com.cooper.binge.companion.jellyfin.server

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.json.JsonObject

/** A push from the server that this app acts on. */
enum class ServerEvent {
    /** A user's played state, position or favourites changed: watch state may differ now. */
    USER_DATA_CHANGED,

    /** Items were added, removed or updated: what the library holds may differ now. */
    LIBRARY_CHANGED,
}

/**
 * The signed-in server's event socket, as a flow shared by every observer: one socket while anything
 * listens, none otherwise, kept alive and reconnected as [socketMessages] does. It follows the
 * saved session: a sign-out closes the socket, a new sign-in opens one for it. Signed out, it waits
 * without connecting. A `LibraryChanged` marks [index] stale once, here, before any observer reads.
 */
class ServerEvents(
    private val connection: Connection,
    private val index: LibraryIndex,
    scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val retryDelayMillis: (attempt: Int) -> Long = ::socketRetryDelay,
) {
    val events: SharedFlow<ServerEvent> =
        flow {
            emitAll(
                connection.clients().flatMapLatest { client ->
                    if (client == null) emptyFlow() else client.socketMessages(retryDelayMillis = retryDelayMillis).mapNotNull(::event)
                },
            )
        }.onEach { if (it == ServerEvent.LIBRARY_CHANGED) index.invalidate() }
            .flowOn(io)
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS))

    private fun event(message: JsonObject): ServerEvent? =
        when (message.string("MessageType")) {
            "UserDataChanged" -> ServerEvent.USER_DATA_CHANGED
            "LibraryChanged" -> ServerEvent.LIBRARY_CHANGED
            else -> null
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
