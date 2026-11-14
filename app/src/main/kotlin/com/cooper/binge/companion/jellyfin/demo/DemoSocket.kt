package com.cooper.binge.companion.jellyfin.demo

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val SWITCHING_PROTOCOLS = 101
private const val SESSIONS_START = "SessionsStart"
private const val TASKS_START = "ScheduledTasksInfoStart"
private const val SESSIONS_EVERY_MILLIS = 2_000L
private const val TASKS_EVERY_MILLIS = 1_000L

/**
 * The demo server's event socket. A real server pushes what is playing and what its tasks are doing to a client that asks
 * for it; this does the same from the demo's state, so a page that waits for a push (a task that is running, what is
 * playing) updates as it would against a real server. Nothing is opened on the network.
 */
internal class DemoSocket(
    private val model: DemoModel,
    private val request: Request,
    private val listener: WebSocketListener,
) : WebSocket {
    private val pusher: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "demo-socket").apply {
                isDaemon =
                    true
            }
        }

    @Volatile private var closed = false

    init {
        pusher.execute {
            listener.onOpen(
                this,
                Response
                    .Builder()
                    .request(
                        request,
                    ).protocol(Protocol.HTTP_1_1)
                    .code(SWITCHING_PROTOCOLS)
                    .message("Switching Protocols")
                    .build(),
            )
        }
    }

    override fun request(): Request = request

    override fun queueSize(): Long = 0

    override fun send(text: String): Boolean {
        if (closed) return false
        val type = (jsonOrNull(text) as? JsonObject)?.string("MessageType")
        when (type) {
            SESSIONS_START -> every(SESSIONS_EVERY_MILLIS) { push("Sessions", "Sessions") }
            TASKS_START -> every(TASKS_EVERY_MILLIS) { push("ScheduledTasksInfo", "ScheduledTasks") }
        }
        return true
    }

    override fun send(bytes: ByteString): Boolean = false

    override fun close(
        code: Int,
        reason: String?,
    ): Boolean {
        stop()
        listener.onClosed(this, code, reason.orEmpty())
        return true
    }

    override fun cancel() = stop()

    private fun stop() {
        closed = true
        pusher.shutdownNow()
    }

    private fun every(
        millis: Long,
        work: () -> Unit,
    ) {
        if (!closed) pusher.scheduleWithFixedDelay(work, 0, millis, TimeUnit.MILLISECONDS)
    }

    private fun push(
        messageType: String,
        path: String,
    ) {
        if (closed) return
        val data = jsonOrNull(model.answer(DemoCall("GET", "/$path", { null }, { "" })).body) ?: return
        val message = JsonObject(mapOf("MessageType" to JsonPrimitive(messageType), "Data" to data))
        listener.onMessage(this, message.toString())
    }

    private fun jsonOrNull(text: String): JsonElement? =
        com.binge.companion.sdk
            .attempt { json(text) }
            .getOrNull()
}
