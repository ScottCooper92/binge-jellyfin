package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.SignedIn
import com.cooper.homelab.ephyra.server.authenticateWithQuickConnect
import com.cooper.homelab.ephyra.server.quickConnectAuthenticated
import com.cooper.homelab.ephyra.server.quickConnectEnabled
import com.cooper.homelab.ephyra.server.quickConnectInitiate
import kotlinx.coroutines.delay
import okhttp3.HttpUrl

/** How long a Quick Connect code is waited on before it counts as expired. */
private const val QUICK_CONNECT_WINDOW_MILLIS = 10 * 60_000L

/**
 * Quick Connect as a sign-in that finishes somewhere else: the server gives a code, the user approves it in a signed-in Jellyfin
 * app, and this polls until they do or the window ends. It holds no screen state; [run] hands the code out and says how it ended,
 * the way the shared flow's link providers do.
 */
internal class QuickConnectSignIn(
    private val connection: Connection,
    private val pollMillis: Long,
    private val windowMillis: Long = QUICK_CONNECT_WINDOW_MILLIS,
) {
    /** How a wait for approval ended. */
    sealed interface Outcome {
        /** The user approved the code, and the server signed this device in. */
        class Approved(
            val signedIn: SignedIn,
        ) : Outcome

        /** The server has Quick Connect turned off. */
        data object Off : Outcome

        /** The code was not approved in time. */
        data object Expired : Outcome
    }

    /** Asks the server at [url] for a code, hands it to [onCode], and waits for the user to approve it. */
    suspend fun run(
        url: HttpUrl,
        onCode: (String) -> Unit,
    ): Outcome {
        val client = connection.anonymous(url)
        if (!client.quickConnectEnabled()) return Outcome.Off
        val start = client.quickConnectInitiate()
        onCode(start.code)
        val deadline = System.currentTimeMillis() + windowMillis
        while (System.currentTimeMillis() < deadline) {
            delay(pollMillis)
            if (client.quickConnectAuthenticated(start.secret)) {
                return Outcome.Approved(client.authenticateWithQuickConnect(start.secret))
            }
        }
        return Outcome.Expired
    }
}
