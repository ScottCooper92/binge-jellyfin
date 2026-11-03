package com.cooper.binge.companion.jellyfin.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.server.Connection
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
    connection: Connection,
    state: UiState,
): TvPlayback? {
    val signedIn = state as? UiState.SignedIn
    if (signedIn?.account?.canEditPreferences != true) return null
    val model = playbackModelFor(signedIn.session.baseUrl, signedIn.session.userId, connection)
    return rememberTvPlayback(model)
}

/**
 * The model lives in a store of its own, kept only while this server and user are signed in: leaving the composition (sign-out)
 * clears it, and a different user gets a new one, so no one is shown, or saves over, another's configuration.
 */
@Composable
private fun playbackModelFor(
    baseUrl: String,
    userId: String,
    connection: Connection,
): PlaybackViewModel {
    val store = remember(baseUrl, userId) { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }
    val owner =
        remember(store) {
            object : ViewModelStoreOwner {
                override val viewModelStore = store
            }
        }
    return viewModel(viewModelStoreOwner = owner, factory = viewModelFactory { initializer { PlaybackViewModel(connection) } })
}

@Composable
private fun rememberTvPlayback(model: PlaybackViewModel): TvPlayback {
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
