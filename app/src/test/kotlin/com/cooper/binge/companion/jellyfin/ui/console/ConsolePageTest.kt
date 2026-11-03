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
    fun `an administrator gets every page, Live TV when their policy lets them manage it`() {
        val admin = account("""{"IsAdministrator":true,"EnableLiveTvManagement":true}""")
        assertTrue(ConsolePage.entries.all { it.available(server, admin) })
    }

    @Test
    fun `Live TV follows the management flag alone, as the server does, with no shortcut for an administrator`() {
        val adminWithout = account("""{"IsAdministrator":true,"EnableLiveTvManagement":false}""")
        val userWith = account("""{"IsAdministrator":false,"EnableLiveTvManagement":true}""")
        assertFalse(ConsolePage.LiveTv.available(server, adminWithout))
        assertTrue(ConsolePage.LiveTv.available(server, userWith))
        assertFalse(ConsolePage.LiveTv.isAdmin)
        assertTrue(ConsolePage.entries.filter { it.isAdmin }.none { it.available(server, userWith) })
    }

    @Test
    fun `recording settings need an administrator who also manages Live TV, since the server saves them for administrators only`() {
        val manager = account("""{"IsAdministrator":false,"EnableLiveTvManagement":true}""")
        val adminWithout = account("""{"IsAdministrator":true,"EnableLiveTvManagement":false}""")
        val admin = account("""{"IsAdministrator":true,"EnableLiveTvManagement":true}""")
        assertFalse(ConsolePage.LiveTvRecording.available(server, manager))
        assertFalse(ConsolePage.LiveTvRecording.available(server, adminWithout))
        assertTrue(ConsolePage.LiveTvRecording.available(server, admin))
        assertFalse(ConsolePage.LiveTvRecording.isAdmin)
    }

    @Test
    fun `a user gets only their own pages`() {
        val user = account("""{"IsAdministrator":false}""")
        assertEquals(
            listOf(
                ConsolePage.ApproveCode,
                ConsolePage.Playback,
                ConsolePage.HomeLayout,
                ConsolePage.Password,
                ConsolePage.ProfilePicture,
                ConsolePage.NowPlaying,
            ),
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
    fun `advanced is for an administrator on any release`() {
        val admin = account("""{"IsAdministrator":true}""")
        val user = account("""{"IsAdministrator":false}""")
        assertTrue(ConsolePage.Advanced.available(JellyfinServerProfile.from("10.8.13"), admin))
        assertFalse(ConsolePage.Advanced.available(server, user))
        assertTrue(ConsolePage.Advanced.isAdmin)
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
