package com.cooper.binge.companion.jellyfin.ui.console

import android.os.LocaleList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.flags.LocalFlags
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import java.util.Locale
import com.binge.designsystem.R as DesR

/** What the playback page can ask of its view model, and of the console. */
data class PlaybackActions(
    val onAudio: (String) -> Unit = {},
    val onSubtitles: (String) -> Unit = {},
    val onSubtitleMode: (SubtitleMode) -> Unit = {},
    val onPlayDefaultAudioTrack: (Boolean) -> Unit = {},
    val onRememberAudio: (Boolean) -> Unit = {},
    val onRememberSubtitles: (Boolean) -> Unit = {},
    val onAutoplayNextEpisode: (Boolean) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** The picker open over the page. */
enum class LanguagePicker { Audio, Subtitles, SubtitleMode }

/** The value that stands for "any language" in the user's configuration. */
private const val ANY = ""

/** Lines a failed save's message takes, so it never moves the rows. */
private const val STATUS_LINES = 2

/**
 * The user's playback settings: the audio and subtitle language Jellyfin prefers when it chooses
 * tracks, when it turns subtitles on, whether a track picked by hand is remembered, and whether the
 * next episode plays. Each change saves as it's made.
 */
@Composable
fun PlaybackScreen(
    state: PlaybackUiState,
    actions: PlaybackActions,
) {
    var open by rememberSaveable { mutableStateOf<LanguagePicker?>(null) }
    BingeScreenScaffold(title = stringResource(R.string.playback_title), bar = ScreenBar.Small, onBack = actions.onBack) { padding ->
        ScreenBody(padding) { _ ->
            when (state) {
                PlaybackUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
                is PlaybackUiState.Failed -> ConsoleFailed(state.failure, actions.onRetry, padding.screenListPadding())
                is PlaybackUiState.Ready -> {
                    PlaybackBody(state, actions, padding.screenListPadding()) { open = it }
                    open?.let { LanguageSheet(it, state, actions) { open = null } }
                }
            }
        }
    }
}

@Composable
private fun PlaybackBody(
    state: PlaybackUiState.Ready,
    actions: PlaybackActions,
    inner: PaddingValues,
    onOpen: (LanguagePicker) -> Unit,
) {
    val locale = currentLocale()
    val any = stringResource(R.string.languages_any)

    fun named(code: String) = if (code == ANY) any else state.cultures.find(code)?.label(locale) ?: code
    val preferences = state.preferences
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        val body = if (LocalFlags.current.bingePromotion) R.string.languages_body else R.string.languages_body_plain
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        ItemGroup(
            title = stringResource(R.string.playback_audio),
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.Audiotrack,
                        label = stringResource(R.string.languages_audio),
                        detail = named(preferences.audio),
                        onClick = { onOpen(LanguagePicker.Audio) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.GraphicEq,
                        label = stringResource(R.string.playback_default_audio),
                        detail = stringResource(R.string.playback_default_audio_detail),
                        toggled = preferences.playDefaultAudioTrack,
                        onClick = { actions.onPlayDefaultAudioTrack(!preferences.playDefaultAudioTrack) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.History,
                        label = stringResource(R.string.playback_remember_audio),
                        detail = stringResource(R.string.playback_remember_detail),
                        toggled = preferences.rememberAudio,
                        onClick = { actions.onRememberAudio(!preferences.rememberAudio) },
                    ),
                ),
        )
        ItemGroup(
            title = stringResource(R.string.playback_subtitles),
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.Subtitles,
                        label = stringResource(R.string.languages_subtitles),
                        detail = named(preferences.subtitles),
                        onClick = { onOpen(LanguagePicker.Subtitles) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.ClosedCaption,
                        label = stringResource(R.string.languages_subtitle_mode),
                        detail = stringResource(preferences.subtitleMode.label),
                        onClick = { onOpen(LanguagePicker.SubtitleMode) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.Restore,
                        label = stringResource(R.string.playback_remember_subtitles),
                        detail = stringResource(R.string.playback_remember_detail),
                        toggled = preferences.rememberSubtitles,
                        onClick = { actions.onRememberSubtitles(!preferences.rememberSubtitles) },
                    ),
                ),
        )
        ItemGroup(
            title = stringResource(R.string.playback_episodes),
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.SkipNext,
                        label = stringResource(R.string.playback_autoplay),
                        toggled = preferences.autoplayNextEpisode,
                        onClick = { actions.onAutoplayNextEpisode(!preferences.autoplayNextEpisode) },
                    ),
                ),
        )
        Text(
            text = state.saveFailed?.let { stringResource(R.string.languages_not_saved, stringResource(it.message)) }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            minLines = STATUS_LINES,
            maxLines = STATUS_LINES,
        )
    }
}

@Composable
private fun LanguageSheet(
    picker: LanguagePicker,
    state: PlaybackUiState.Ready,
    actions: PlaybackActions,
    onDismiss: () -> Unit,
) {
    when (picker) {
        LanguagePicker.Audio ->
            LanguageChoiceSheet(
                R.string.languages_audio,
                state.cultures,
                state.preferences.audio,
                actions.onAudio,
                onDismiss,
            )
        LanguagePicker.Subtitles ->
            LanguageChoiceSheet(R.string.languages_subtitles, state.cultures, state.preferences.subtitles, actions.onSubtitles, onDismiss)
        LanguagePicker.SubtitleMode ->
            BingeChoiceSheet(
                title = stringResource(R.string.languages_subtitle_mode),
                choices =
                    BingeChoiceList.Ready(
                        SubtitleMode.entries.map {
                            BingeChoice(it, stringResource(it.label), supportingText = stringResource(it.detail), icon = it.icon)
                        },
                    ),
                selected = state.preferences.subtitleMode,
                onSelect = actions.onSubtitleMode,
                onDismissRequest = onDismiss,
            )
    }
}

/** A pick from every language the server knows, "any" first and the device's own suggested. */
@Composable
private fun LanguageChoiceSheet(
    title: Int,
    cultures: List<Culture>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale()
    val any = stringResource(R.string.languages_any)
    val choices =
        remember(cultures, locale, any) {
            listOf(BingeChoice(ANY, any, icon = Icons.Outlined.Language)) +
                cultures
                    .map { BingeChoice(it.code, it.label(locale), mark = it.mark()) }
                    .sortedBy { it.label.lowercase(locale) }
        }
    val locales = LocalConfiguration.current.locales
    val suggested = remember(cultures, locales) { deviceLanguages(cultures, locales) }
    BingeChoiceSheet(
        title = stringResource(title),
        choices = BingeChoiceList.Ready(choices),
        // A code the server stores under another of the language's names still shows as chosen.
        selected = cultures.find(selected)?.code ?: selected,
        onSelect = onSelect,
        onDismissRequest = onDismiss,
        suggested = suggested,
        pinned = listOf(ANY),
    )
}

/** A console page that couldn't read: what went wrong, and Try again, where focus can land. */
@Composable
internal fun ConsoleFailed(
    failure: ConsoleFailure,
    onRetry: () -> Unit,
    inner: PaddingValues,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l), Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(failure.message), style = MaterialTheme.typography.bodyLarge)
        BingeFilledButton(label = stringResource(R.string.console_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT

/** The culture stored as [code], under any of its three-letter names. */
internal fun List<Culture>.find(code: String): Culture? = firstOrNull { code in it.codes }

/** The language's name in [locale] where Android knows it, a region included, else the server's English name. */
internal fun Culture.label(locale: Locale): String {
    val known = Locale.forLanguageTag(twoLetter).takeIf { twoLetter.isNotBlank() && it.language.isNotEmpty() }
    // Android names a language it doesn't know by its code, which is no name at all.
    val local = known?.takeUnless { it.getDisplayLanguage(locale).equals(it.language, ignoreCase = true) }?.getDisplayName(locale)
    val name = local ?: displayName
    return name.replaceFirstChar { it.titlecase(locale) }
}

/** The short mark a language's row carries: its two-letter code where it has one. */
internal fun Culture.mark(): String = (twoLetter.takeIf { it.length == 2 } ?: code).uppercase(Locale.ROOT)

/** The server's codes for the device's own languages, in the device's order. */
internal fun deviceLanguages(
    cultures: List<Culture>,
    locales: LocaleList,
): List<String> =
    (0 until locales.size())
        .mapNotNull { index -> locales[index]?.let { locale -> cultures.find(iso3Language(locale))?.code } }
        .distinct()

/** A locale's three-letter language, or empty where Android has none. */
private fun iso3Language(locale: Locale): String =
    try {
        locale.isO3Language
    } catch (_: java.util.MissingResourceException) {
        ""
    }

private val SubtitleMode.label: Int
    get() =
        when (this) {
            SubtitleMode.Default -> R.string.subtitle_mode_default
            SubtitleMode.Smart -> R.string.subtitle_mode_smart
            SubtitleMode.OnlyForced -> R.string.subtitle_mode_forced
            SubtitleMode.Always -> R.string.subtitle_mode_always
            SubtitleMode.None -> R.string.subtitle_mode_none
        }

private val SubtitleMode.detail: Int
    get() =
        when (this) {
            SubtitleMode.Default -> R.string.subtitle_mode_default_detail
            SubtitleMode.Smart -> R.string.subtitle_mode_smart_detail
            SubtitleMode.OnlyForced -> R.string.subtitle_mode_forced_detail
            SubtitleMode.Always -> R.string.subtitle_mode_always_detail
            SubtitleMode.None -> R.string.subtitle_mode_none_detail
        }

private val SubtitleMode.icon: ImageVector
    get() =
        when (this) {
            SubtitleMode.Default -> Icons.Outlined.Description
            SubtitleMode.Smart -> Icons.Outlined.Translate
            SubtitleMode.OnlyForced -> Icons.Outlined.PriorityHigh
            SubtitleMode.Always -> Icons.Outlined.ClosedCaption
            SubtitleMode.None -> Icons.Outlined.ClosedCaptionDisabled
        }
