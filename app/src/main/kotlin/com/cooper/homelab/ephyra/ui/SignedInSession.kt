package com.cooper.homelab.ephyra.ui

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.auth.SessionStore
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinAccount
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinUnreadableAnswer
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.server.SAVED_SERVER
import com.cooper.homelab.ephyra.server.logout
import com.cooper.homelab.ephyra.server.me
import com.cooper.homelab.ephyra.server.noted
import com.cooper.homelab.ephyra.server.publicInfo
import com.cooper.homelab.ephyra.server.quickConnectEnabled
import com.cooper.homelab.ephyra.telemetry.SignInReports
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * What happens once a session is saved: reading what the server and the user allow, the library index, the server's health, and
 * the end of the session. It works on the screen state it is given, as [StartupWizard] and [ForgotPasswordFlow] do, and
 * leaves a session that has ended to the setup half through [state].
 */
class SignedInSession(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<UiState.SignedIn?>,
    private val sessions: SessionStore,
    private val connection: Connection,
    private val index: LibraryIndex,
    private val card: ServerCardLoader,
    private val localNetwork: LocalNetworkPermission,
    private val reports: SignInReports,
    private val onSessionEnded: suspend () -> Unit,
    /** Hands the screen to the setup half, with the server's address, once the session has ended. */
    private val leave: (baseUrl: String) -> Unit,
) {
    init {
        // A console page found the token refused: sign-in again, as a refused library refresh does.
        scope.launch {
            connection.refused.collect { state.value?.let { sessionRefused(it.session) } }
        }
        // The saved server stopped answering, or began again, while the app is open: the home follows it, as binge-seerr's does.
        scope.launch {
            connection.monitor.health(SAVED_SERVER).collect { reach ->
                state.update { it?.afterReach(reach, localNetwork) }
            }
        }
    }

    /**
     * Reads the gates, then the library index. With [gatesOnly], reads the gates again and only them:
     * when a console page closes (it may have changed one, as turning Quick Connect off does) and when
     * the app comes back (the server or the user's rights may have changed elsewhere). That read is
     * quiet: one that fails keeps the gates as they were, and a full refresh says why.
     */
    fun refresh(gatesOnly: Boolean = false) {
        val current = state.value ?: return
        if (gatesOnly) {
            scope.refreshGates(connection, state, card)
            return
        }
        // One read at a time: a second would end the first's spinner early, and the older of the two could write last.
        if (current.refreshing) return
        state.value = current.copy(refreshing = true, refreshFailed = false)
        scope.launch {
            val health =
                try {
                    val signedIn = connection.signedIn()
                    if (signedIn == null) {
                        state.update { it?.copy(refreshing = false) }
                        return@launch
                    }
                    // The gates first, so a library index that fails can't leave them unread.
                    val gates = signedIn.second.readGates(signedIn.first.serverVersion)
                    // The server has answered: end the first check here, not when the index finishes. A health that is already a problem stays one.
                    state.update {
                        it?.copy(
                            profile = gates.first,
                            account = gates.second,
                            health = if (it.health == ConnectionHealth.Checking) ConnectionHealth.Healthy else it.health,
                        )
                    }
                    card.refresh(scope, state, signedIn.first, signedIn.second, gates.second, force = true)
                    index.refresh(signedIn.second, signedIn.first.userId)
                    ConnectionHealth.Healthy
                } catch (e: CancellationException) {
                    throw e
                } catch (e: JellyfinHttpException) {
                    connection.logger.noted(TAG, "library refresh refused", e)
                    if (e.code == HTTP_UNAUTHORIZED) {
                        sessionRefused(current.session)
                        return@launch
                    }
                    ConnectionHealth.CouldNotLoad
                } catch (e: JellyfinUnreadableAnswer) {
                    connection.logger.noted(TAG, "library refresh answered something that is not a media server's", e)
                    ConnectionHealth.CouldNotLoad
                } catch (e: IOException) {
                    connection.logger.noted(TAG, "library refresh failed", e)
                    unreachableHealth(current.session.baseUrl, localNetwork, e)
                }
            state.update {
                it?.copy(
                    refreshing = false,
                    refreshFailed = health.isProblem,
                    health = health,
                    titles = index.size,
                )
            }
        }
    }

    /** The server no longer accepts the token (a revoked device, say): forget it and ask for a sign-in, as the Service's UNAUTHENTICATED does. */
    private suspend fun sessionRefused(session: JellyfinSession) {
        sessions.clear()
        index.clear()
        onSessionEnded()
        leave(session.baseUrl)
    }

    /** Changes the signed-in state; with no session, nothing changes. */
    internal fun updateSignedIn(change: (UiState.SignedIn) -> UiState.SignedIn) = state.update { it?.let(change) }

    /** Ends the session on the server too, so the device leaves the user's Jellyfin devices list. A failure there still signs out here. */
    fun signOut() {
        state.update { it?.copy(confirmingSignOut = false, confirmingTakeBack = false) }
        scope.launch {
            val signedIn = connection.signedIn()
            attempt { signedIn?.second?.logout() }
            reports.signedOut()
            sessions.clear()
            index.clear()
            onSessionEnded()
            leave(signedIn?.first?.baseUrl.orEmpty())
        }
    }
}

internal const val TAG = "JellyfinSetup"
internal const val HTTP_UNAUTHORIZED = 401

/**
 * The console's gates: the server's profile (its current version, which may have moved since sign-in,
 * and whether Quick Connect is on) and the user's account. Only the account read may fail the refresh.
 */
private suspend fun JellyfinClient.readGates(signedInVersion: String): Pair<JellyfinServerProfile, JellyfinAccount> {
    val account = me()
    val version = attempt { publicInfo().version }.getOrNull() ?: signedInVersion
    val quickConnect = attempt { quickConnectEnabled() }.getOrDefault(true)
    return JellyfinServerProfile.from(version, quickConnect) to account
}

/** Reads what the server and the user allow again, and the server card with it, leaving the library alone. */
private fun CoroutineScope.refreshGates(
    connection: Connection,
    state: MutableStateFlow<UiState.SignedIn?>,
    card: ServerCardLoader,
) {
    launch {
        val (session, client) = connection.signedIn() ?: return@launch
        attempt { client.readGates(session.serverVersion) }.onSuccess { (profile, account) ->
            state.update { it?.copy(profile = profile, account = account) }
            card.refresh(this@refreshGates, state, session, client, account, force = false)
        }
    }
}
