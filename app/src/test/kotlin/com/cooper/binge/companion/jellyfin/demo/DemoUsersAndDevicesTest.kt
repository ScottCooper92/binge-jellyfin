package com.cooper.binge.companion.jellyfin.demo

import com.cooper.binge.companion.jellyfin.server.ActivityFilter
import com.cooper.binge.companion.jellyfin.server.ActivityWho
import com.cooper.binge.companion.jellyfin.server.ClientIdentity
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.PlaystateCommand
import com.cooper.binge.companion.jellyfin.server.Severity
import com.cooper.binge.companion.jellyfin.server.activity
import com.cooper.binge.companion.jellyfin.server.channelMappingOptions
import com.cooper.binge.companion.jellyfin.server.createUser
import com.cooper.binge.companion.jellyfin.server.deleteUser
import com.cooper.binge.companion.jellyfin.server.devices
import com.cooper.binge.companion.jellyfin.server.nowPlaying
import com.cooper.binge.companion.jellyfin.server.removeDevice
import com.cooper.binge.companion.jellyfin.server.removeUserImage
import com.cooper.binge.companion.jellyfin.server.renameDevice
import com.cooper.binge.companion.jellyfin.server.savePolicy
import com.cooper.binge.companion.jellyfin.server.sendPlaystate
import com.cooper.binge.companion.jellyfin.server.setChannelMapping
import com.cooper.binge.companion.jellyfin.server.setUserImage
import com.cooper.binge.companion.jellyfin.server.user
import com.cooper.binge.companion.jellyfin.server.userImage
import com.cooper.binge.companion.jellyfin.server.users
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoUsersAndDevicesTest {
    private val transport = DemoTransport()
    private val client =
        JellyfinClient(
            OkHttpClient.Builder().addInterceptor(transport).build(),
            DEMO_ORIGIN.toHttpUrl(),
            ClientIdentity("Ephyra", "Test", "device-1", "1.0"),
            Dispatchers.Unconfined,
            DEMO_TOKEN,
        )

    @Test
    fun `a user can be added, given a password, changed and removed`() =
        runBlocking {
            val created = client.createUser("Robin", "secret")
            assertTrue(created.hasPassword)
            assertEquals(listOf("Alex", "Kids", "Robin", "Sam"), client.users().map { it.name })
            client.savePolicy(created.id, JsonObject(mapOf("IsAdministrator" to JsonPrimitive(true), "IsDisabled" to JsonPrimitive(true))))
            assertTrue(client.user(created.id).disabled)
            client.deleteUser(created.id)
            assertFalse(client.users().any { it.id == created.id })
        }

    @Test
    fun `a profile picture is read, set and removed on the newer route`() =
        runBlocking {
            assertNull(client.userImage(DEMO_USER_ID, newRoute = true))
            client.setUserImage(DEMO_USER_ID, byteArrayOf(1, 2, 3), newRoute = true)
            client.removeUserImage(DEMO_USER_ID, newRoute = true)
        }

    @Test
    fun `the demo refuses to remove the last administrator or the signed-in user`() {
        val failure =
            assertThrows(JellyfinHttpException::class.java) {
                runBlocking { client.savePolicy(DEMO_USER_ID, JsonObject(mapOf("IsAdministrator" to JsonPrimitive(false)))) }
            }
        assertEquals(400, failure.code)
        assertThrows(JellyfinHttpException::class.java) { runBlocking { client.deleteUser(DEMO_USER_ID) } }
    }

    @Test
    fun `a device is renamed and removed, and signing out puts the sample data back`() =
        runBlocking {
            val tablet = client.devices().first { it.name == "Tablet" }
            client.renameDevice(tablet.id, "Kids' tablet")
            assertEquals("Kids' tablet", client.devices().first { it.id == tablet.id }.displayName)
            client.removeDevice(tablet.id)
            assertFalse(client.devices().any { it.id == tablet.id })
            transport.model.reset()
            assertEquals("Tablet", client.devices().first { it.id == tablet.id }.displayName)
        }

    @Test
    fun `signing out puts the Live TV channel mappings back`() =
        runBlocking {
            client.setChannelMapping("p", "c3", "p3")
            assertEquals(
                "p3",
                client
                    .channelMappingOptions("p")
                    .tunerChannels
                    .first { it.id == "c3" }
                    .providerChannelId,
            )
            transport.model.reset()
            assertNull(
                client
                    .channelMappingOptions("p")
                    .tunerChannels
                    .first { it.id == "c3" }
                    .providerChannelId,
            )
        }

    @Test
    fun `pausing a session is seen the next time sessions are read`() =
        runBlocking {
            assertFalse(client.nowPlaying().first { it.sessionId == "demo-session-1" }.paused)
            client.sendPlaystate("demo-session-1", PlaystateCommand.Pause)
            assertTrue(client.nowPlaying().first { it.sessionId == "demo-session-1" }.paused)
        }

    @Test
    fun `the activity log pages and narrows the way the server does`() =
        runBlocking {
            val all = client.activity(0, 20)
            assertEquals(20, all.entries.size)
            assertTrue(all.total > 20)
            assertTrue(all.entries.zipWithNext().all { (a, b) -> (a.dateMillis ?: 0) >= (b.dateMillis ?: 0) })
            val system = client.activity(0, 100, ActivityFilter(who = ActivityWho.System))
            assertTrue(system.entries.isNotEmpty() && system.entries.all { it.userId == null })
            val errors = client.activity(0, 100, ActivityFilter(severity = Severity.Error))
            assertTrue(errors.entries.isNotEmpty() && errors.entries.all { it.severity == Severity.Error })
        }
}
