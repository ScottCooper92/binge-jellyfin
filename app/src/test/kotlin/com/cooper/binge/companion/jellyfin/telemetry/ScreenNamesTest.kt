package com.cooper.binge.companion.jellyfin.telemetry

import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.PublicServerInfo
import com.cooper.binge.companion.jellyfin.ui.SetupError
import com.cooper.binge.companion.jellyfin.ui.UiState
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenNamesTest {
    private val server = PublicServerInfo("truenas", "10.11.6", "id-1", "http://192.168.1.2:8096".toHttpUrl())
    private val session = JellyfinSession("http://192.168.1.2:8096", "Living room", "a1", "10.11.6", "u1", "sam", "not-a-token")

    @Test
    fun `each sign-in step has its own name, and a Quick Connect code makes it the Quick Connect one`() {
        assertNull(ScreenNames.of(UiState.Loading, null))
        assertEquals("enter_server", ScreenNames.of(UiState.EnterServer(), null))
        val signIn = UiState.SignIn("http://192.168.1.2:8096", server)
        assertEquals("sign_in", ScreenNames.of(signIn, null))
        assertEquals("sign_in_quick_connect", ScreenNames.of(signIn.copy(quickConnectCode = "123456"), null))
        assertEquals("sign_in_quick_connect", ScreenNames.of(signIn.copy(gettingCode = true), null))
    }

    @Test
    fun `a signed-in user is on the home, or on the console page open over it`() {
        val signedIn = UiState.SignedIn(session)
        assertEquals("home", ScreenNames.of(signedIn, null))
        assertEquals("console_libraries", ScreenNames.of(signedIn, ConsolePage.Libraries))
    }

    @Test
    fun `every console page has its own lowercase snake case name, with nothing but letters, digits and underscores`() {
        val names = ConsolePage.entries.map(ScreenNames::console)
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { it.matches(Regex("console_[a-z0-9]+(_[a-z0-9]+)*")) })
        assertEquals("console_api_keys", ScreenNames.console(ConsolePage.ApiKeys))
        assertEquals("console_approve_code", ScreenNames.console(ConsolePage.ApproveCode))
    }

    @Test
    fun `a failed sign-in is reported as one of a fixed set of reasons`() {
        val reasons = SetupError.entries.map { it.analyticsReason }.toSet()
        assertTrue(reasons.all { it.matches(Regex("[a-z_]+")) })
        assertEquals("wrong_credentials", SetupError.WrongCredentials.analyticsReason)
        assertEquals("other", SetupError.Generic.analyticsReason)
    }
}
