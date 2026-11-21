package com.cooper.homelab.ephyra.openitem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.ui.UiState
import com.cooper.homelab.ephyra.ui.connected
import com.cooper.homelab.ephyra.ui.nav.ShellRoute
import com.cooper.homelab.ephyra.ui.nav.openTitle
import kotlinx.coroutines.flow.first

/** Whether a pending request can be acted on yet, must wait, or is no longer wanted. */
internal enum class OpenReadiness { Wait, Drop, Go }

/**
 * What to do with a pending request given the app's state: wait while the session or the client gate is still being read, and while the
 * root has not yet settled to the shell; drop it when nobody is signed in or the client is off, so the app opens as a normal launch.
 * [connected] and [clientResolved] are null until read, and [root] is the bottom of the back stack.
 */
internal fun openReadiness(
    connected: Boolean?,
    clientResolved: Boolean?,
    root: NavKey?,
): OpenReadiness =
    when {
        connected == null -> OpenReadiness.Wait
        !connected -> OpenReadiness.Drop
        clientResolved == null -> OpenReadiness.Wait
        !clientResolved -> OpenReadiness.Drop
        root == ShellRoute -> OpenReadiness.Go
        else -> OpenReadiness.Wait
    }

/**
 * Opens the title another app asked for, over whatever is on [backStack]: a cold start has just the shell, so Back returns to the home
 * tab, and a running app keeps its stack, so Back returns to where the user was. Each request in [requests] waits until [openReadiness]
 * says go, is resolved by [resolve], and goes on the stack only if the app is still ready. A request that does not resolve to a title
 * changes nothing.
 */
@Composable
internal fun OpenRequestedItem(
    requests: OpenItemRequests,
    state: UiState,
    clientResolved: Boolean?,
    backStack: NavBackStack<NavKey>,
    resolve: suspend (OpenItemRequest, UiState.SignedIn) -> OpenTarget,
) {
    val stateNow by rememberUpdatedState(state)
    val clientNow by rememberUpdatedState(clientResolved)
    LaunchedEffect(requests, backStack) {
        requests.requests.collect { request ->
            val ready = { openReadiness(stateNow.connected(), clientNow, backStack.firstOrNull()) }
            val readiness = snapshotFlow(ready).first { it != OpenReadiness.Wait }
            val signed = stateNow as? UiState.SignedIn
            if (readiness == OpenReadiness.Go && signed != null) {
                val target = resolve(request, signed)
                if (target is OpenTarget.Title && ready() == OpenReadiness.Go) backStack.openTitle(target.kind, target.id)
            }
        }
    }
}

/** Resolves [request] for the signed-in [signed] user over the app's own connection and index. */
internal suspend fun AppGraph.resolveFor(
    request: OpenItemRequest,
    signed: UiState.SignedIn,
): OpenTarget {
    val (session, client) = connection.signedIn() ?: return OpenTarget.Home
    val titles = JellyfinTitleRepository(client, session.userId, signed.profile)
    return resolveOpenTarget(request, session.baseUrl, titles) { key -> index.find(client, session.userId, key) }
}
