package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.ephyra.auth.JellyfinSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The review prompt reads loading as unknown, so reading the saved session is never taken as leaving and returning. */
class ConnectedStateTest {
    @Test
    fun `loading is not known yet`() = assertNull(UiState.Loading.connected())

    @Test
    fun `the server step is not connected`() = assertEquals(false, UiState.EnterServer().connected())

    @Test
    fun `a signed in session is connected`() {
        val session = JellyfinSession("http://192.168.1.2:8096", "Living room", "a1", "10.11.6", "u1", "sam", "not-a-token")
        assertEquals(true, UiState.SignedIn(session).connected())
    }
}
