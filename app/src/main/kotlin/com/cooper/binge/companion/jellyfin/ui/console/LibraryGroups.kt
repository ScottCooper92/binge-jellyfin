package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.LIBRARY_CHAPTER_IMAGES
import com.cooper.binge.companion.jellyfin.server.LIBRARY_CHAPTER_IMAGES_ON_SCAN
import com.cooper.binge.companion.jellyfin.server.LIBRARY_COLLECTIONS
import com.cooper.binge.companion.jellyfin.server.LIBRARY_COUNTRY
import com.cooper.binge.companion.jellyfin.server.LIBRARY_EMBEDDED_EPISODES
import com.cooper.binge.companion.jellyfin.server.LIBRARY_EMBEDDED_TITLES
import com.cooper.binge.companion.jellyfin.server.LIBRARY_LANGUAGE
import com.cooper.binge.companion.jellyfin.server.LIBRARY_LOUDNESS
import com.cooper.binge.companion.jellyfin.server.LIBRARY_LYRICS_WITH_MEDIA
import com.cooper.binge.companion.jellyfin.server.LIBRARY_PERFECT_MATCH
import com.cooper.binge.companion.jellyfin.server.LIBRARY_PHOTOS
import com.cooper.binge.companion.jellyfin.server.LIBRARY_REALTIME
import com.cooper.binge.companion.jellyfin.server.LIBRARY_REFRESH_DAYS
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SAVE_LOCAL
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SAVE_SUBTITLES
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SEASON_ZERO
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SERIES_GROUPING
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SKIP_AUDIO_MATCH
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SKIP_EMBEDDED_SUBTITLES
import com.cooper.binge.companion.jellyfin.server.LIBRARY_TRICKPLAY
import com.cooper.binge.companion.jellyfin.server.LIBRARY_TRICKPLAY_ON_SCAN
import com.cooper.binge.companion.jellyfin.server.LIBRARY_TRICKPLAY_WITH_MEDIA
import com.cooper.binge.companion.jellyfin.server.ProviderGroup
import com.cooper.binge.companion.jellyfin.server.bool
import com.cooper.binge.companion.jellyfin.server.int
import com.cooper.binge.companion.jellyfin.server.libraryHasMovies
import com.cooper.binge.companion.jellyfin.server.libraryHasShows
import com.cooper.binge.companion.jellyfin.server.libraryHasVideo
import com.cooper.binge.companion.jellyfin.server.providers
import com.cooper.binge.companion.jellyfin.server.string

/** The choices a library's page opens in a sheet. */
internal enum class LibraryPicker { Language, Country, Refresh }

/** The refresh intervals offered, in days; 0 is never. The library's own is offered too, if it is another. */
private val REFRESH_PRESETS = listOf(0, 7, 14, 30, 60, 90)

/** A switch row for a library option, off when the library doesn't have the key. */
@Composable
private fun LibraryUiState.Ready.switch(
    key: String,
    icon: ImageVector,
    label: Int,
    actions: LibraryActions,
    detail: Int? = null,
): ListItem {
    val on = options.bool(key) ?: false
    return ListItem(
        icon = icon,
        label = stringResource(label),
        detail = detail?.let { stringResource(it) },
        supportingMaxLines = LIBRARY_DETAIL_LINES,
        toggled = on,
        onClick = { actions.onFlag(key, !on) },
    )
}

/** The row that opens a group's providers, saying how many of them the library uses. */
@Composable
private fun LibraryUiState.Ready.providersRow(
    group: ProviderGroup,
    icon: ImageVector,
    actions: LibraryActions,
): ListItem? {
    val all = options.providers(group, group.available(available))
    if (all.isEmpty()) return null
    return ListItem(
        icon = icon,
        label = stringResource(group.title()),
        detail = stringResource(R.string.library_providers_on, all.count { it.enabled }, all.size),
        onClick = { actions.onPage(LibraryPage.Providers(group)) },
    )
}

@Composable
internal fun MetadataGroup(
    state: LibraryUiState.Ready,
    actions: LibraryActions,
    onPick: (LibraryPicker) -> Unit,
) {
    val kind = state.library.type
    val options = state.options
    val notSet = stringResource(R.string.library_default)
    val days = options.int(LIBRARY_REFRESH_DAYS) ?: 0
    ItemGroup(
        title = stringResource(R.string.library_metadata),
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Outlined.Translate,
                    label = stringResource(R.string.library_language),
                    detail =
                        state.cultures.firstOrNull { it.twoLetter.equals(options.string(LIBRARY_LANGUAGE), true) }?.displayName
                            ?: options.string(LIBRARY_LANGUAGE)?.ifBlank { null } ?: notSet,
                    onClick = { onPick(LibraryPicker.Language) },
                ),
                ListItem(
                    icon = Icons.Outlined.Public,
                    label = stringResource(R.string.library_country),
                    detail =
                        state.countries.firstOrNull { it.code.equals(options.string(LIBRARY_COUNTRY), true) }?.displayName
                            ?: options.string(LIBRARY_COUNTRY)?.ifBlank { null } ?: notSet,
                    onClick = { onPick(LibraryPicker.Country) },
                ),
                ListItem(
                    icon = Icons.Outlined.Update,
                    label = stringResource(R.string.library_refresh),
                    detail = refreshLabel(days),
                    onClick = { onPick(LibraryPicker.Refresh) },
                ),
                state.switch(
                    LIBRARY_REALTIME,
                    Icons.Outlined.Visibility,
                    R.string.library_realtime,
                    actions,
                    R.string.library_realtime_detail,
                ),
                state.switch(
                    LIBRARY_SAVE_LOCAL,
                    Icons.Outlined.Save,
                    R.string.library_save_local,
                    actions,
                    R.string.library_save_local_detail,
                ),
                state
                    .switch(LIBRARY_EMBEDDED_TITLES, Icons.Outlined.Title, R.string.library_embedded_titles, actions)
                    .takeIf { libraryHasVideo(kind) },
                state
                    .switch(LIBRARY_EMBEDDED_EPISODES, Icons.Outlined.ListAlt, R.string.library_embedded_episodes, actions)
                    .takeIf { libraryHasShows(kind) },
                state
                    .switch(
                        LIBRARY_SERIES_GROUPING,
                        Icons.Outlined.MergeType,
                        R.string.library_series_grouping,
                        actions,
                        R.string.library_series_grouping_detail,
                    ).takeIf { libraryHasShows(kind) },
                ListItem(
                    icon = Icons.Outlined.Tag,
                    label = stringResource(R.string.library_season_zero),
                    detail = options.string(LIBRARY_SEASON_ZERO)?.ifBlank { null } ?: notSet,
                    onClick = { actions.onAsk(LibraryDialog.SeasonZero) },
                ).takeIf { libraryHasShows(kind) },
                state
                    .switch(
                        LIBRARY_COLLECTIONS,
                        Icons.Outlined.CollectionsBookmark,
                        R.string.library_collections,
                        actions,
                        R.string.library_collections_detail,
                    ).takeIf { libraryHasMovies(kind) },
                state.switch(LIBRARY_PHOTOS, Icons.Outlined.Photo, R.string.library_photos, actions).takeIf { kind == "homevideos" },
                state.providersRow(ProviderGroup.Readers, Icons.Outlined.Description, actions),
            ),
    )
}

@Composable
internal fun VideoGroups(
    state: LibraryUiState.Ready,
    actions: LibraryActions,
    profile: JellyfinServerProfile,
) {
    if (!libraryHasVideo(state.library.type)) return
    ItemGroup(
        title = stringResource(R.string.library_subtitles),
        rows =
            listOfNotNull(
                state.switch(LIBRARY_SKIP_EMBEDDED_SUBTITLES, Icons.Outlined.ClosedCaptionOff, R.string.library_skip_embedded, actions),
                state.switch(LIBRARY_SKIP_AUDIO_MATCH, Icons.Outlined.Hearing, R.string.library_skip_audio, actions),
                state.switch(LIBRARY_PERFECT_MATCH, Icons.Outlined.Verified, R.string.library_perfect_match, actions),
                state.switch(LIBRARY_SAVE_SUBTITLES, Icons.Outlined.Subtitles, R.string.library_save_subtitles, actions),
                state.providersRow(ProviderGroup.Subtitles, Icons.Outlined.CloudDownload, actions),
            ),
    )
    ItemGroup(
        title = stringResource(R.string.library_images),
        rows =
            listOf(
                state.switch(LIBRARY_CHAPTER_IMAGES, Icons.Outlined.ViewTimeline, R.string.library_chapter_images, actions),
                state.switch(LIBRARY_CHAPTER_IMAGES_ON_SCAN, Icons.Outlined.Sync, R.string.library_on_scan, actions),
            ),
    )
    if (profile.hasTrickplay) {
        ItemGroup(
            title = stringResource(R.string.library_trickplay),
            rows =
                listOfNotNull(
                    state.switch(LIBRARY_TRICKPLAY, Icons.Outlined.Timeline, R.string.library_trickplay_extract, actions),
                    state.switch(LIBRARY_TRICKPLAY_ON_SCAN, Icons.Outlined.Sync, R.string.library_on_scan, actions),
                    state
                        .switch(LIBRARY_TRICKPLAY_WITH_MEDIA, Icons.Outlined.Save, R.string.library_trickplay_with_media, actions)
                        .takeIf { profile.hasLibraryTrickplayWithMedia },
                ),
        )
    }
}

@Composable
internal fun MusicGroups(
    state: LibraryUiState.Ready,
    actions: LibraryActions,
    profile: JellyfinServerProfile,
) {
    if (state.library.type != "music") return
    val rows =
        listOfNotNull(
            state
                .switch(LIBRARY_LOUDNESS, Icons.Outlined.GraphicEq, R.string.library_loudness, actions, R.string.library_loudness_detail)
                .takeIf { profile.hasLoudnessScan },
            state
                .switch(
                    LIBRARY_LYRICS_WITH_MEDIA,
                    Icons.Outlined.Lyrics,
                    R.string.library_lyrics_with_media,
                    actions,
                ).takeIf { profile.hasLyrics },
            state.providersRow(ProviderGroup.Lyrics, Icons.Outlined.CloudDownload, actions).takeIf { profile.hasLibraryLyricFetchers },
        )
    if (rows.isNotEmpty()) ItemGroup(title = stringResource(R.string.library_music), rows = rows)
}

@Composable
internal fun SegmentsGroup(
    state: LibraryUiState.Ready,
    actions: LibraryActions,
    profile: JellyfinServerProfile,
) {
    if (!profile.hasLibrarySegmentProviders || !libraryHasVideo(state.library.type)) return
    val row = state.providersRow(ProviderGroup.Segments, Icons.Outlined.SkipNext, actions) ?: return
    ItemGroup(title = stringResource(R.string.library_segments), rows = listOf(row))
}

/** The interval as people say it. */
@Composable
internal fun refreshLabel(days: Int): String =
    if (days <= 0) stringResource(R.string.library_never) else pluralStringResource(R.plurals.library_every_days, days, days)

@Composable
internal fun LibraryPickerSheet(
    picker: LibraryPicker,
    state: LibraryUiState.Ready,
    actions: LibraryActions,
    onDismiss: () -> Unit,
) {
    val default = stringResource(R.string.library_default)
    val locales = LocalConfiguration.current.locales
    when (picker) {
        LibraryPicker.Language -> {
            val choices =
                remember(state.cultures, default) {
                    listOf(BingeChoice("", default, icon = Icons.Outlined.Translate)) +
                        state.cultures
                            .filter { it.twoLetter.isNotBlank() }
                            .distinctBy { it.twoLetter.lowercase() }
                            .map { BingeChoice(it.twoLetter, it.displayName, mark = it.mark()) }
                }
            val suggested =
                remember(choices, locales) {
                    val device =
                        (0 until locales.size()).mapNotNull { i ->
                            choices.firstOrNull { it.value.equals(locales[i].language, true) }?.value
                        }
                    val popular = POPULAR_LANGUAGES.mapNotNull { code -> choices.firstOrNull { it.value.equals(code, true) }?.value }
                    suggestions(device, popular, choices.map { it.value }.toSet())
                }
            BingeChoiceSheet(
                title = stringResource(R.string.library_language),
                choices = BingeChoiceList.Ready(choices),
                selected = choices.firstOrNull { it.value.equals(state.options.string(LIBRARY_LANGUAGE).orEmpty(), true) }?.value ?: "",
                onSelect = { actions.onText(LIBRARY_LANGUAGE, it) },
                onDismissRequest = onDismiss,
                suggested = suggested,
                pinned = listOf(""),
            )
        }
        LibraryPicker.Country -> {
            val choices =
                remember(state.countries, default) {
                    listOf(BingeChoice("", default, icon = Icons.Outlined.Public)) +
                        state.countries.map { BingeChoice(it.code, it.displayName, mark = it.code) }
                }
            val suggested =
                remember(choices, locales) {
                    val device =
                        (0 until locales.size()).mapNotNull { i ->
                            choices.firstOrNull { it.value.equals(locales[i].country, true) }?.value
                        }
                    val popular = POPULAR_REGIONS.mapNotNull { code -> choices.firstOrNull { it.value.equals(code, true) }?.value }
                    suggestions(device, popular, choices.map { it.value }.toSet())
                }
            BingeChoiceSheet(
                title = stringResource(R.string.library_country),
                choices = BingeChoiceList.Ready(choices),
                selected = choices.firstOrNull { it.value.equals(state.options.string(LIBRARY_COUNTRY).orEmpty(), true) }?.value ?: "",
                onSelect = { actions.onText(LIBRARY_COUNTRY, it) },
                onDismissRequest = onDismiss,
                suggested = suggested,
                pinned = listOf(""),
            )
        }
        LibraryPicker.Refresh -> {
            val current = state.options.int(LIBRARY_REFRESH_DAYS) ?: 0
            val choices =
                (REFRESH_PRESETS + current).distinct().sorted().map { BingeChoice(it.toString(), refreshLabel(it)) }
            BingeChoiceSheet(
                title = stringResource(R.string.library_refresh),
                choices = BingeChoiceList.Ready(choices),
                selected = current.toString(),
                onSelect = { value -> value.toIntOrNull()?.let(actions.onDays) },
                onDismissRequest = onDismiss,
            )
        }
    }
}
