package com.cooper.binge.companion.jellyfin.ui.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.template.TvPaneGroup
import com.binge.designsystem.tv.template.TvPaneOption
import com.binge.designsystem.tv.template.TvPaneRow
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.PlaybackPreferences
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import com.cooper.binge.companion.jellyfin.ui.console.ANY
import com.cooper.binge.companion.jellyfin.ui.console.PlaybackActions
import com.cooper.binge.companion.jellyfin.ui.console.PlaybackUiState
import com.cooper.binge.companion.jellyfin.ui.console.find
import com.cooper.binge.companion.jellyfin.ui.console.label
import com.cooper.binge.companion.jellyfin.ui.console.suggestedLanguages
import com.cooper.binge.companion.jellyfin.ui.console.text
import java.util.Locale
import com.cooper.binge.companion.jellyfin.ui.console.detail as subtitleModeDetail
import com.cooper.binge.companion.jellyfin.ui.console.label as subtitleModeLabel

/** What the TV's signed-in page needs to show and change the user's own playback settings: the phone's state and its actions. */
class TvPlayback(
    val state: PlaybackUiState,
    val actions: PlaybackActions,
    /** The row whose save the status is for before the user changes one: a frame's seed, as the board's own. */
    val changed: String? = null,
)

/**
 * The user's playback settings as a group of the list-and-pane board: a row for each choice, with its options in the pane, and a
 * switch is a row of one option that flips it. Each change saves as it is made, and the pane says how the save stands; the TV has
 * no snackbar, so a failure stays in the pane with Retry. A page that has not read them says so, and offers Try again if it
 * could not.
 */
@Composable
internal fun playbackGroup(playback: TvPlayback): TvPaneGroup {
    val title = stringResource(R.string.playback_title)
    val actions = playback.actions
    return when (val state = playback.state) {
        PlaybackUiState.Loading ->
            TvPaneGroup(title, listOf(TvPaneRow(key = LOADING_KEY, label = stringResource(R.string.tv_loading), loading = true)))
        is PlaybackUiState.Failed ->
            TvPaneGroup(
                title,
                listOf(
                    TvPaneRow(
                        key = FAILED_KEY,
                        label = stringResource(R.string.playback_title),
                        body = state.failure.text(),
                        options =
                            listOf(
                                TvPaneOption(label = stringResource(R.string.tv_try_again), action = true, onSelect = actions.onRetry),
                            ),
                    ),
                ),
            )
        is PlaybackUiState.Ready -> TvPaneGroup(title, readyRows(state, actions, playback.changed))
    }
}

@Composable
private fun readyRows(
    state: PlaybackUiState.Ready,
    actions: PlaybackActions,
    seed: String?,
): List<TvPaneRow> {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    val suggested = suggestedLanguages(state.cultures, LocalConfiguration.current.locales)
    val any = stringResource(R.string.languages_any)
    val prefs = state.preferences
    val refusal = state.refused?.text()
    val status =
        when {
            refusal != null -> SaveStatus.Refused(stringResource(R.string.settings_not_saved, refusal))
            state.save.failed -> SaveStatus.Failed
            else -> state.save.status
        }
    // The save status belongs to the row the user just changed, so it is shown there and nowhere else.
    var changed by rememberSaveable { mutableStateOf(seed) }
    val on = stringResource(R.string.library_provider_on)
    val off = stringResource(R.string.library_provider_off)

    fun language(
        key: String,
        label: String,
        current: String,
        pick: (String) -> Unit,
    ) = TvPaneRow(
        key = key,
        label = label,
        icon = Icons.Outlined.Language,
        detail = if (current == ANY) any else state.cultures.find(current)?.label(locale) ?: current,
        options =
            languageOptions(state.cultures, suggested, current, any, locale) { value ->
                changed = key
                pick(value)
            },
    )

    fun switch(
        key: String,
        label: String,
        body: String,
        value: Boolean,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        change: (Boolean) -> Unit,
    ) = TvPaneRow(
        key = key,
        label = label,
        body = body,
        icon = icon,
        detail = if (value) on else off,
        multiSelect = true,
        // The row's own label is the pane's title, so the one option is the setting's state: ticked while it is on.
        options =
            listOf(
                TvPaneOption(
                    label = on,
                    selected = value,
                    onSelect = {
                        changed = key
                        change(!value)
                    },
                ),
            ),
    )

    val rows =
        listOf(
            language(AUDIO_KEY, stringResource(R.string.languages_audio), prefs.audio, actions.onAudio),
            language(SUBTITLES_KEY, stringResource(R.string.languages_subtitles), prefs.subtitles, actions.onSubtitles),
            subtitleModeRow(prefs) {
                changed = SUBTITLE_MODE_KEY
                actions.onSubtitleMode(it)
            },
            switch(
                DEFAULT_AUDIO_KEY,
                stringResource(R.string.playback_default_audio),
                stringResource(R.string.playback_default_audio_detail),
                prefs.playDefaultAudioTrack,
                Icons.Outlined.VolumeUp,
                actions.onPlayDefaultAudioTrack,
            ),
            switch(
                REMEMBER_AUDIO_KEY,
                stringResource(R.string.playback_remember_audio),
                stringResource(R.string.playback_remember_detail),
                prefs.rememberAudio,
                Icons.Outlined.VolumeUp,
                actions.onRememberAudio,
            ),
            switch(
                REMEMBER_SUBTITLES_KEY,
                stringResource(R.string.playback_remember_subtitles),
                stringResource(R.string.playback_remember_detail),
                prefs.rememberSubtitles,
                Icons.Outlined.ClosedCaption,
                actions.onRememberSubtitles,
            ),
            switch(
                AUTOPLAY_KEY,
                stringResource(R.string.playback_autoplay),
                "",
                prefs.autoplayNextEpisode,
                Icons.Outlined.SkipNext,
                actions.onAutoplayNextEpisode,
            ),
        )
    return rows.map { row ->
        if (row.key == changed) row.copy(saveStatus = status, onRetry = actions.onRetrySave) else row
    }
}

@Composable
private fun subtitleModeRow(
    prefs: PlaybackPreferences,
    onMode: (SubtitleMode) -> Unit,
): TvPaneRow =
    TvPaneRow(
        key = SUBTITLE_MODE_KEY,
        label = stringResource(R.string.languages_subtitle_mode),
        icon = Icons.Outlined.Translate,
        detail = stringResource(prefs.subtitleMode.subtitleModeLabel),
        options =
            SubtitleMode.entries.map { mode ->
                TvPaneOption(
                    label = stringResource(mode.subtitleModeLabel),
                    selected = mode == prefs.subtitleMode,
                    onSelect = { onMode(mode) },
                )
            },
        body = stringResource(prefs.subtitleMode.subtitleModeDetail),
    )

/** "Any language", then the device's own and the popular ones, then every other language the server offers, A to Z. */
private fun languageOptions(
    cultures: List<Culture>,
    suggested: List<String>,
    current: String,
    any: String,
    locale: Locale,
    pick: (String) -> Unit,
): List<TvPaneOption> {
    val first = suggested.mapNotNull { code -> cultures.find(code) }
    val rest = cultures.filterNot { culture -> first.any { it.code == culture.code } }.sortedBy { it.label(locale) }
    return listOf(TvPaneOption(label = any, selected = current == ANY, onSelect = { pick(ANY) })) +
        (first + rest).distinctBy { it.code }.map { culture ->
            TvPaneOption(label = culture.label(locale), selected = current in culture.codes, onSelect = { pick(culture.code) })
        }
}

internal const val LOADING_KEY = "playback-loading"
internal const val FAILED_KEY = "playback-failed"
internal const val AUDIO_KEY = "audio"
private const val SUBTITLES_KEY = "subtitles"
private const val SUBTITLE_MODE_KEY = "subtitleMode"
private const val DEFAULT_AUDIO_KEY = "defaultAudio"
private const val REMEMBER_AUDIO_KEY = "rememberAudio"
private const val REMEMBER_SUBTITLES_KEY = "rememberSubtitles"
internal const val AUTOPLAY_KEY = "autoplay"
