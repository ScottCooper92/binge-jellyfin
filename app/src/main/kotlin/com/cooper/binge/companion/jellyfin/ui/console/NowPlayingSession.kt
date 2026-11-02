package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.SessionControls
import com.cooper.binge.companion.jellyfin.server.TICKS_PER_SECOND
import java.util.Locale
import com.binge.designsystem.R as DesR

/** How far the back and forward buttons move playback. */
private const val SKIP_MILLIS = 10_000L

/**
 * One session's page: what is playing and how far, and the controls the signed-in user is allowed for it. Only the
 * controls in [SessionControls] are drawn; Stop is last, red, and asks first.
 */
@Composable
internal fun SessionPage(
    session: NowPlaying,
    state: NowPlayingUiState.Ready,
    actions: NowPlayingActions,
    inner: PaddingValues,
) {
    val controls = state.controlsFor(session)
    LazyColumn(
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        item {
            val who = stringResource(R.string.now_playing_who, session.userName, session.deviceName, session.client)
            ItemGroup(
                title = null,
                rows =
                    listOf(
                        ListItem(
                            icon = if (session.paused) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            label = session.title,
                            detail = listOfNotNull(session.episode, who).joinToString("\n"),
                            supportingMaxLines = 2,
                            clickable = false,
                        ),
                    ),
            )
        }
        item { Position(session, controls, state.sending, actions) }
        if (controls.playPause || controls.seek || controls.skip) item { Transport(session, controls, state.sending, actions) }
        if (controls.volume || controls.mute) item { Volume(session, controls, state.sending, actions) }
        if (controls.message) {
            item {
                BingeOutlinedButton(
                    label = stringResource(R.string.now_playing_message),
                    onClick = { actions.onAskMessage(session) },
                    leadingIcon = Icons.Outlined.Message,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (controls.stop) {
            item {
                BingeTextButton(
                    label = stringResource(R.string.now_playing_stop),
                    onClick = { actions.onStop(session) },
                    leadingIcon = Icons.Outlined.Stop,
                    loading = state.stopping == session.sessionId,
                    destructive = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The position and length, and a bar to move it where the session seeks. */
@Composable
private fun Position(
    session: NowPlaying,
    controls: SessionControls,
    sending: Boolean,
    actions: NowPlayingActions,
) {
    val runtime = session.runtimeTicks?.takeIf { it > 0 } ?: return
    var dragging by remember(session.sessionId) { mutableFloatStateOf(-1f) }
    val shown = if (dragging >= 0f) dragging else session.positionTicks.toFloat() / runtime
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        if (controls.seek) {
            Slider(
                value = shown.coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    if (dragging >= 0f) actions.onSeekTo(session, (dragging * runtime).toLong())
                    dragging = -1f
                },
                enabled = !sending,
            )
        }
        Text(
            stringResource(R.string.now_playing_position, formatTicks((shown * runtime).toLong()), formatTicks(runtime)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Previous, back 10 seconds, play or pause, forward 10 seconds, next: each only where the session takes it. */
@Composable
private fun Transport(
    session: NowPlaying,
    controls: SessionControls,
    sending: Boolean,
    actions: NowPlayingActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensionResource(DesR.dimen.padding_s)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (controls.skip) {
            ExpressiveIconButton(
                onClick = { actions.onSkip(session, false) },
                icon = Icons.Outlined.SkipPrevious,
                contentDescription = stringResource(R.string.now_playing_previous),
                enabled = !sending,
            )
        }
        if (controls.seek) {
            ExpressiveIconButton(
                onClick = { actions.onSeekBy(session, -SKIP_MILLIS) },
                icon = Icons.Outlined.Replay10,
                contentDescription = stringResource(R.string.now_playing_back_10),
                enabled = !sending,
            )
        }
        if (controls.playPause) {
            ExpressiveIconButton(
                onClick = { actions.onPlayPause(session) },
                icon = if (session.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                contentDescription = stringResource(if (session.paused) R.string.now_playing_play else R.string.now_playing_pause),
                enabled = !sending,
            )
        }
        if (controls.seek) {
            ExpressiveIconButton(
                onClick = { actions.onSeekBy(session, SKIP_MILLIS) },
                icon = Icons.Outlined.Forward10,
                contentDescription = stringResource(R.string.now_playing_forward_10),
                enabled = !sending,
            )
        }
        if (controls.skip) {
            ExpressiveIconButton(
                onClick = { actions.onSkip(session, true) },
                icon = Icons.Outlined.SkipNext,
                contentDescription = stringResource(R.string.now_playing_next),
                enabled = !sending,
            )
        }
    }
}

/** The volume bar and the mute button, each only where the session takes its command. */
@Composable
private fun Volume(
    session: NowPlaying,
    controls: SessionControls,
    sending: Boolean,
    actions: NowPlayingActions,
) {
    var dragging by remember(session.sessionId) { mutableFloatStateOf(-1f) }
    val level = (if (dragging >= 0f) dragging else (session.volumeLevel ?: 0).toFloat()).coerceIn(0f, MAX_VOLUME)
    val volumeLabel = stringResource(R.string.now_playing_volume)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        if (controls.mute) {
            ExpressiveIconButton(
                onClick = { actions.onToggleMute(session) },
                icon = if (session.muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = stringResource(if (session.muted) R.string.now_playing_unmute else R.string.now_playing_mute),
                enabled = !sending,
            )
        }
        if (controls.volume) {
            Slider(
                value = level,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    if (dragging >= 0f) actions.onVolume(session, dragging.toInt())
                    dragging = -1f
                },
                valueRange = 0f..MAX_VOLUME,
                enabled = !sending,
                modifier = Modifier.weight(1f).semantics { contentDescription = volumeLabel },
            )
        }
    }
}

/** What goes on a session's screen: a title, which may be left empty, and the text. [inWindow] false draws the form for a frame. */
@Composable
internal fun MessageSheet(
    session: NowPlaying,
    state: NowPlayingUiState.Ready,
    actions: NowPlayingActions,
    inWindow: Boolean = true,
) {
    var header by rememberSaveable(key = "MessageSheet.header") { mutableStateOf("") }
    var text by rememberSaveable(key = "MessageSheet.text") { mutableStateOf("") }
    val submitting = state.messageSending
    ConsoleSheet(onDismissRequest = actions.onCancelMessage, inWindow = inWindow) {
        ConsoleFieldsForm(
            title = stringResource(R.string.now_playing_message),
            body = stringResource(R.string.now_playing_message_body, session.userName, session.deviceName),
            submitLabel = stringResource(R.string.now_playing_message_send),
            onSubmit = { if (validMessage(header, text)) actions.onSendMessage(session, header, text) },
            onCancel = actions.onCancelMessage,
            submitting = submitting,
            error = state.messageFailed?.let { stringResource(R.string.now_playing_not_sent, it.text()) },
        ) {
            ConsoleTextField(
                value = header,
                onValueChange = { header = it.take(MESSAGE_HEADER_MAX) },
                label = stringResource(R.string.now_playing_message_header),
                enabled = !submitting,
                imeAction = ImeAction.Next,
            )
            ConsoleTextField(
                value = text,
                onValueChange = { text = it.take(MESSAGE_TEXT_MAX) },
                label = stringResource(R.string.now_playing_message_text),
                enabled = !submitting,
                singleLine = false,
                minLines = 3,
            )
        }
    }
}

private const val MAX_VOLUME = 100f

/** Ticks (100 ns) as `h:mm:ss`, or `m:ss` under an hour. */
internal fun formatTicks(ticks: Long): String {
    val seconds = (ticks.coerceAtLeast(0) / TICKS_PER_SECOND)
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return if (hours >
        0
    ) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, rest)
    }
}

private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_MINUTE = 60L
