package com.cooper.binge.companion.jellyfin.server

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** A Jellyfin server that answered the broadcast: its id, its name and the address it gave. */
data class DiscoveredServer(
    val id: String,
    val name: String,
    val address: String,
)

/** Where the probe goes, and how to put its socket on the right network first (a no-op off Android). */
class DiscoveryTarget(
    val address: InetAddress,
    val bind: (DatagramSocket) -> Unit = {},
)

/**
 * Jellyfin's own discovery: a UDP datagram, `who is JellyfinServer?`, broadcast to port 7359, which
 * every server on the LAN answers with a JSON `{Address, Id, Name}`. This sends it to each of
 * [targets] and gathers the answers that arrive within [windowMillis], one per server.
 */
class UdpDiscovery(
    private val targets: () -> List<DiscoveryTarget>,
    private val io: CoroutineDispatcher,
    private val port: Int = PORT,
    private val windowMillis: Long = WINDOW_MILLIS,
) {
    /** Probes every target at once, so the whole search takes one window, not one per target. */
    suspend fun find(): List<DiscoveredServer> =
        withContext(io) {
            targets()
                .map { target ->
                    async {
                        try {
                            probe(target)
                        } catch (e: IOException) {
                            Log.i(TAG, "Discovery to ${target.address} failed", e)
                            emptyList()
                        }
                    }
                }.awaitAll()
                .flatten()
                .distinctBy { it.id }
        }

    private fun probe(target: DiscoveryTarget): List<DiscoveredServer> =
        DatagramSocket().use { socket ->
            target.bind(socket)
            socket.broadcast = true
            val message = MESSAGE.toByteArray()
            socket.send(DatagramPacket(message, message.size, target.address, port))
            val found = mutableListOf<DiscoveredServer>()
            val deadline = System.currentTimeMillis() + windowMillis
            var answer = receive(socket, deadline)
            while (answer != null) {
                parse(answer)?.let { found += it }
                answer = receive(socket, deadline)
            }
            found
        }

    /** The next answer's text, or null once [deadline] passes with none. */
    private fun receive(
        socket: DatagramSocket,
        deadline: Long,
    ): String? {
        val left = deadline - System.currentTimeMillis()
        if (left <= 0) return null
        socket.soTimeout = left.toInt()
        val packet = DatagramPacket(ByteArray(BUFFER_BYTES), BUFFER_BYTES)
        return try {
            socket.receive(packet)
            String(packet.data, packet.offset, packet.length)
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    companion object {
        const val PORT = 7359
        private const val TAG = "JellyfinDiscovery"
        private const val MESSAGE = "who is JellyfinServer?"
        private const val WINDOW_MILLIS = 3_000L
        private const val BUFFER_BYTES = 2048
        private val PARSER = Json { ignoreUnknownKeys = true }

        /** One server's answer, or null for one that is not JSON or names no usable address. */
        fun parse(text: String): DiscoveredServer? {
            val json =
                try {
                    PARSER.parseToJsonElement(text) as? JsonObject
                } catch (e: SerializationException) {
                    Log.i(TAG, "An answer that is not a Jellyfin server's", e)
                    null
                } ?: return null
            val address = json.string("Address")?.takeIf { it.toHttpUrlOrNull() != null } ?: return null
            val id = json.string("Id")?.takeIf { it.isNotBlank() } ?: address
            return DiscoveredServer(id = id, name = json.string("Name").orEmpty().ifBlank { address }, address = address)
        }
    }
}
