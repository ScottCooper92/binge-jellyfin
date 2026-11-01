package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HideSource
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.UserView
import com.binge.designsystem.R as DesR

/** What the home screen page can ask of its view model, and of the console. */
data class HomeLayoutActions(
    val onOpen: (String?) -> Unit = {},
    val onInMyMedia: (String, Boolean) -> Unit = { _, _ -> },
    val onInLatest: (String, Boolean) -> Unit = { _, _ -> },
    val onGrouped: (String, Boolean) -> Unit = { _, _ -> },
    val onMove: (String, Int) -> Unit = { _, _ -> },
    val onHidePlayedInLatest: (Boolean) -> Unit = {},
    val onDisplayMissingEpisodes: (Boolean) -> Unit = {},
    val onDisplayCollectionsView: (Boolean) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Lines a switch's detail may wrap to, so it reads in full at large fonts. */
private const val HOME_DETAIL_LINES = 3

/** Lines a failed save's message takes, so it never moves the rows. */
private const val HOME_STATUS_LINES = 2

/**
 * How Jellyfin's apps lay out the user's home: their libraries in order, each with its own page for
 * where it shows and its place in the order, and the display switches. Each change saves as it's made.
 */
@Composable
fun HomeLayoutScreen(
    state: HomeLayoutUiState,
    actions: HomeLayoutActions,
) {
    val library = (state as? HomeLayoutUiState.Ready)?.openLibrary
    BackHandler(enabled = library != null) { actions.onOpen(null) }
    BingeScreenScaffold(
        title = library?.name ?: stringResource(R.string.home_layout_title),
        bar = ScreenBar.Small,
        onBack = if (library != null) ({ actions.onOpen(null) }) else actions.onBack,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                HomeLayoutUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
                is HomeLayoutUiState.Failed -> ConsoleFailed(state.failure, actions.onRetry, inner)
                is HomeLayoutUiState.Ready ->
                    if (library == null) HomeLayoutBody(state, actions, inner) else LibraryPage(state, library, actions, inner)
            }
        }
    }
}

@Composable
private fun HomeLayoutBody(
    state: HomeLayoutUiState.Ready,
    actions: HomeLayoutActions,
    inner: PaddingValues,
) {
    val layout = state.layout
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        Text(stringResource(R.string.home_layout_body), style = MaterialTheme.typography.bodyMedium)
        ItemGroup(
            title = stringResource(R.string.home_layout_libraries),
            rows =
                state.libraries
                    .map { library ->
                        ListItem(
                            icon = library.icon(),
                            label = library.name,
                            detail = stringResource(library.whereShown(state)),
                            onClick = { actions.onOpen(library.id) },
                        )
                    }.ifEmpty {
                        listOf(
                            ListItem(
                                icon = Icons.Outlined.VideoLibrary,
                                label = stringResource(R.string.home_layout_no_libraries),
                                clickable = false,
                            ),
                        )
                    },
        )
        ItemGroup(
            title = stringResource(R.string.home_layout_display),
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.NewReleases,
                        label = stringResource(R.string.home_layout_hide_played),
                        toggled = layout.hidePlayedInLatest,
                        onClick = { actions.onHidePlayedInLatest(!layout.hidePlayedInLatest) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.HideSource,
                        label = stringResource(R.string.home_layout_missing),
                        detail = stringResource(R.string.home_layout_missing_detail),
                        supportingMaxLines = HOME_DETAIL_LINES,
                        toggled = layout.displayMissingEpisodes,
                        onClick = { actions.onDisplayMissingEpisodes(!layout.displayMissingEpisodes) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.CollectionsBookmark,
                        label = stringResource(R.string.home_layout_collections),
                        detail = stringResource(R.string.home_layout_collections_detail),
                        supportingMaxLines = HOME_DETAIL_LINES,
                        toggled = layout.displayCollectionsView,
                        onClick = { actions.onDisplayCollectionsView(!layout.displayCollectionsView) },
                    ),
                ),
        )
        SaveStatus(state.saveFailed)
    }
}

/** One library's page: where it shows on the home, whether it's grouped, and its place in the order. */
@Composable
private fun LibraryPage(
    state: HomeLayoutUiState.Ready,
    library: UserView,
    actions: HomeLayoutActions,
    inner: PaddingValues,
) {
    val layout = state.layout
    val inMyMedia = library.id !in layout.myMediaExcludes
    val inLatest = library.id !in layout.latestExcludes
    val grouped = library.id in layout.grouped
    val place = state.libraries.indexOf(library)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        ItemGroup(
            title = stringResource(R.string.home_layout_where),
            rows =
                listOfNotNull(
                    ListItem(
                        icon = Icons.Outlined.ViewModule,
                        label = stringResource(R.string.home_layout_in_my_media),
                        toggled = inMyMedia,
                        onClick = { actions.onInMyMedia(library.id, !inMyMedia) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.NewReleases,
                        label = stringResource(R.string.home_layout_in_latest),
                        toggled = inLatest,
                        onClick = { actions.onInLatest(library.id, !inLatest) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.Workspaces,
                        label = stringResource(R.string.home_layout_grouped),
                        detail = stringResource(R.string.home_layout_grouped_detail),
                        supportingMaxLines = HOME_DETAIL_LINES,
                        toggled = grouped,
                        onClick = { actions.onGrouped(library.id, !grouped) },
                    ).takeIf { library.id in state.groupable },
                ),
        )
        ItemGroup(
            title = stringResource(R.string.home_layout_order),
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.ArrowUpward,
                        label = stringResource(R.string.home_layout_move_up),
                        enabled = place > 0,
                        destination = ListItemDestination.Action,
                        onClick = { actions.onMove(library.id, -1) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.ArrowDownward,
                        label = stringResource(R.string.home_layout_move_down),
                        enabled = place < state.libraries.lastIndex,
                        destination = ListItemDestination.Action,
                        onClick = { actions.onMove(library.id, 1) },
                    ),
                ),
        )
        SaveStatus(state.saveFailed)
    }
}

/** A failed save's message, in a space kept for it so the rows never move. */
@Composable
private fun SaveStatus(failure: ConsoleFailure?) {
    Text(
        text = failure?.let { stringResource(R.string.languages_not_saved, stringResource(it.message)) }.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        minLines = HOME_STATUS_LINES,
        maxLines = HOME_STATUS_LINES,
    )
}

/** Where a library shows on the home, for its row. */
private fun UserView.whereShown(state: HomeLayoutUiState.Ready): Int {
    val myMedia = id !in state.layout.myMediaExcludes
    val latest = id !in state.layout.latestExcludes
    return when {
        myMedia && latest -> R.string.home_layout_shown_both
        myMedia -> R.string.home_layout_shown_my_media
        latest -> R.string.home_layout_shown_latest
        else -> R.string.home_layout_shown_none
    }
}

/** The icon for a library's kind, as its collection type names it. */
internal fun UserView.icon(): ImageVector =
    when (collectionType) {
        "movies" -> Icons.Outlined.Movie
        "tvshows" -> Icons.Outlined.Tv
        "music", "musicvideos" -> Icons.Outlined.MusicNote
        "books" -> Icons.Outlined.AutoStories
        "photos", "homevideos" -> Icons.Outlined.PhotoLibrary
        "boxsets" -> Icons.Outlined.CollectionsBookmark
        "playlists" -> Icons.AutoMirrored.Outlined.PlaylistPlay
        "livetv" -> Icons.Outlined.LiveTv
        "folders" -> Icons.Outlined.Folder
        else -> Icons.Outlined.GridView
    }
