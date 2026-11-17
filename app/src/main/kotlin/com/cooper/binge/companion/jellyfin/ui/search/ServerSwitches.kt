package com.cooper.binge.companion.jellyfin.ui.search

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.template.FormSaveFailureSnackbar
import com.binge.designsystem.template.formSaveStatus
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleFailure
import com.cooper.binge.companion.jellyfin.ui.console.HomeLayoutUiState
import com.cooper.binge.companion.jellyfin.ui.console.HomeLayoutViewModel
import com.cooper.binge.companion.jellyfin.ui.console.PlaybackUiState
import com.cooper.binge.companion.jellyfin.ui.console.PlaybackViewModel
import com.cooper.binge.companion.jellyfin.ui.console.RefusedMessage
import com.cooper.binge.companion.jellyfin.ui.console.SaveProgress
import com.cooper.homelab.core.uiandroid.search.SearchToggle

/**
 * The view models the search has started so far, one per page whose switches a result carries. A page's view model is created
 * when the first result that needs it is shown, so a search that never shows one of its switches reads nothing from the server.
 * It is the page's own view model, the same class with the same save-as-made path, so a flip here is saved, put back and reported
 * exactly as it is on the page ([SearchServerMessages] says so).
 */
@Stable
internal class SearchServerSwitches {
    var playback by mutableStateOf<PlaybackViewModel?>(null)
    var home by mutableStateOf<HomeLayoutViewModel?>(null)
}

/** The rows of the Playback page that carry a switch. */
internal val PLAYBACK_SWITCH_ROWS =
    setOf(
        SettingRows.PLAYBACK_DEFAULT_AUDIO,
        SettingRows.PLAYBACK_REMEMBER_AUDIO,
        SettingRows.PLAYBACK_REMEMBER_SUBTITLES,
        SettingRows.PLAYBACK_AUTOPLAY,
    )

/** The rows of the Home screen page that carry a switch: its display options, not the per-library ones. */
internal val HOME_SWITCH_ROWS = setOf(SettingRows.HOME_HIDE_PLAYED, SettingRows.HOME_MISSING_EPISODES, SettingRows.HOME_COLLECTIONS)

/**
 * The switch for a result whose setting is on a server-backed page of the user's own, or null where [row] is not one. The page's
 * view model is started on first use (see [SearchServerSwitches]).
 */
@Composable
internal fun serverSwitchToggle(
    row: String?,
    graph: AppGraph,
    profile: JellyfinServerProfile,
    server: SearchServerSwitches,
): SearchToggle? =
    when (row) {
        in PLAYBACK_SWITCH_ROWS -> {
            val playback: PlaybackViewModel =
                viewModel(factory = viewModelFactory { initializer { PlaybackViewModel(graph.connection) } })
            LaunchedEffect(playback) { server.playback = playback }
            val state by playback.state.collectAsStateWithLifecycle()
            val set =
                PlaybackSwitches(
                    playback::setPlayDefaultAudioTrack,
                    playback::setRememberAudio,
                    playback::setRememberSubtitles,
                    playback::setAutoplayNextEpisode,
                )
            playbackToggle(row, state, set)
        }
        in HOME_SWITCH_ROWS -> {
            val home: HomeLayoutViewModel =
                viewModel(factory = viewModelFactory { initializer { HomeLayoutViewModel(graph.connection, profile) } })
            LaunchedEffect(home) { server.home = home }
            val state by home.state.collectAsStateWithLifecycle()
            homeToggle(
                row,
                state,
                HomeSwitches(home::setHidePlayedInLatest, home::setDisplayMissingEpisodes, home::setDisplayCollectionsView),
            )
        }
        else -> null
    }

/** What a result's switch does on a server-backed page, from the page's own state: nothing to show until it has loaded. */
internal fun serverSwitch(
    loaded: Boolean,
    failed: Boolean,
    checked: Boolean,
    saving: Boolean,
    set: (Boolean) -> Unit,
): SearchToggle? =
    when {
        // The page opens on its failure and its Retry; a switch that cannot be read is not drawn.
        failed -> null
        // Not read yet: the same spinner as a write in flight, and no tap.
        !loaded -> SearchToggle(checked = false, onCheckedChange = {}, enabled = false, loading = true)
        else -> SearchToggle(checked, set, enabled = true, loading = saving)
    }

/** The setters the Playback page's switches call, so the mapping below does not need a view model to be tested. */
internal class PlaybackSwitches(
    val defaultAudio: (Boolean) -> Unit,
    val rememberAudio: (Boolean) -> Unit,
    val rememberSubtitles: (Boolean) -> Unit,
    val autoplay: (Boolean) -> Unit,
)

internal fun playbackToggle(
    row: String?,
    state: PlaybackUiState,
    set: PlaybackSwitches,
): SearchToggle? {
    val ready = state as? PlaybackUiState.Ready
    val preferences = ready?.preferences
    val (checked, write) =
        when (row) {
            SettingRows.PLAYBACK_DEFAULT_AUDIO -> preferences?.playDefaultAudioTrack to set.defaultAudio
            SettingRows.PLAYBACK_REMEMBER_AUDIO -> preferences?.rememberAudio to set.rememberAudio
            SettingRows.PLAYBACK_REMEMBER_SUBTITLES -> preferences?.rememberSubtitles to set.rememberSubtitles
            SettingRows.PLAYBACK_AUTOPLAY -> preferences?.autoplayNextEpisode to set.autoplay
            else -> return null
        }
    return serverSwitch(ready != null, state is PlaybackUiState.Failed, checked == true, ready?.save.isSaving(), write)
}

/** The setters the Home screen page's display switches call. */
internal class HomeSwitches(
    val hidePlayed: (Boolean) -> Unit,
    val missingEpisodes: (Boolean) -> Unit,
    val collections: (Boolean) -> Unit,
)

internal fun homeToggle(
    row: String?,
    state: HomeLayoutUiState,
    set: HomeSwitches,
): SearchToggle? {
    val ready = state as? HomeLayoutUiState.Ready
    val layout = ready?.layout
    val (checked, write) =
        when (row) {
            SettingRows.HOME_HIDE_PLAYED -> layout?.hidePlayedInLatest to set.hidePlayed
            SettingRows.HOME_MISSING_EPISODES -> layout?.displayMissingEpisodes to set.missingEpisodes
            SettingRows.HOME_COLLECTIONS -> layout?.displayCollectionsView to set.collections
            else -> return null
        }
    return serverSwitch(ready != null, state is HomeLayoutUiState.Failed, checked == true, ready?.save.isSaving(), write)
}

private fun SaveProgress?.isSaving() = this?.status == SaveStatus.Saving

/**
 * What the pages say about a save, which the search must say too, since the user does not leave it: a refusal (the server put the
 * change back) as the page's own message, and a change kept unsent as the page's snackbar with Retry. Both go to [host].
 */
@Composable
internal fun SearchServerMessages(
    server: SearchServerSwitches,
    host: SnackbarHostState,
) {
    server.playback?.let { playback ->
        val state by playback.state.collectAsStateWithLifecycle()
        (state as? PlaybackUiState.Ready)?.let { SaveMessages(it.refused, it.save, playback::retrySave, host) }
    }
    server.home?.let { home ->
        val state by home.state.collectAsStateWithLifecycle()
        (state as? HomeLayoutUiState.Ready)?.let { SaveMessages(it.refused, it.save, home::retrySave, host) }
    }
}

@Composable
private fun SaveMessages(
    refused: ConsoleFailure?,
    save: SaveProgress,
    retry: () -> Unit,
    host: SnackbarHostState,
) {
    RefusedMessage(refused, host)
    FormSaveFailureSnackbar(formSaveStatus(save.status, save.failed, retry), host)
}
