package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.PlaystateCommand
import com.cooper.binge.companion.jellyfin.server.SessionControls
import com.cooper.binge.companion.jellyfin.server.sendGeneralCommand
import com.cooper.binge.companion.jellyfin.server.sendPlaystate
import com.cooper.binge.companion.jellyfin.server.sendSessionMessage
import com.cooper.binge.companion.jellyfin.server.setSessionVolume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the page sends to a session: playback, volume, mute and a message. Every send names the session it was given and
 * only that one, and goes only if the page's [SessionControls] for it allow it, so a control the gates do not offer sends
 * nothing even when called.
 */
internal class SessionRemote(
    private val connection: Connection,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<NowPlayingUiState>,
) {
    fun playPause(session: NowPlaying) =
        send(session, if (session.paused) PlaystateCommand.Unpause else PlaystateCommand.Pause) {
            it.playPause
        }

    fun skip(
        session: NowPlaying,
        next: Boolean,
    ) = send(session, if (next) PlaystateCommand.NextTrack else PlaystateCommand.PreviousTrack) { it.skip }

    /** Moves [session]'s playback by [deltaMillis], within its own length. */
    fun seekBy(
        session: NowPlaying,
        deltaMillis: Long,
    ) = seekTo(session, session.positionTicks + deltaMillis * TICKS_PER_MILLI)

    fun seekTo(
        session: NowPlaying,
        ticks: Long,
    ) {
        val limit = session.runtimeTicks ?: Long.MAX_VALUE
        send(session, PlaystateCommand.Seek, ticks.coerceIn(0, limit)) { it.seek }
    }

    fun setVolume(
        session: NowPlaying,
        level: Int,
    ) = sendCommand(session, { it.volume }) { client -> client.setSessionVolume(session.sessionId, level) }

    /** Mutes a session that is not muted, and unmutes one that is. */
    fun toggleMute(session: NowPlaying) =
        sendCommand(session, { it.mute }) { client ->
            client.sendGeneralCommand(session.sessionId, if (session.muted) "Unmute" else "Mute")
        }

    fun askMessage(session: NowPlaying) =
        state.updateReady { if (controlsFor(session).message) copy(messaging = session.sessionId, messageFailed = null) else this }

    fun cancelMessage() = state.updateReady { copy(messaging = null, messageFailed = null) }

    /**
     * Shows [text] on [session]'s screen, under [header], for [MESSAGE_TIMEOUT_MILLIS]. Text that is blank or too long, or a
     * header too long, sends nothing and keeps the sheet. A send that fails keeps the sheet and what was typed.
     */
    fun sendMessage(
        session: NowPlaying,
        header: String,
        text: String,
    ) {
        val ready = state.value as? NowPlayingUiState.Ready ?: return
        if (!ready.controlsFor(session).message || ready.messageSending) return
        if (!validMessage(header, text)) return
        state.updateReady { copy(messageSending = true, messageFailed = null) }
        scope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.sendSessionMessage(session.sessionId, header, text, MESSAGE_TIMEOUT_MILLIS) }
                .onSuccess { state.updateReady { copy(messageSending = false, messaging = null) } }
                .onFailure { error -> state.updateReady { copy(messageSending = false, messageFailed = error.consoleFailure()) } }
        }
    }

    private fun sendCommand(
        session: NowPlaying,
        allowed: (SessionControls) -> Boolean,
        send: suspend (JellyfinClient) -> Unit,
    ) {
        val ready = state.value as? NowPlayingUiState.Ready ?: return
        if (!allowed(ready.controlsFor(session)) || ready.sending) return
        state.updateReady { copy(sending = true, sendFailed = null) }
        scope.launch {
            connection
                .consoleCall(TAG) { _, client -> send(client) }
                .onSuccess { state.updateReady { copy(sending = false) } }
                .onFailure { error -> state.updateReady { copy(sending = false, sendFailed = error.consoleFailure()) } }
        }
    }

    /** Sends [command] to [session] and no other, and only when the gates offer [allowed] for it: a refused one sends nothing. */
    private fun send(
        session: NowPlaying,
        command: PlaystateCommand,
        seekTicks: Long? = null,
        allowed: (SessionControls) -> Boolean,
    ) {
        val ready = state.value as? NowPlayingUiState.Ready ?: return
        if (!allowed(ready.controlsFor(session)) || ready.sending) return
        state.updateReady { copy(sending = true, sendFailed = null) }
        scope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.sendPlaystate(session.sessionId, command, seekTicks) }
                .onSuccess { state.updateReady { copy(sending = false) } }
                .onFailure { error -> state.updateReady { copy(sending = false, sendFailed = error.consoleFailure()) } }
        }
    }

    private companion object {
        const val TAG = "JellyfinNowPlaying"
    }
}

/** Changes the ready state, and leaves any other state as it is. */
internal fun MutableStateFlow<NowPlayingUiState>.updateReady(change: NowPlayingUiState.Ready.() -> NowPlayingUiState.Ready) =
    update { (it as? NowPlayingUiState.Ready)?.change() ?: it }

/** How long a message stays on the screen it is sent to. */
internal const val MESSAGE_TIMEOUT_MILLIS = 5_000L

internal const val MESSAGE_TEXT_MAX = 300
internal const val MESSAGE_HEADER_MAX = 60

/** Whether [text] and [header] can be sent: the text is there and neither is longer than a screen takes. */
internal fun validMessage(
    header: String,
    text: String,
): Boolean = text.isNotBlank() && text.length <= MESSAGE_TEXT_MAX && header.length <= MESSAGE_HEADER_MAX

/** A tick is 100 nanoseconds, so 10,000 to a millisecond. */
private const val TICKS_PER_MILLI = 10_000L
