package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchorIf
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.PlayMethod
import com.binge.designsystem.R as DesR

/** What the now-playing page can ask of its view model, and of the console. */
data class NowPlayingActions(
    val onSelect: (NowPlaying) -> Unit = {},
    val onPlayPause: (NowPlaying) -> Unit = {},
    val onSkip: (NowPlaying, Boolean) -> Unit = { _, _ -> },
    val onSeekBy: (NowPlaying, Long) -> Unit = { _, _ -> },
    val onSeekTo: (NowPlaying, Long) -> Unit = { _, _ -> },
    val onVolume: (NowPlaying, Int) -> Unit = { _, _ -> },
    val onToggleMute: (NowPlaying) -> Unit = {},
    val onAskMessage: (NowPlaying) -> Unit = {},
    val onCancelMessage: () -> Unit = {},
    val onSendMessage: (NowPlaying, String, String) -> Unit = { _, _, _ -> },
    val onStop: (NowPlaying) -> Unit = {},
    val onConfirmStop: () -> Unit = {},
    val onCancelStop: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Two lines under a title: the episode, then who and where. */
private const val DETAIL_LINES = 2

/**
 * Who is watching what: each stream's title, who and on which device, how far through, and whether
 * the server plays the file as it is or transcodes it, and why. An [administrator] sees everyone's
 * streams; anyone else their own. A stream the user can control opens its own screen ([NowPlayingSessionScreen]),
 * and [open] is the id of the one open, marked in the list.
 */
@Composable
fun NowPlayingScreen(
    state: NowPlayingUiState,
    administrator: Boolean,
    actions: NowPlayingActions,
    open: String? = LocalOpenItem.current?.let(::sessionItemId),
) {
    // Held above the loading, failed and ready branches, so a reload does not drop where the user was.
    val listState = rememberLazyListState()
    BingeScreenScaffold(
        title = stringResource(R.string.now_playing_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                NowPlayingUiState.Loading ->
                    ConsoleLoading(inner, LOADING_ROWS, intro = stringResource(nowPlayingBody(administrator)), formInset = false)
                is NowPlayingUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is NowPlayingUiState.Ready -> Streams(state, administrator, actions, inner, open, listState)
            }
        }
    }
}

/**
 * One stream's screen: what is playing and how far, and the controls the signed-in user is allowed for it. Stop asks
 * first. A stream that has left the feed closes it.
 */
@Composable
fun NowPlayingSessionScreen(
    state: NowPlayingUiState,
    sessionId: String,
    actions: NowPlayingActions,
) {
    val host = remember { SnackbarHostState() }
    val ready = state as? NowPlayingUiState.Ready
    val session = ready?.sessions?.firstOrNull { it.sessionId == sessionId }
    // A stream that has ended has nothing to control: close it.
    val gone = ready != null && session == null
    LaunchedEffect(gone) { if (gone) actions.onBack() }
    BingeScreenScaffold(
        title = session?.title ?: stringResource(R.string.now_playing_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                NowPlayingUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                is NowPlayingUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is NowPlayingUiState.Ready -> {
                    session?.let { SessionPage(it, state, actions, inner) }
                    state.confirming?.let { StopDialog(it, actions) }
                    state.messaging?.let { id ->
                        state.sessions.firstOrNull { it.sessionId == id }?.let { MessageSheet(it, state, actions) }
                    }
                    RefusedMessage(state.stopFailed, host, R.string.now_playing_not_stopped)
                    RefusedMessage(state.sendFailed, host, R.string.now_playing_not_sent)
                }
            }
        }
    }
}

/** The empty right-hand pane when no stream is open. */
@Composable
internal fun NowPlayingPlaceholder() {
    EmptyScreen(
        message = stringResource(R.string.now_playing_select),
        title = stringResource(R.string.now_playing_select_title),
        icon = Icons.Outlined.PlayCircle,
    )
}

/** The item key of a stream on the stack, and the stream a key is of. */
internal fun sessionItem(sessionId: String) = "session:$sessionId"

internal fun sessionItemId(item: String) = item.removePrefix("session:")

/** The page's intro: an administrator sees everyone's streams, anyone else their own. */
private fun nowPlayingBody(administrator: Boolean) = if (administrator) R.string.now_playing_body_admin else R.string.now_playing_body

@Composable
private fun Streams(
    state: NowPlayingUiState.Ready,
    administrator: Boolean,
    actions: NowPlayingActions,
    inner: PaddingValues,
    open: String?,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        item {
            PageIntro(stringResource(nowPlayingBody(administrator)))
        }
        if (state.sessions.isEmpty()) {
            item { ConsoleEmptyScreen(stringResource(R.string.now_playing_empty), Icons.Outlined.PlayCircle) }
        }
        itemsIndexed(state.sessions, key = { _, session -> session.sessionId }) { index, session ->
            Stream(
                session,
                controllable = state.controlsFor(session).any,
                selected = session.sessionId == open,
                modifier = Modifier.layoutAnchorIf(index == 0, LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
            ) { actions.onSelect(session) }
        }
    }
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

/** One stream in the list. A session the user can control opens its page; any other is read-only. */
@Composable
private fun Stream(
    session: NowPlaying,
    controllable: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val who = stringResource(R.string.now_playing_who, session.userName, session.deviceName, session.client)
    ItemGroup(
        title = null,
        modifier = modifier,
        rows =
            listOf(
                ListItem(
                    icon = if (session.paused) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    label = session.title,
                    detail = listOfNotNull(session.episode, who).joinToString("\n"),
                    supportingMaxLines = DETAIL_LINES,
                    note = ListItemNote(methodText(session), icon = session.method.icon),
                    clickable = controllable,
                    selected = selected,
                    onClick = onOpen,
                ),
            ),
        belowRows = {
            session.progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_m)),
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

private const val LOADING_ROWS = 3
