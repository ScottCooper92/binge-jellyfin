package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.NowPlaying

/**
 * The now-playing page with its own view model, whose event socket closes with the page. A stream opens as an entry above
 * it on the stack ([openItem]), and its screen ([NowPlayingItemRoute]) reads this same view model.
 */
@Composable
internal fun NowPlayingRoute(
    graph: AppGraph,
    account: JellyfinAccount,
    openItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val nowPlaying: NowPlayingViewModel =
        viewModel(factory = viewModelFactory { initializer { NowPlayingViewModel(graph.connection, account) } })
    val state by nowPlaying.state.collectAsStateWithLifecycle()
    NowPlayingScreen(
        state,
        account.isAdministrator,
        nowPlayingActions(
            nowPlaying,
            onSelect = {
                nowPlaying.itemOpened()
                openItem(sessionItem(it.sessionId))
            },
            onBack = onBack,
        ),
    )
}

/** One stream's screen over the page's view model, which its page shares. */
@Composable
internal fun NowPlayingItemRoute(
    graph: AppGraph,
    account: JellyfinAccount,
    item: String,
    onBack: () -> Unit,
) {
    val nowPlaying: NowPlayingViewModel =
        viewModel(factory = viewModelFactory { initializer { NowPlayingViewModel(graph.connection, account) } })
    val state by nowPlaying.state.collectAsStateWithLifecycle()
    NowPlayingSessionScreen(state, sessionItemId(item), nowPlayingActions(nowPlaying, onBack = onBack))
}

private fun nowPlayingActions(
    nowPlaying: NowPlayingViewModel,
    onSelect: (NowPlaying) -> Unit = {},
    onBack: () -> Unit,
) = NowPlayingActions(
    onSelect = onSelect,
    onPlayPause = nowPlaying.remote::playPause,
    onSkip = nowPlaying.remote::skip,
    onSeekBy = nowPlaying.remote::seekBy,
    onSeekTo = nowPlaying.remote::seekTo,
    onVolume = nowPlaying.remote::setVolume,
    onToggleMute = nowPlaying.remote::toggleMute,
    onAskMessage = nowPlaying.remote::askMessage,
    onCancelMessage = nowPlaying.remote::cancelMessage,
    onSendMessage = nowPlaying.remote::sendMessage,
    onStop = nowPlaying::askStop,
    onConfirmStop = nowPlaying::stop,
    onCancelStop = nowPlaying::cancelStop,
    onRetry = nowPlaying::load,
    onBack = onBack,
)
