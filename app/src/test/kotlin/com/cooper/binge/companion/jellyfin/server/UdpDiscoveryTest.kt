package com.cooper.binge.companion.jellyfin.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.concurrent.thread

/** Discovery against servers answering on loopback, as Jellyfin answers the broadcast on a LAN. */
@RunWith(RobolectricTestRunner::class)
class UdpDiscoveryTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val responder = DatagramSocket(0, loopback)
    private val asked = mutableListOf<String>()

    /** Answers every probe with each of [answers], as several servers (or one, twice) would. */
    private fun answer(vararg answers: String) {
        thread(isDaemon = true) {
            val buffer = ByteArray(BUFFER)
            while (!responder.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                runCatching { responder.receive(packet) }.onFailure { return@thread }
                synchronized(asked) { asked += String(packet.data, packet.offset, packet.length) }
                answers.forEach { text ->
                    val bytes = text.toByteArray()
                    responder.send(DatagramPacket(bytes, bytes.size, packet.socketAddress))
                }
            }
        }
    }

    @After
    fun close() = responder.close()

    private fun discovery(targets: List<DiscoveryTarget> = listOf(DiscoveryTarget(loopback))) =
        UdpDiscovery(targets = { targets }, io = Dispatchers.IO, port = responder.localPort, windowMillis = WINDOW)

    @Test
    fun `every server that answers is found once, and the probe is Jellyfin's`() =
        runBlocking {
            answer(
                """{"Address":"http://192.168.86.38:8096","Id":"a","Name":"truenas","EndpointAddress":null}""",
                """{"Address":"http://192.168.86.38:8096","Id":"a","Name":"truenas"}""",
                """{"Address":"https://jf.example.com","Id":"b","Name":"cabin"}""",
                "not json",
                """{"Id":"c","Name":"no address"}""",
            )
            val found = discovery().find()
            assertEquals(listOf("truenas", "cabin"), found.map { it.name })
            assertEquals("http://192.168.86.38:8096", found.first().address)
            assertEquals(listOf("who is JellyfinServer?"), synchronized(asked) { asked.toList() })
        }

    @Test
    fun `no answer within the window is an empty list, and a target that fails is skipped`() =
        runBlocking {
            val unbindable = DiscoveryTarget(loopback) { throw java.io.IOException("EPERM") }
            assertTrue(discovery(listOf(unbindable, DiscoveryTarget(loopback))).find().isEmpty())
        }

    @Test
    fun `an answer is read as Jellyfin writes it`() {
        assertEquals("http://x:8096", UdpDiscovery.parse("""{"Address":"http://x:8096"}""")?.name)
        assertNull(UdpDiscovery.parse("""{"Address":"not a url","Id":"a"}"""))
        assertNull(UdpDiscovery.parse("[1]"))
    }

    @Test
    fun `a subnet's broadcast address`() {
        val ip = InetAddress.getByName("192.168.86.53") as Inet4Address
        assertEquals("192.168.86.255", broadcastOf(ip, 24).hostAddress)
        assertEquals("192.168.95.255", broadcastOf(ip, 20).hostAddress)
        assertEquals("192.168.86.53", broadcastOf(ip, 32).hostAddress)
    }

    private companion object {
        const val BUFFER = 512
        const val WINDOW = 400L
    }
}
