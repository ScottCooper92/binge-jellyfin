package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.JellyfinUnreadableAnswer
import com.cooper.homelab.ephyra.server.SignedIn
import com.cooper.homelab.ephyra.server.authenticateByName
import com.cooper.homelab.ephyra.server.noted
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import java.io.IOException

private const val TAG = "JellyfinSetup"

/**
 * One call of the setup half that may fail, and what a failure means. [block] runs; a server that refused it is [refused]'s
 * to word (a wrong password, an expired code), one that answered with something that is not a media server's is
 * [SetupError.Generic], and one that did not answer is [unreachableSetup]. [onFailure] gets the error, and every failure is
 * logged under [what] without its message. A cancelled scope is never a failure.
 */
internal suspend fun Connection.setupCall(
    what: String,
    baseUrl: String,
    localNetwork: LocalNetworkPermission,
    refused: (JellyfinHttpException) -> SetupError,
    onFailure: (SetupError) -> Unit,
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: JellyfinHttpException) {
        logger.noted(TAG, "$what refused", e)
        onFailure(refused(e))
    } catch (e: JellyfinUnreadableAnswer) {
        logger.noted(TAG, "$what answered something that is not a media server's", e)
        onFailure(SetupError.Generic)
    } catch (e: IOException) {
        logger.noted(TAG, "$what failed", e)
        onFailure(unreachableSetup(baseUrl, localNetwork))
    }
}

/** Signs in to the server at [url] with a username and password: what the shared flow's password authenticator will be. */
internal suspend fun Connection.passwordSignIn(
    url: HttpUrl,
    username: String,
    password: String,
): SignedIn = anonymous(url).authenticateByName(username.trim(), password)
