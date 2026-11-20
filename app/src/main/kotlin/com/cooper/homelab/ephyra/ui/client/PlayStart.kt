package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.demo.isDemoOrigin
import com.cooper.homelab.ephyra.insecurePublicHost
import com.cooper.homelab.ephyra.playback.AudioSwitchFailure
import com.cooper.homelab.ephyra.playback.PlayItem
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

/** Says why the audio did not change, and that the play goes on. An acknowledgement: the cause and the next step, and one OK. */
@Composable
internal fun AudioSwitchMessage(
    failure: AudioSwitchFailure,
    onDismiss: () -> Unit,
    inWindow: Boolean = true,
) {
    val message =
        when (failure) {
            AudioSwitchFailure.Unreachable -> R.string.client_audio_switch_unreachable
            AudioSwitchFailure.Refused -> R.string.client_audio_switch_refused
        }
    ConsoleMessage(
        title = stringResource(R.string.client_audio_switch_title),
        message = stringResource(message),
        onDismiss = onDismiss,
        inWindow = inWindow,
    )
}

/**
 * What every Play shares. [content] is given the way to begin a play of an item: it begins a sitting and hands its id to [onPlayer], which
 * opens the player; where the server at [serverAddress] cannot be played from, it says why in a message instead.
 */
@Composable
internal fun PlayStartHost(
    graph: AppGraph,
    serverAddress: String,
    onPlayer: (sessionId: String, title: String) -> Unit,
    content: @Composable (play: (PlayItem) -> Unit) -> Unit,
) {
    var blocked by remember { mutableStateOf<PlayBlock?>(null) }
    content { item ->
        val block = playBlockFor(serverAddress)
        if (block != null) blocked = block else onPlayer(graph.playbackSittings.start(item), item.title.title)
    }
    blocked?.let { PlayBlockedMessage(it, onDismiss = { blocked = null }) }
}
