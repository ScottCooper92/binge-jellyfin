package com.cooper.homelab.ephyra.client.libraries

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.FilterControlHandlers
import com.binge.designsystem.template.FilterControlUi
import com.binge.designsystem.template.FilterOptionUi
import com.binge.designsystem.template.FilterRowUi
import com.binge.designsystem.template.FilterSearchUi
import com.binge.designsystem.template.FilterSectionContent
import com.binge.designsystem.template.FilterSectionUi
import com.binge.designsystem.template.FilterSheet
import com.binge.designsystem.template.GridFilterBar
import com.binge.designsystem.template.GridFilterChip
import com.binge.designsystem.template.GridItem
import com.binge.designsystem.template.PagedGridCallbacks
import com.binge.designsystem.template.PagedGridScreen
import com.binge.designsystem.template.PagedGridState
import com.binge.designsystem.template.ScreenAction
import com.cooper.homelab.core.presentation.paging.GridState
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.FilterChoices
import com.cooper.homelab.ephyra.browse.ItemFilters
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemSort
import com.cooper.homelab.ephyra.browse.SortField
import com.cooper.homelab.ephyra.client.R
import com.cooper.homelab.ephyra.client.home.canRetry
import com.cooper.homelab.ephyra.client.home.errorKind

/** What the grid reports to whatever holds its state. */
class LibraryGridActions(
    val onOpenItem: (kind: ItemKind, id: String) -> Unit,
    val onLoadMore: () -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
    val onFiltersOpened: () -> Unit,
    val onApply: (LibraryFilterDraft) -> Unit,
    val onClearChip: (section: String) -> Unit,
    val onClearFilters: () -> Unit,
    val onRefresh: () -> Unit,
)

/** How wide a poster is asked for: a phone column at a high density, which a tablet's column is not wider than. */
private const val POSTER_WIDTH_PX = 400

/** The grid's key for a title: its kind and id, so a tap opens the right page without a second look-up. */
fun libraryItemKey(item: BrowseItem): String = "${item.kind.name}:${item.id}"

/** The kind and id a [libraryItemKey] holds, or null for a key it did not make. */
fun parseLibraryItemKey(key: String): Pair<ItemKind, String>? {
    val kind = ItemKind.entries.firstOrNull { key.startsWith("${it.name}:") } ?: return null
    return kind to key.removePrefix("${kind.name}:")
}

/**
 * A library's titles on the design system's paged grid: the library's name as the title, a pill and chips for the order and filters,
 * and a sheet that holds them. [imageUrl] turns a title and a width in pixels into a URL, or null when it has no picture, so this
 * screen holds no server and no token. A pull down reads the first page again behind the rows, and a refresh that fails says so in a
 * snackbar and keeps them.
 */
@Composable
fun LibraryGridScreen(
    state: LibraryGridUiState,
    imageUrl: (BrowseItem, Int) -> String?,
    actions: LibraryGridActions,
    modifier: Modifier = Modifier,
) {
    val applied = state.controls.applied
    val filtered = applied.appliedCount > 0
    val ready = state as? GridState.Ready
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshFailed = stringResource(R.string.client_library_refresh_failed)
    LaunchedEffect(ready?.refreshFailed) { if (ready?.refreshFailed == true) snackbarHostState.showSnackbar(refreshFailed) }
    PagedGridScreen(
        title = state.controls.libraryName ?: stringResource(R.string.client_libraries_title),
        state = state.toTemplate(imageUrl, filtered, actions.onClearFilters),
        callbacks =
            PagedGridCallbacks(
                onOpenItem = { key -> parseLibraryItemKey(key)?.let { (kind, id) -> actions.onOpenItem(kind, id) } },
                onLoadMore = actions.onLoadMore,
                onRetry = actions.onRetry,
                onClearChip = actions.onClearChip,
                onOpenFilters = actions.onFiltersOpened,
                onBack = actions.onBack,
                onRefresh = actions.onRefresh,
            ),
        modifier = modifier,
        refreshing = ready?.refreshing == true,
        snackbarHostState = snackbarHostState,
        filterBar = applied.toBar(),
        filterSheet = { dismiss -> LibraryFilterSheet(state.controls, actions.onApply, dismiss) },
    )
}

@Composable
private fun LibraryGridUiState.toTemplate(
    imageUrl: (BrowseItem, Int) -> String?,
    filtered: Boolean,
    onClearFilters: () -> Unit,
): PagedGridState {
    val resources = LocalResources.current
    val countLabel = { count: Int -> resources.getQuantityString(R.plurals.client_library_collection_count, count, count) }
    return when (this) {
        is GridState.Loading -> PagedGridState.Loading
        is GridState.Empty ->
            if (filtered) {
                PagedGridState.Empty(
                    message = stringResource(R.string.client_library_empty_filtered),
                    title = stringResource(R.string.client_library_empty_filtered_title),
                    action = ScreenAction(stringResource(R.string.client_library_clear_filters), onClearFilters),
                )
            } else {
                PagedGridState.Empty(stringResource(R.string.client_library_empty))
            }
        is GridState.Error -> PagedGridState.Failed(failure.errorKind(), retryable = failure.canRetry())
        is GridState.Ready ->
            PagedGridState.Ready(
                items = items.map { it.toCell(imageUrl, countLabel) },
                hasMore = hasMore,
                appending = appending,
                appendFailed = appendFailed,
            )
    }
}

private fun BrowseItem.toCell(
    imageUrl: (BrowseItem, Int) -> String?,
    countLabel: (Int) -> String,
): GridItem =
    GridItem(
        key = libraryItemKey(this),
        posterUrl = imageUrl(this, POSTER_WIDTH_PX),
        title = name,
        rating = communityRating?.toFloat(),
        favourite = userData.favourite,
        progress = userData.playedPercentage?.let { (it / PERCENT).toFloat() }?.takeIf { it > 0f && !userData.played },
        subtitle = childCount.takeIf { kind == ItemKind.BoxSet }?.let(countLabel) ?: year?.toString(),
        watched = userData.played,
        unwatchedCount = userData.unplayedCount?.takeIf { it > 0 && kind == ItemKind.Series },
    )

private const val PERCENT = 100.0

/** The pill and the chips for what is applied: each filter that has a value is one chip, and a changed order counts without one. */
@Composable
private fun LibraryFilterDraft.toBar(): GridFilterBar =
    GridFilterBar(
        activeCount = activeCount,
        pillLabel = stringResource(R.string.client_library_filters),
        pillDescription = stringResource(R.string.client_library_filters_description),
        chips =
            buildList {
                if (filters.genres.isNotEmpty()) add(GridFilterChip(LibraryFilterSection.GENRES, filters.genres.sorted().joinToString()))
                if (filters.officialRatings.isNotEmpty()) {
                    add(GridFilterChip(LibraryFilterSection.RATINGS, filters.officialRatings.sorted().joinToString()))
                }
                if (filters.years.isNotEmpty()) add(GridFilterChip(LibraryFilterSection.YEARS, filters.years.sorted().joinToString()))
                filters.played?.let {
                    val label = if (it) R.string.client_library_played_watched else R.string.client_library_played_unwatched
                    add(GridFilterChip(LibraryFilterSection.PLAYED, stringResource(label)))
                }
                if (filters.favouritesOnly) {
                    add(GridFilterChip(LibraryFilterSection.FAVOURITES, stringResource(R.string.client_library_section_favourites)))
                }
            },
    )

/** The sheet: the order and the short filters inline, the long ones (genres, years) as rows that open a page with a search. */
@Composable
private fun LibraryFilterSheet(
    controls: LibraryGridControls,
    onApply: (LibraryFilterDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(controls.applied) }
    // The pill asked the library what it offers when the sheet opened; the sections follow once that answer is in.
    val sections = draft.sections(controls.choices ?: FilterChoices(emptyList(), emptyList(), emptyList()), controls.kind)
    val inline = sections.filter { it.id !in LONG_SECTIONS }.map { it.toUi() }
    val rows = sections.filter { it.id in LONG_SECTIONS }.map { it.toRow() }
    val handlers =
        FilterControlHandlers(
            onSingleSelect = { section, option -> draft = draft.select(section, option) },
            onOptionClick = { section, option -> draft = draft.toggle(section, option) },
        )
    FilterSheet(
        title = stringResource(R.string.client_library_filters),
        clearAllLabel = stringResource(R.string.client_library_clear_all),
        showResultsLabel = stringResource(R.string.client_library_show_results),
        sections = inline,
        onDismiss = onDismiss,
        onApply = {
            onApply(draft)
            onDismiss()
        },
        onClearAll = { draft = draft.clearedAll() },
        appliedLabel = draft.activeCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.client_library_filters_applied, it, it) },
        rows = rows,
        onSingleSelect = handlers.onSingleSelect,
        onOptionClick = handlers.onOptionClick,
        detail = { rowId, nav, onBack ->
            val section = sections.firstOrNull { it.id == rowId }
            if (section != null) {
                var query by remember(rowId) { mutableStateOf("") }
                val chosen = (section as? FilterSection.Multi)?.chosen.orEmpty()
                FilterSectionContent(
                    section = section.toUi(),
                    clearLabel = stringResource(R.string.client_library_clear),
                    clearEnabled = chosen.isNotEmpty(),
                    doneLabel = stringResource(R.string.client_library_done),
                    onClear = { draft = draft.without(rowId) },
                    onDone = nav::pop,
                    handlers = handlers,
                    onBack = onBack,
                    search =
                        FilterSearchUi(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder =
                                stringResource(
                                    if (rowId ==
                                        LibraryFilterSection.YEARS
                                    ) {
                                        R.string.client_library_search_years
                                    } else {
                                        R.string.client_library_search_genres
                                    },
                                ),
                            noResultsLabel = stringResource(R.string.client_library_no_match),
                        ),
                )
            }
        },
    )
}

private val LONG_SECTIONS = setOf(LibraryFilterSection.GENRES, LibraryFilterSection.YEARS)

@Composable
private fun FilterSection.toUi(): FilterSectionUi =
    when (this) {
        is FilterSection.Single ->
            FilterSectionUi(
                id = id,
                title = sectionTitle(id),
                control = FilterControlUi.SingleChoice(options.map { FilterOptionUi(it, optionLabel(id, it)) }, selected),
            )
        is FilterSection.Multi ->
            FilterSectionUi(
                id = id,
                title = sectionTitle(id),
                control = FilterControlUi.MultiChoice(options.map { FilterOptionUi(it, it) }, chosen, emptySet(), includeOnly = true),
            )
    }

@Composable
private fun FilterSection.toRow(): FilterRowUi {
    val chosen = (this as? FilterSection.Multi)?.chosen.orEmpty()
    return FilterRowUi(
        id = id,
        title = sectionTitle(id),
        summary = if (chosen.isEmpty()) stringResource(R.string.client_library_any) else chosen.sorted().joinToString(),
        icon = if (id == LibraryFilterSection.YEARS) Icons.Filled.CalendarMonth else Icons.Filled.Category,
    )
}

@Composable
private fun sectionTitle(id: String): String =
    stringResource(
        when (id) {
            LibraryFilterSection.SORT -> R.string.client_library_section_sort
            LibraryFilterSection.DIRECTION -> R.string.client_library_section_direction
            LibraryFilterSection.PLAYED -> R.string.client_library_section_played
            LibraryFilterSection.FAVOURITES -> R.string.client_library_section_favourites
            LibraryFilterSection.GENRES -> R.string.client_library_section_genres
            LibraryFilterSection.RATINGS -> R.string.client_library_section_ratings
            else -> R.string.client_library_section_years
        },
    )

@Composable
private fun optionLabel(
    section: String,
    option: String,
): String =
    stringResource(
        when (section) {
            LibraryFilterSection.SORT -> sortLabel(option)
            LibraryFilterSection.DIRECTION ->
                if (option == LibraryFilterOption.ASCENDING) R.string.client_library_ascending else R.string.client_library_descending
            LibraryFilterSection.PLAYED ->
                when (PlayedChoice.valueOf(option)) {
                    PlayedChoice.Any -> R.string.client_library_played_any
                    PlayedChoice.Watched -> R.string.client_library_played_watched
                    PlayedChoice.Unwatched -> R.string.client_library_played_unwatched
                }
            else ->
                if (option ==
                    LibraryFilterOption.ALL
                ) {
                    R.string.client_library_favourites_all
                } else {
                    R.string.client_library_favourites_only
                }
        },
    )

private fun sortLabel(option: String): Int =
    when (SortField.valueOf(option)) {
        SortField.DateAdded -> R.string.client_library_sort_date_added
        SortField.ReleaseDate -> R.string.client_library_sort_release_date
        SortField.Rating -> R.string.client_library_sort_rating
        SortField.Runtime -> R.string.client_library_sort_runtime
        SortField.LastPlayed -> R.string.client_library_sort_last_played
        else -> R.string.client_library_sort_name
    }

/** How a [LibraryFilterDraft] is kept across a process death: the order and each filter, as plain text. */
val LibraryFilterDraftSaver: Saver<LibraryFilterDraft, Any> =
    listSaver<LibraryFilterDraft, String>(
        save = { draft ->
            listOf(
                draft.sort.field.name,
                draft.sort.ascending.toString(),
                draft.filters.genres.joinToString(SEPARATOR),
                draft.filters.years.joinToString(SEPARATOR),
                draft.filters.officialRatings.joinToString(SEPARATOR),
                draft.filters.played
                    ?.toString()
                    .orEmpty(),
                draft.filters.favouritesOnly.toString(),
            )
        },
        restore = { saved ->
            val texts = saved

            fun values(index: Int) = texts[index].split(SEPARATOR).filter { it.isNotEmpty() }
            LibraryFilterDraft(
                sort =
                    ItemSort(
                        SortField.entries.firstOrNull { it.name == texts[0] } ?: SortField.Name,
                        texts[1].toBooleanStrictOrNull() ?: true,
                    ),
                filters =
                    ItemFilters(
                        genres = values(2).toSet(),
                        years = values(3).mapNotNull { it.toIntOrNull() }.toSet(),
                        officialRatings = values(4).toSet(),
                        played = texts[5].toBooleanStrictOrNull(),
                        favouritesOnly = texts[6].toBooleanStrictOrNull() ?: false,
                    ),
            )
        },
    )

private const val SEPARATOR = "\n"
