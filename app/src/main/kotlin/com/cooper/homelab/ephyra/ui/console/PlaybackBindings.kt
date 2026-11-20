package com.cooper.homelab.ephyra.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.homelab.ephyra.AppGraph

/** What the playback page, and the same settings under This app, ask of their one view model. */
internal fun PlaybackViewModel.actions(onBack: () -> Unit = {}) =
    PlaybackActions(
        onAudio = ::setAudio,
        onSubtitles = ::setSubtitles,
        onSubtitleMode = ::setSubtitleMode,
        onPlayDefaultAudioTrack = ::setPlayDefaultAudioTrack,
        onRememberAudio = ::setRememberAudio,
        onRememberSubtitles = ::setRememberSubtitles,
        onAutoplayNextEpisode = ::setAutoplayNextEpisode,
        onRetry = ::load,
        onRetrySave = ::retrySave,
        onBack = onBack,
    )

/** The playback view model's state and what it is asked, the same code path as the playback page. */
@Composable
internal fun thisAppPlayback(graph: AppGraph): Pair<PlaybackUiState, PlaybackActions> {
    val playback: PlaybackViewModel =
        viewModel(factory = viewModelFactory { initializer { PlaybackViewModel(graph.connection) } })
    val state by playback.state.collectAsStateWithLifecycle()
    return state to playback.actions()
}
