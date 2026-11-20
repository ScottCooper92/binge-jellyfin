package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.demo.isDemoOrigin
import com.cooper.homelab.ephyra.insecurePublicHost
import com.cooper.homelab.ephyra.ui.console.ConsoleMessage

/** Why Play cannot begin for the session that is saved. Each is said to the user, with what still works. */
internal enum class PlayBlock {
    /** The demo has no video to play. */
    Demo,

    /** The server is reached over plain http at a public address, which the player does not fetch from. */
    PublicHttp,
}

/** What stops a play on the server at [baseUrl], or null when nothing does. */
internal fun playBlockFor(baseUrl: String): PlayBlock? =
    when {
        baseUrl.isDemoOrigin() -> PlayBlock.Demo
        insecurePublicHost(baseUrl) != null -> PlayBlock.PublicHttp
        else -> null
    }

/** Says why Play cannot begin, and what still works. An acknowledgement: the cause and the next step, and one OK. */
@Composable
internal fun PlayBlockedMessage(
    block: PlayBlock,
    onDismiss: () -> Unit,
    inWindow: Boolean = true,
) {
    val (title, message) =
        when (block) {
            PlayBlock.Demo -> R.string.client_play_demo_title to R.string.client_play_demo_message
            PlayBlock.PublicHttp -> R.string.client_play_http_title to R.string.client_play_http_message
        }
    ConsoleMessage(title = stringResource(title), message = stringResource(message), onDismiss = onDismiss, inWindow = inWindow)
}
