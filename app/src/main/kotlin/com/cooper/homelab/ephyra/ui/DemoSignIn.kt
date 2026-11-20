package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.ephyra.demo.DEMO_ORIGIN
import com.cooper.homelab.ephyra.demo.DEMO_USER_NAME
import com.cooper.homelab.ephyra.demo.isDemoOrigin
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.server.SignedIn
import com.cooper.homelab.ephyra.server.noted
import com.cooper.homelab.ephyra.telemetry.AnalyticsEvents
import com.cooper.homelab.ephyra.telemetry.SignInReports
import java.io.IOException

/**
 * The demo server, which answers from memory and signs in as its own administrator with no account. [enter] is the entry the connect
 * flow offers; [handOff] is the same sign-in for the demo's address typed or found, so the sign-in step is never shown for it.
 */
internal class DemoSignIn(
    private val connection: Connection,
    private val probe: JellyfinProbe,
    private val reports: SignInReports,
    private val save: suspend (ProbedServer<PublicServerInfo>, SignedIn) -> Boolean,
) {
    /** Signs in to the demo with no address typed. The flow counts it. */
    suspend fun enter() {
        val address = ServerAddress.parse(DEMO_ORIGIN) ?: throw DemoSignInFailed()
        signIn(probe.probe(address))
    }

    /** The demo's address, as the flow read it: signs in to the demo and says the flow need not show a sign-in step. Any other address is not its. */
    suspend fun handOff(server: ProbedServer<PublicServerInfo>): Boolean {
        if (!server.address.url.isDemoOrigin() ||
            !server.preview.baseUrl
                .toString()
                .isDemoOrigin()
        ) {
            return false
        }
        try {
            signIn(server)
        } catch (e: DemoSignInFailed) {
            reports.failed(AnalyticsEvents.METHOD_DEMO, SetupError.Generic)
            throw e
        }
        reports.signedIn(AnalyticsEvents.METHOD_DEMO)
        return true
    }

    private suspend fun signIn(server: ProbedServer<PublicServerInfo>) {
        val signedIn =
            try {
                connection.passwordSignIn(server.preview.baseUrl, DEMO_USER_NAME, "")
            } catch (e: IOException) {
                connection.logger.noted(TAG, "the demo sign-in failed", e)
                throw DemoSignInFailed()
            }
        if (!save(server, signedIn)) throw DemoSignInFailed()
    }

    private companion object {
        const val TAG = "JellyfinSetup"
    }
}
