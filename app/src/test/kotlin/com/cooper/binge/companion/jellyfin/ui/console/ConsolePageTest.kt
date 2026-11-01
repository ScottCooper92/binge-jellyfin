package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsolePageTest {
    private val server = JellyfinServerProfile.from("10.11.2")

    private fun account(policy: String) = JellyfinAccount("Claude", Json.parseToJsonElement(policy).jsonObject)

    @Test
    fun `an administrator gets every page`() {
        val admin = account("""{"IsAdministrator":true}""")
        assertTrue(ConsolePage.entries.all { it.available(server, admin) })
    }

    @Test
    fun `a user gets only their own pages`() {
        val user = account("""{"IsAdministrator":false}""")
        assertEquals(
            listOf(ConsolePage.ApproveCode, ConsolePage.Playback, ConsolePage.HomeLayout, ConsolePage.Password, ConsolePage.NowPlaying),
            ConsolePage.entries.filter { it.available(server, user) },
        )
        assertTrue(ConsolePage.entries.filter { it.isAdmin }.none { it.available(server, user) })
    }

    @Test
    fun `trickplay is for an administrator on a server that has it`() {
        val admin = account("""{"IsAdministrator":true}""")
        val user = account("""{"IsAdministrator":false}""")
        assertFalse(ConsolePage.Trickplay.available(JellyfinServerProfile.from("10.8.13"), admin))
        assertTrue(ConsolePage.Trickplay.available(JellyfinServerProfile.from("10.9.0"), admin))
        assertTrue(ConsolePage.Trickplay.available(JellyfinServerProfile.from(null), admin))
        assertFalse(ConsolePage.Trickplay.available(server, user))
    }

    @Test
    fun `plugins is for an administrator on any release`() {
        val admin = account("""{"IsAdministrator":true}""")
        val user = account("""{"IsAdministrator":false}""")
        for (release in listOf("10.8.13", "10.9.0", "10.11.2", null)) {
            assertTrue(ConsolePage.Plugins.available(JellyfinServerProfile.from(release), admin))
            assertFalse(ConsolePage.Plugins.available(JellyfinServerProfile.from(release), user))
        }
        assertTrue(ConsolePage.Plugins.isAdmin)
    }

    @Test
    fun `pages follow what the server and the user's policy turned off`() {
        val user =
            account(
                """{"IsAdministrator":false,"EnableUserPreferenceAccess":false,"AuthenticationProviderId":"Ldap"}""",
            )
        assertFalse(ConsolePage.Playback.available(server, user))
        assertFalse(ConsolePage.HomeLayout.available(server, user))
        assertFalse(ConsolePage.Password.available(server, user))
        assertFalse(ConsolePage.ApproveCode.available(server.copy(quickConnect = false), user))
        assertTrue(ConsolePage.NowPlaying.available(server, user))
    }
}
