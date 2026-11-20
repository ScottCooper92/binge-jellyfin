package com.cooper.homelab.ephyra.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.template.FormSaveFailureSnackbar
import com.binge.designsystem.template.formSaveStatus
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.ui.console.LanguagePicker
import com.cooper.homelab.ephyra.ui.console.LanguageSheet
import com.cooper.homelab.ephyra.ui.console.PlaybackActions
import com.cooper.homelab.ephyra.ui.console.PlaybackUiState
import com.cooper.homelab.ephyra.ui.console.RefusedMessage
import com.cooper.homelab.ephyra.ui.console.languageItems

/**
 * The Playback group on the This app page: the user's audio language, subtitle language and subtitle mode. They are the server's own
 * user preferences, so the rows, the pickers and the saves are the playback page's (one owner), and each change saves as it is made.
 * Nothing shows until they are read: a read that fails leaves the group out, since there is nothing to change.
 */
@Composable
internal fun PlaybackSettingsGroup(
    state: PlaybackUiState,
    actions: PlaybackActions,
    host: SnackbarHostState,
) {
    val ready = state as? PlaybackUiState.Ready ?: return
    var open by rememberSaveable { mutableStateOf<LanguagePicker?>(null) }
    val languages = languageItems(ready) { open = it }
    ItemGroup(
        title = stringResource(R.string.playback_title),
        rows = listOf(languages.audio, languages.subtitles, languages.subtitleMode),
    )
    open?.let { LanguageSheet(it, ready, actions) { open = null } }
    FormSaveFailureSnackbar(formSaveStatus(ready.save.status, ready.save.failed, actions.onRetrySave), host)
    RefusedMessage(ready.refused, host)
}
