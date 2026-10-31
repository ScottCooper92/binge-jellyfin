package com.cooper.binge.companion.jellyfin.server

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** A push from the server that this app acts on. */
enum class ServerEvent {
    /** A user's played state, position or favourites changed: watch state may differ now. */
    USER_DATA_CHANGED,

    /** Items were added, removed or updated: what the library holds may differ now. */
    LIBRARY_CHANGED,
}

/**
 * The signed-in server's event socket, as a flow shared by every observer: one socket while anything
 * listens, none otherwise. It answers the server's `ForceKeepAlive` with a `KeepAlive` at half the
 * interval the server asks for, and reconnects with a backoff when the socket drops. Signed out,
 * it waits without connecting.
 */
class ServerEvents(
    private val connection: Connection,
    scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val retryDelayMillis: (attempt: Int) -> Long = { attempt ->
        minOf(
            MAX_RETRY_MILLIS,
            FIRST_RETRY_MILLIS shl attempt.coerceAtMost(MAX_SHIFT),
        )
    },
) {
    val events: SharedFlow<ServerEvent> =
        flow {
            var attempt = 0
            while (true) {
                val client = connection.signedIn()?.second ?: awaitCancellation()
                var opened = false
                emitAll(socket(client) { opened = true })
                attempt = if (opened) 0 else attempt + 1
                delay(retryDelayMillis(attempt))
            }
        }.flowOn(io)
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS))

    /** One socket's events until it closes or fails; [onOpen] runs once the server accepts it. */
    private fun socket(
        client: JellyfinClient,
        onOpen: () -> Unit,
    ): Flow<ServerEvent> =
        callbackFlow {
            var keepAlive: Job? = null
            val socket =
                client.openSocket(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) = onOpen()

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            val message = parse(text) ?: return
                            when (message.string("MessageType")) {
                                "ForceKeepAlive" -> {
                                    val seconds = message.int("Data")?.takeIf { it > 0 } ?: DEFAULT_KEEP_ALIVE_SECONDS
                                    keepAlive?.cancel()
                                    keepAlive =
                                        launch {
                                            while (isActive) {
                                                webSocket.send(KEEP_ALIVE)
                                                delay(seconds * HALF_SECOND_MILLIS)
                                            }
                                        }
                                }
                                "UserDataChanged" -> trySend(ServerEvent.USER_DATA_CHANGED)
                                "LibraryChanged" -> trySend(ServerEvent.LIBRARY_CHANGED)
                            }
                        }

                        // The server is closing: answer, which completes the close and ends this socket.
                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            webSocket.close(NORMAL_CLOSURE, null)
                            channel.close()
                        }

                        override fun onClosed(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            channel.close()
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            Log.i(TAG, "Event socket failed; reconnecting", t)
                            channel.close()
                        }
                    },
                )
            awaitClose {
                keepAlive?.cancel()
                socket.close(NORMAL_CLOSURE, null)
            }
        }

    private fun parse(text: String): JsonObject? =
        try {
            PARSER.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            Log.w(TAG, "Unreadable event from the server", e)
            null
        }

    private companion object {
        const val TAG = "JellyfinEvents"
        const val KEEP_ALIVE = """{"MessageType":"KeepAlive"}"""
        const val DEFAULT_KEEP_ALIVE_SECONDS = 60
        const val HALF_SECOND_MILLIS = 500L
        const val NORMAL_CLOSURE = 1000
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val FIRST_RETRY_MILLIS = 1_000L
        const val MAX_RETRY_MILLIS = 60_000L
        const val MAX_SHIFT = 6
        val PARSER = Json { ignoreUnknownKeys = true }
    }
}
