package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.core.telemetry.NoOpAnalytics
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.telemetry.SignInReports
import com.cooper.homelab.ephyra.testSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A refresh starts from the state it finds, in one step. A sign-out that lands between the read of the state and the write that starts
 * the refresh must not be undone: the screen would stay on the signed-in home of a session that has ended, and never show the server step.
 */
class SignedInSessionRefreshTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection =
        Connection(
            OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build(),
            sessions,
            "Jellyfin Companion",
            "Test",
            "1.0",
            Dispatchers.IO,
            DiscardLogger,
        )
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)

    @After
    fun close() {
        scope.cancel()
        connection.close()
        jellyfin.close()
    }

    /** The signed-in state, with something that happens right after the first time it is read, as a sign-out on another thread would. */
    private class ReadHook(
        private val inner: MutableStateFlow<UiState.SignedIn?>,
        private val afterFirstRead: () -> Unit,
    ) : MutableStateFlow<UiState.SignedIn?> by inner {
        /** Set once the session is built, so what it reads of the state while it starts does not use the hook up. */
        @Volatile
        var armed = false

        override var value: UiState.SignedIn?
            get() = inner.value.also { if (armed) afterFirstRead().also { armed = false } }
            set(value) {
                inner.value = value
            }
    }

    @Test
    fun `a sign-out that lands as a refresh starts is not undone by it`() {
        val inner = MutableStateFlow<UiState.SignedIn?>(signedInState(jellyfin.session()))
        val hooked = ReadHook(inner) { inner.value = null }
        val session =
            SignedInSession(
                scope,
                hooked,
                sessions,
                connection,
                LibraryIndex(),
                ServerCardLoader(),
                LocalNetworkPermission.AlwaysGranted,
                SignInReports(NoOpAnalytics),
                onSessionEnded = {},
                leave = {},
            )

        hooked.armed = true
        session.refresh()

        assertNull("the ended session is not brought back", inner.value)
    }

    @Test
    fun `a refresh while one is running starts no second`() {
        val inner = MutableStateFlow<UiState.SignedIn?>(signedInState(jellyfin.session()).copy(refreshing = true))
        val session =
            SignedInSession(
                scope,
                inner,
                sessions,
                connection,
                LibraryIndex(),
                ServerCardLoader(),
                LocalNetworkPermission.AlwaysGranted,
                SignInReports(NoOpAnalytics),
                onSessionEnded = {},
                leave = {},
            )

        session.refresh()

        assertTrue("the running refresh is left as it was", inner.value?.refreshing == true)
    }
}
