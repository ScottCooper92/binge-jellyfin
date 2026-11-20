package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.playback.AudioSwitchFailure
import com.cooper.homelab.ephyra.ui.nav.PlayerRoute
import com.cooper.homelab.player.PlayerScreen
import com.cooper.homelab.player.PlayerViewModel
import com.cooper.homelab.player.api.PlayerHostTraits

private const val TABLET_SMALLEST_WIDTH_DP = 600

/**
 * A play's screen, over the page that began it. The player's view model is the sitting's own, kept under its id so a rotation or a
 * picture-in-picture resize keeps the play going, and [onBack] leaves it, which the player reports as the stop.
 */
@Composable
fun PlayerPage(
    graph: AppGraph,
    route: PlayerRoute,
    onBack: () -> Unit,
) {
    val model: PlayerViewModel =
        viewModel(key = "player-${route.sessionId}", factory = graph.playerViewModels(route.sessionId, route.title))
    val phoneSized = LocalConfiguration.current.smallestScreenWidthDp < TABLET_SMALLEST_WIDTH_DP
    PlayerScreen(viewModel = model, hostTraits = PlayerHostTraits(phoneSized = phoneSized), onBack = onBack)
    var failure by remember(route.sessionId) { mutableStateOf<AudioSwitchFailure?>(null) }
    LaunchedEffect(route.sessionId) { graph.playbackSittings.audioSwitchFailures(route.sessionId).collect { failure = it } }
    failure?.let { AudioSwitchMessage(it, onDismiss = { failure = null }) }
    val leave by rememberUpdatedState(onBack)
    LaunchedEffect(route.sessionId) { graph.playbackSittings.endings(route.sessionId).collect { leave() } }
}
