package com.cooper.binge.companion.jellyfin.ui.search

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.template.FormSaveFailureSnackbar
import com.binge.designsystem.template.formSaveStatus
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.ui.console.RefusedMessage
import com.cooper.homelab.core.uiandroid.search.SearchToggle

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

/** The switches of the user's own pages, on one view model: [SearchSwitchesViewModel] says why there is one. */
@Composable
internal fun searchSwitchesModel(
    graph: AppGraph,
    onModel: (SearchSwitchesViewModel) -> Unit,
): SearchSwitchesViewModel {
    val model: SearchSwitchesViewModel =
        viewModel(factory = viewModelFactory { initializer { SearchSwitchesViewModel(graph.connection) } })
    // The route reads it afresh, once, each time the search comes to the front: it is told which model there is.
    LaunchedEffect(model) { onModel(model) }
    return model
}

/**
 * The switch for a result whose setting is on a server-backed page of the user's own, or null where [row] is not one. The view
 * model starts, and reads the configuration, the first time a result that needs it is shown.
 */
@Composable
internal fun serverSwitchToggle(
    row: String?,
    graph: AppGraph,
    onModel: (SearchSwitchesViewModel) -> Unit,
): SearchToggle? {
    if (row !in PLAYBACK_SWITCH_ROWS && row !in HOME_SWITCH_ROWS) return null
    val model = searchSwitchesModel(graph, onModel)
    val state by model.state.collectAsStateWithLifecycle()
    return serverToggle(row, state, model.switches())
}

/** The setters the seven switches call, so the mapping below does not need a view model to be tested. */
internal class ServerSwitches(
    val defaultAudio: (Boolean) -> Unit,
    val rememberAudio: (Boolean) -> Unit,
    val rememberSubtitles: (Boolean) -> Unit,
    val autoplay: (Boolean) -> Unit,
    val hidePlayed: (Boolean) -> Unit,
    val missingEpisodes: (Boolean) -> Unit,
    val collections: (Boolean) -> Unit,
)

private fun SearchSwitchesViewModel.switches() =
    ServerSwitches(
        ::setPlayDefaultAudioTrack,
        ::setRememberAudio,
        ::setRememberSubtitles,
        ::setAutoplayNextEpisode,
        ::setHidePlayedInLatest,
        ::setDisplayMissingEpisodes,
        ::setDisplayCollectionsView,
    )

/**
 * What a result's switch says, from the one copy of the configuration: the value and the setter of its row, a spinner until the
 * configuration has been read and while a write is in flight, and no switch at all if it could not be read (the page opens on its
 * own failure and Retry).
 */
internal fun serverToggle(
    row: String?,
    state: SearchSwitchesState,
    set: ServerSwitches,
): SearchToggle? {
    val ready = state as? SearchSwitchesState.Ready
    val (checked, write) =
        when (row) {
            SettingRows.PLAYBACK_DEFAULT_AUDIO -> ready?.playback?.playDefaultAudioTrack to set.defaultAudio
            SettingRows.PLAYBACK_REMEMBER_AUDIO -> ready?.playback?.rememberAudio to set.rememberAudio
            SettingRows.PLAYBACK_REMEMBER_SUBTITLES -> ready?.playback?.rememberSubtitles to set.rememberSubtitles
            SettingRows.PLAYBACK_AUTOPLAY -> ready?.playback?.autoplayNextEpisode to set.autoplay
            SettingRows.HOME_HIDE_PLAYED -> ready?.home?.hidePlayedInLatest to set.hidePlayed
            SettingRows.HOME_MISSING_EPISODES -> ready?.home?.displayMissingEpisodes to set.missingEpisodes
            SettingRows.HOME_COLLECTIONS -> ready?.home?.displayCollectionsView to set.collections
            else -> return null
        }
    return when {
        // The page opens on its failure and its Retry; a switch that cannot be read is not drawn.
        state is SearchSwitchesState.Failed -> null
        // Not read yet: the same spinner as a write in flight, and no tap.
        ready == null -> SearchToggle(checked = false, onCheckedChange = {}, enabled = false, loading = true)
        else -> SearchToggle(checked == true, write, enabled = true, loading = ready.save.status == SaveStatus.Saving)
    }
}

/**
 * What the pages say about a save, which the search must say too, since the user does not leave it: a refusal (the server put the
 * change back) as the page's own message, and a change kept unsent as the page's snackbar with Retry. Both go to [host].
 */
@Composable
internal fun SearchServerMessages(
    model: SearchSwitchesViewModel?,
    host: SnackbarHostState,
) {
    model ?: return
    val state by model.state.collectAsStateWithLifecycle()
    (state as? SearchSwitchesState.Ready)?.let {
        RefusedMessage(it.refused, host)
        FormSaveFailureSnackbar(formSaveStatus(it.save.status, it.save.failed, model::retrySave), host)
    }
}
