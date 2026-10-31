package com.cooper.binge.companion.jellyfin.server

import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The server's event socket (`/socket`) as the messages it sends, opened again with a backoff each
 * time it drops. Each socket sends [subscribe] once the server accepts it (a `SessionsStart`, say),
 * and answers the server's `ForceKeepAlive` with a `KeepAlive` at half the interval asked for.
 */
internal fun JellyfinClient.socketMessages(
    subscribe: List<String> = emptyList(),
    retryDelayMillis: (attempt: Int) -> Long = ::socketRetryDelay,
): Flow<JsonObject> =
    flow {
        var attempt = 0
        while (true) {
            var opened = false
            emitAll(oneSocket(subscribe) { opened = true })
            attempt = if (opened) 0 else attempt + 1
            delay(retryDelayMillis(attempt))
        }
    }

/** One socket's messages until it closes or fails; [onOpen] runs once the server accepts it. */
private fun JellyfinClient.oneSocket(
    subscribe: List<String>,
    onOpen: () -> Unit,
): Flow<JsonObject> =
    callbackFlow {
        var keepAlive: Job? = null
        val socket =
            openSocket(
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        onOpen()
                        subscribe.forEach(webSocket::send)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        val message = parse(text) ?: return
                        if (message.string("MessageType") == "ForceKeepAlive") {
                            val seconds = message.int("Data")?.takeIf { it > 0 } ?: DEFAULT_KEEP_ALIVE_SECONDS
                            keepAlive?.cancel()
                            keepAlive =
                                launch {
                                    while (isActive) {
                                        webSocket.send(KEEP_ALIVE)
                                        delay(seconds * HALF_SECOND_MILLIS)
                                    }
                                }
                        } else {
                            trySend(message)
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

/** One second, doubling with each failed attempt, to a minute at most. */
internal fun socketRetryDelay(attempt: Int): Long = minOf(MAX_RETRY_MILLIS, FIRST_RETRY_MILLIS shl attempt.coerceAtMost(MAX_SHIFT))

private const val TAG = "JellyfinEvents"
private const val KEEP_ALIVE = """{"MessageType":"KeepAlive"}"""
private const val DEFAULT_KEEP_ALIVE_SECONDS = 60
private const val HALF_SECOND_MILLIS = 500L
private const val NORMAL_CLOSURE = 1000
private const val FIRST_RETRY_MILLIS = 1_000L
private const val MAX_RETRY_MILLIS = 60_000L
private const val MAX_SHIFT = 6
private val PARSER = Json { ignoreUnknownKeys = true }
