package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.CHAPTER_RESOLUTIONS
import com.cooper.binge.companion.jellyfin.server.DISPLAY_COLLECTIONS
import com.cooper.binge.companion.jellyfin.server.DISPLAY_COLLECTIONS_MOVIES
import com.cooper.binge.companion.jellyfin.server.DISPLAY_COLLECTIONS_SHOWS
import com.cooper.binge.companion.jellyfin.server.DISPLAY_EXTERNAL_SUGGESTIONS
import com.cooper.binge.companion.jellyfin.server.DISPLAY_FOLDER_VIEW
import com.cooper.binge.companion.jellyfin.server.DISPLAY_SPECIALS_IN_SEASONS
import com.cooper.binge.companion.jellyfin.server.DISPLAY_UI_CULTURE
import com.cooper.binge.companion.jellyfin.server.DisplayList
import com.cooper.binge.companion.jellyfin.server.IMAGE_SAVING_CONVENTIONS
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.ListEntry
import com.cooper.binge.companion.jellyfin.server.METADATA_CHAPTER_RESOLUTION
import com.cooper.binge.companion.jellyfin.server.METADATA_CONVENTION
import com.cooper.binge.companion.jellyfin.server.METADATA_DUMMY_CHAPTER
import com.cooper.binge.companion.jellyfin.server.METADATA_HIDDEN
import com.cooper.binge.companion.jellyfin.server.METADATA_PATH
import com.cooper.binge.companion.jellyfin.server.bool
import com.cooper.binge.companion.jellyfin.server.entries
import com.cooper.binge.companion.jellyfin.server.int
import com.cooper.binge.companion.jellyfin.server.string
import com.binge.designsystem.R as DesR

/** What the library display page can ask of its view model, and of the console. */
data class LibraryDisplayActions(
    val onFlag: (key: String, on: Boolean) -> Unit = { _, _ -> },
    val onText: (key: String, text: String) -> Unit = { _, _ -> },
    val onSeconds: (Int) -> Unit = {},
    val onSheet: (DisplaySheet?) -> Unit = {},
    val onEdit: (DisplayList?) -> Unit = {},
    val onAdd: (ListEntry) -> Unit = {},
    val onRemove: (Int) -> Unit = {},
    val onSave: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

private const val LOADING_ROWS = 8
private const val DETAIL_LINES = 4
private const val SUMMARY_ITEMS = 4

private enum class DisplayPicker { Language, Convention, Resolution }

/**
 * The server's library display and metadata defaults: how libraries show, what metadata is saved and where,
 * how titles sort, and path substitutions. Each switch and choice saves as it's made; a list has an editor
 * page with its own Save. [profile] says which settings this release has.
 */
@Composable
fun LibraryDisplayScreen(
    state: LibraryDisplayUiState,
    actions: LibraryDisplayActions,
    profile: JellyfinServerProfile,
    folders: FolderBrowseState = FolderBrowseState.Loading,
    folderActions: FolderActions = FolderActions(),
) {
    val ready = state as? LibraryDisplayUiState.Ready
    val list = ready?.editing
    BackHandler(enabled = list != null) { actions.onEdit(null) }
    BingeScreenScaffold(
        title = list?.let { stringResource(it.title()) } ?: stringResource(R.string.display_title),
        bar = ScreenBar.Small,
        onBack = if (list != null) ({ actions.onEdit(null) }) else actions.onBack,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                LibraryDisplayUiState.Loading -> ListRowSkeletonColumn(contentPadding = inner, count = LOADING_ROWS)
                is LibraryDisplayUiState.Failed -> ConsoleFailed(state.failure, actions.onRetry, inner)
                is LibraryDisplayUiState.Ready -> {
                    if (list == null) DisplayBody(state, actions, profile, inner) else ListEditor(list, state, actions, inner)
                    DisplaySheets(state, actions, folders, folderActions)
                }
            }
        }
    }
}

@Composable
private fun DisplayBody(
    state: LibraryDisplayUiState.Ready,
    actions: LibraryDisplayActions,
    profile: JellyfinServerProfile,
    inner: PaddingValues,
) {
    var picker by rememberSaveable { mutableStateOf<DisplayPicker?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        Text(stringResource(R.string.display_body), style = MaterialTheme.typography.bodyMedium)
        ItemGroup(title = stringResource(R.string.display_group), rows = displayRows(state, actions, profile) { picker = it })
        ItemGroup(title = stringResource(R.string.display_metadata), rows = metadataRows(state, actions, profile) { picker = it })
        ItemGroup(
            title = stringResource(R.string.display_sorting),
            rows = listRows(state, actions, listOf(DisplayList.RemoveWords, DisplayList.RemoveCharacters, DisplayList.ReplaceCharacters)),
        )
        ItemGroup(title = stringResource(R.string.display_paths), rows = listRows(state, actions, listOf(DisplayList.Substitutions)))
        FailureText(state.saveFailed?.let { stringResource(R.string.settings_not_saved, stringResource(it.message)) })
    }
    picker?.let { DisplayPickerSheet(it, state, actions) { picker = null } }
}

@Composable
private fun switchRow(
    state: LibraryDisplayUiState.Ready,
    key: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    actions: LibraryDisplayActions,
    detail: Int? = null,
): ListItem {
    val on = state.configuration.bool(key) ?: false
    return ListItem(
        icon = icon,
        label = stringResource(label),
        detail = detail?.let { stringResource(it) },
        supportingMaxLines = DETAIL_LINES,
        toggled = on,
        onClick = { actions.onFlag(key, !on) },
    )
}

@Composable
private fun displayRows(
    state: LibraryDisplayUiState.Ready,
    actions: LibraryDisplayActions,
    profile: JellyfinServerProfile,
    onPick: (DisplayPicker) -> Unit,
): List<ListItem> {
    val culture = state.configuration.string(DISPLAY_UI_CULTURE).orEmpty()
    // Before 10.11 one switch groups movies and shows; from 10.11 each has its own, and the old key is gone.
    val collections =
        if (profile.hasSeparateCollectionGrouping) {
            listOf(
                switchRow(
                    state,
                    DISPLAY_COLLECTIONS_MOVIES,
                    Icons.Outlined.CollectionsBookmark,
                    R.string.display_collections_movies,
                    actions,
                ),
                switchRow(
                    state,
                    DISPLAY_COLLECTIONS_SHOWS,
                    Icons.Outlined.CollectionsBookmark,
                    R.string.display_collections_shows,
                    actions,
                ),
            )
        } else {
            listOf(
                switchRow(
                    state,
                    DISPLAY_COLLECTIONS,
                    Icons.Outlined.CollectionsBookmark,
                    R.string.display_collections,
                    actions,
                    R.string.display_collections_detail,
                ),
            )
        }
    return listOf(
        ListItem(
            icon = Icons.Outlined.Translate,
            label = stringResource(R.string.display_language),
            detail = state.languages.firstOrNull { it.value.equals(culture, true) }?.name ?: culture,
            clickable = state.languages.isNotEmpty(),
            onClick = { onPick(DisplayPicker.Language) },
        ),
        switchRow(
            state,
            DISPLAY_FOLDER_VIEW,
            Icons.Outlined.FolderOpen,
            R.string.display_folder_view,
            actions,
            R.string.display_folder_view_detail,
        ),
        switchRow(
            state,
            DISPLAY_SPECIALS_IN_SEASONS,
            Icons.Outlined.Tv,
            R.string.display_specials,
            actions,
            R.string.display_specials_detail,
        ),
        switchRow(
            state,
            DISPLAY_EXTERNAL_SUGGESTIONS,
            Icons.Outlined.Explore,
            R.string.display_external,
            actions,
            R.string.display_external_detail,
        ),
    ) + collections
}

@Composable
private fun metadataRows(
    state: LibraryDisplayUiState.Ready,
    actions: LibraryDisplayActions,
    profile: JellyfinServerProfile,
    onPick: (DisplayPicker) -> Unit,
): List<ListItem> {
    val config = state.configuration
    val seconds = config.int(METADATA_DUMMY_CHAPTER) ?: 0
    return listOfNotNull(
        switchRow(state, METADATA_HIDDEN, Icons.Outlined.VisibilityOff, R.string.display_hidden, actions, R.string.display_hidden_detail),
        ListItem(
            icon = Icons.Outlined.Image,
            label = stringResource(R.string.display_convention),
            detail = conventionLabel(config.string(METADATA_CONVENTION) ?: IMAGE_SAVING_CONVENTIONS.first()),
            onClick = { onPick(DisplayPicker.Convention) },
        ),
        ListItem(
            icon = Icons.Outlined.Folder,
            label = stringResource(R.string.display_path),
            detail = config.string(METADATA_PATH)?.ifBlank { null } ?: stringResource(R.string.display_path_default),
            onClick = { actions.onSheet(DisplaySheet.MetadataPath) },
        ),
        ListItem(
            icon = Icons.Outlined.HighQuality,
            label = stringResource(R.string.display_chapter_resolution),
            detail = resolutionLabel(config.string(METADATA_CHAPTER_RESOLUTION) ?: CHAPTER_RESOLUTIONS.first()),
            onClick = { onPick(DisplayPicker.Resolution) },
        ).takeIf { profile.hasChapterImageSettings },
        ListItem(
            icon = Icons.Outlined.Timer,
            label = stringResource(R.string.display_dummy_chapters),
            detail =
                if (seconds == 0) {
                    stringResource(R.string.display_dummy_chapters_off)
                } else {
                    stringResource(R.string.display_dummy_chapters_every, seconds)
                },
            onClick = { actions.onSheet(DisplaySheet.ChapterDuration) },
        ).takeIf { profile.hasChapterImageSettings },
    )
}

@Composable
private fun listRows(
    state: LibraryDisplayUiState.Ready,
    actions: LibraryDisplayActions,
    lists: List<DisplayList>,
): List<ListItem> =
    lists.map { list ->
        val entries = state.configuration.entries(list)
        ListItem(
            icon = if (list.pairs) Icons.Outlined.SwapHoriz else Icons.Outlined.SortByAlpha,
            label = stringResource(list.title()),
            detail =
                if (entries.isEmpty()) {
                    stringResource(R.string.display_list_empty)
                } else {
                    entries.take(SUMMARY_ITEMS).joinToString(", ") { it.shown() } + if (entries.size > SUMMARY_ITEMS) "…" else ""
                },
            onClick = { actions.onEdit(list) },
        )
    }

/** An entry as a row shows it: the word, or `from → to`. */
internal fun ListEntry.shown(): String = if (second == null) first else "$first → $second"

internal fun DisplayList.title(): Int =
    when (this) {
        DisplayList.RemoveWords -> R.string.display_remove_words
        DisplayList.RemoveCharacters -> R.string.display_remove_characters
        DisplayList.ReplaceCharacters -> R.string.display_replace_characters
        DisplayList.Substitutions -> R.string.display_substitutions
    }

@Composable
private fun conventionLabel(value: String): String =
    when (value) {
        "Legacy" -> stringResource(R.string.display_convention_legacy)
        "Compatible" -> stringResource(R.string.display_convention_compatible)
        else -> value
    }

@Composable
private fun resolutionLabel(value: String): String =
    if (value == "MatchSource") stringResource(R.string.display_resolution_source) else value.removePrefix("P") + "p"

@Composable
private fun DisplayPickerSheet(
    picker: DisplayPicker,
    state: LibraryDisplayUiState.Ready,
    actions: LibraryDisplayActions,
    onDismiss: () -> Unit,
) {
    val config = state.configuration
    when (picker) {
        DisplayPicker.Language ->
            BingeChoiceSheet(
                title = stringResource(R.string.display_language),
                choices = BingeChoiceList.Ready(state.languages.map { BingeChoice(it.value, it.name) }),
                selected = state.languages.firstOrNull { it.value.equals(config.string(DISPLAY_UI_CULTURE), true) }?.value,
                onSelect = { actions.onText(DISPLAY_UI_CULTURE, it) },
                onDismissRequest = onDismiss,
            )
        DisplayPicker.Convention ->
            BingeChoiceSheet(
                title = stringResource(R.string.display_convention),
                choices = BingeChoiceList.Ready(IMAGE_SAVING_CONVENTIONS.map { BingeChoice(it, conventionLabel(it)) }),
                selected = config.string(METADATA_CONVENTION) ?: IMAGE_SAVING_CONVENTIONS.first(),
                onSelect = { actions.onText(METADATA_CONVENTION, it) },
                onDismissRequest = onDismiss,
            )
        DisplayPicker.Resolution ->
            BingeChoiceSheet(
                title = stringResource(R.string.display_chapter_resolution),
                choices = BingeChoiceList.Ready(CHAPTER_RESOLUTIONS.map { BingeChoice(it, resolutionLabel(it)) }),
                selected = config.string(METADATA_CHAPTER_RESOLUTION) ?: CHAPTER_RESOLUTIONS.first(),
                onSelect = { actions.onText(METADATA_CHAPTER_RESOLUTION, it) },
                onDismissRequest = onDismiss,
            )
    }
}
