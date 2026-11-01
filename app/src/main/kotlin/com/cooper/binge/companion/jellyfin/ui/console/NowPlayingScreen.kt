package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.PlayMethod
import com.binge.designsystem.R as DesR

/** What the now-playing page can ask of its view model, and of the console. */
data class NowPlayingActions(
    val onStop: (NowPlaying) -> Unit = {},
    val onConfirmStop: () -> Unit = {},
    val onCancelStop: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Lines a failed stop's message takes, so it never moves the list. */
private const val STATUS_LINES = 2

/** Two lines under a title: the episode, then who and where. */
private const val DETAIL_LINES = 2

/**
 * Who is watching what: each stream's title, who and on which device, how far through, and whether
 * the server plays the file as it is or transcodes it, and why. An [administrator] sees everyone's
 * streams; anyone else their own. Stop asks first.
 */
@Composable
fun NowPlayingScreen(
    state: NowPlayingUiState,
    administrator: Boolean,
    actions: NowPlayingActions,
) {
    BingeScreenScaffold(title = stringResource(R.string.now_playing_title), bar = ScreenBar.Small, onBack = actions.onBack) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                NowPlayingUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
                is NowPlayingUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is NowPlayingUiState.Ready -> Streams(state, administrator, actions, inner)
            }
        }
    }
}

@Composable
private fun Streams(
    state: NowPlayingUiState.Ready,
    administrator: Boolean,
    actions: NowPlayingActions,
    inner: PaddingValues,
) {
    LazyColumn(
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        item {
            Text(
                stringResource(if (administrator) R.string.now_playing_body_admin else R.string.now_playing_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.sessions.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.now_playing_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = dimensionResource(DesR.dimen.padding_l)),
                )
            }
        }
        items(state.sessions, key = { it.sessionId }) { session ->
            Stream(session, stopping = state.stopping == session.sessionId) { actions.onStop(session) }
        }
        item {
            Text(
                text = state.stopFailed?.let { stringResource(R.string.now_playing_not_stopped, stringResource(it.message)) }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                minLines = STATUS_LINES,
                maxLines = STATUS_LINES,
            )
        }
    }
    state.confirming?.let { StopDialog(it, actions) }
}

/** Stopping a stream, after its title, device and user, as a confirm. [inWindow] false draws its body for a frame. */
@Composable
internal fun StopDialog(
    session: NowPlaying,
    actions: NowPlayingActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.now_playing_stop_title, session.title),
        message = stringResource(R.string.now_playing_stop_body, session.deviceName, session.userName),
        confirmLabel = stringResource(R.string.now_playing_stop),
        onConfirm = actions.onConfirmStop,
        onDismissRequest = actions.onCancelStop,
        destructive = true,
        inWindow = inWindow,
    )
}

@Composable
private fun Stream(
    session: NowPlaying,
    stopping: Boolean,
    onStop: () -> Unit,
) {
    val who = stringResource(R.string.now_playing_who, session.userName, session.deviceName, session.client)
    ItemGroup(
        title = null,
        rows =
            listOf(
                ListItem(
                    icon = if (session.paused) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    label = session.title,
                    detail = listOfNotNull(session.episode, who).joinToString("\n"),
                    supportingMaxLines = DETAIL_LINES,
                    note = ListItemNote(methodText(session), icon = session.method.icon),
                    clickable = false,
                ),
            ),
        belowRows = {
            Column(
                modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
            ) {
                session.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                BingeTextButton(
                    label = stringResource(R.string.now_playing_stop),
                    onClick = onStop,
                    leadingIcon = Icons.Outlined.Stop,
                    loading = stopping,
                    destructive = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun methodText(session: NowPlaying): String =
    when (session.method) {
        PlayMethod.DirectPlay -> stringResource(R.string.method_direct_play)
        PlayMethod.DirectStream -> stringResource(R.string.method_direct_stream)
        PlayMethod.Transcode -> stringResource(transcodeReason(session.transcodeReasons))
    }

private val PlayMethod.icon
    get() =
        when (this) {
            PlayMethod.DirectPlay -> Icons.Outlined.Bolt
            PlayMethod.DirectStream -> Icons.Outlined.SwapHoriz
            PlayMethod.Transcode -> Icons.Outlined.Transform
        }

/**
 * Why the server transcodes, from its `TranscodeReasons`, as one line: the reason that costs the
 * most first (burning in subtitles, then the video, the audio, the bitrate, the container).
 */
internal fun transcodeReason(reasons: List<String>): Int =
    when {
        "SubtitleCodecNotSupported" in reasons -> R.string.transcode_subtitles
        reasons.any { it in VIDEO_REASONS } -> R.string.transcode_video
        reasons.any { it in AUDIO_REASONS } -> R.string.transcode_audio
        reasons.any { it in BITRATE_REASONS } -> R.string.transcode_bitrate
        "ContainerNotSupported" in reasons -> R.string.transcode_container
        else -> R.string.transcode_other
    }

private val VIDEO_REASONS =
    setOf(
        "VideoCodecNotSupported",
        "VideoProfileNotSupported",
        "VideoLevelNotSupported",
        "VideoResolutionNotSupported",
        "VideoBitDepthNotSupported",
        "VideoFramerateNotSupported",
        "RefFramesNotSupported",
        "AnamorphicVideoNotSupported",
        "InterlacedVideoNotSupported",
        "VideoRangeTypeNotSupported",
        "VideoCodecTagNotSupported",
        "UnknownVideoStreamInfo",
    )

private val AUDIO_REASONS =
    setOf(
        "AudioCodecNotSupported",
        "AudioIsExternal",
        "SecondaryAudioNotSupported",
        "AudioChannelsNotSupported",
        "AudioProfileNotSupported",
        "AudioSampleRateNotSupported",
        "AudioBitDepthNotSupported",
        "UnknownAudioStreamInfo",
    )

private val BITRATE_REASONS = setOf("ContainerBitrateExceedsLimit", "VideoBitrateNotSupported", "AudioBitrateNotSupported")
