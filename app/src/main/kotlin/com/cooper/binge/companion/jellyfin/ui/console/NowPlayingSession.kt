package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forward10
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.SessionControls
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

private const val TICKS_PER_SECOND = 10_000_000L
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_MINUTE = 60L
