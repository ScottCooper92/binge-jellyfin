package com.cooper.binge.companion.jellyfin.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.ui.UiState
import com.cooper.binge.companion.jellyfin.ui.console.PlaybackActions
import com.cooper.binge.companion.jellyfin.ui.console.PlaybackViewModel

/**
 * The playback settings for the TV's signed-in page: the phone's own [PlaybackViewModel], unchanged, read only once a user who
 * may change their preferences (the gate the phone's page uses) is signed in, and null for everyone else, so a user who may
 * not never has the page and nothing is read for them.
 */
@Composable
internal fun rememberTvPlayback(
    graph: AppGraph,
    state: UiState,
): TvPlayback? {
    if ((state as? UiState.SignedIn)?.account?.canEditPreferences != true) return null
    val model: PlaybackViewModel = viewModel(factory = viewModelFactory { initializer { PlaybackViewModel(graph.connection) } })
    val playback by model.state.collectAsStateWithLifecycle()
    val actions =
        remember(model) {
            PlaybackActions(
                onAudio = model::setAudio,
                onSubtitles = model::setSubtitles,
                onSubtitleMode = model::setSubtitleMode,
                onPlayDefaultAudioTrack = model::setPlayDefaultAudioTrack,
                onRememberAudio = model::setRememberAudio,
                onRememberSubtitles = model::setRememberSubtitles,
                onAutoplayNextEpisode = model::setAutoplayNextEpisode,
                onRetry = model::load,
                onRetrySave = model::retrySave,
            )
        }
    return remember(playback, actions) { TvPlayback(playback, actions) }
}
