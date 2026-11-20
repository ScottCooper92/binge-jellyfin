package com.cooper.homelab.ephyra.client.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.MediaResult
import com.binge.designsystem.component.MediaResultKind
import com.binge.designsystem.template.SearchCallbacks
import com.binge.designsystem.template.SearchScreen
import com.binge.designsystem.template.SearchState
import com.binge.designsystem.template.SearchTypeChip
import com.binge.designsystem.template.SearchTypes
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.R
import com.cooper.homelab.ephyra.client.home.canRetry
import com.cooper.homelab.ephyra.client.home.errorKind

/** What the Search tab reports to whatever holds its state. */
class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onClear: () -> Unit,
    val onSubmit: () -> Unit,
    val onOpen: (kind: SearchKind, id: String) -> Unit,
    val onLoadMore: () -> Unit,
    val onRetry: () -> Unit,
    val onType: (SearchType) -> Unit,
    val onPickRecent: (String) -> Unit,
    val onClearRecents: () -> Unit,
)

/** How wide each kind of picture is asked for, in pixels: a phone's row at a high density. */
private const val POSTER_WIDTH_PX = 300
private const val STILL_WIDTH_PX = 400
private const val AVATAR_WIDTH_PX = 200

/**
 * The Search tab on the design system's search screen: the field and the type chips under a large title, the recent queries while idle,
 * rows of results, and the failures and empty answers. [imageUrl] turns a result and a width in pixels into a URL, or null when it has no
 * picture, so this screen holds no server and no token. [reselect] is the tab this is the root of: a tap on it again focuses the field.
 */
@Composable
fun SearchTabScreen(
    state: SearchUiState,
    imageUrl: (SearchHit, Int) -> String?,
    actions: SearchActions,
    modifier: Modifier = Modifier,
    reselect: Any? = null,
) {
    val title = stringResource(R.string.client_search_title)
    val types = state.type.toTypes()
    SearchScreen(
        title = title,
        text = state.text,
        state = state.body.toTemplate(types, imageUrl),
        callbacks =
            SearchCallbacks(
                onQueryChange = actions.onQueryChange,
                onClear = actions.onClear,
                onOpenResult = { key -> parseSearchHitKey(key)?.let { (kind, id) -> actions.onOpen(kind, id) } },
                onLoadMore = actions.onLoadMore,
                onRetry = actions.onRetry,
                onSubmit = actions.onSubmit,
                onType = { key -> SearchType.entries.firstOrNull { it.name == key }?.let(actions.onType) },
                onPickRecent = actions.onPickRecent,
                onClearRecents = actions.onClearRecents,
            ),
        placeholder = stringResource(R.string.client_search_placeholder),
        modifier = modifier,
        idleTitle = title,
        idleMessage = stringResource(R.string.client_search_idle_message),
        reselectKey = reselect,
    )
}

@Composable
private fun SearchType.toTypes(): SearchTypes =
    SearchTypes(
        chips = SearchType.entries.map { SearchTypeChip(it.name, stringResource(it.label())) },
        selectedKey = name,
    )

private fun SearchType.label(): Int =
    when (this) {
        SearchType.All -> R.string.client_search_type_all
        SearchType.Movies -> R.string.client_search_type_movies
        SearchType.Series -> R.string.client_search_type_series
        SearchType.Episodes -> R.string.client_search_type_episodes
        SearchType.People -> R.string.client_search_type_people
    }

@Composable
private fun SearchBody.toTemplate(
    types: SearchTypes,
    imageUrl: (SearchHit, Int) -> String?,
): SearchState =
    when (this) {
        is SearchBody.Idle -> SearchState.Idle(recents)
        SearchBody.Loading -> SearchState.Loading
        is SearchBody.Ready ->
            SearchState.Ready(
                results = hits.map { it.toResult(imageUrl) },
                loaded = loaded,
                hasMore = hasMore,
                appending = appending,
                appendFailed = appendFailed,
                types = types,
                emptyMessage = stringResource(R.string.client_search_type_empty),
            )
        is SearchBody.NoResults -> SearchState.NoResults(stringResource(R.string.client_search_no_results, query), types = types)
        is SearchBody.Failed -> SearchState.Failed(failure.errorKind(), retryable = failure.canRetry())
    }

@Composable
private fun SearchHit.toResult(imageUrl: (SearchHit, Int) -> String?): MediaResult =
    when (this) {
        is SearchHit.Person -> MediaResult(searchHitKey(this), MediaResultKind.Avatar, imageUrl(this, AVATAR_WIDTH_PX), person.name)
        is SearchHit.Title -> {
            val episode = item.kind == ItemKind.Episode
            MediaResult(
                key = searchHitKey(this),
                kind = if (episode) MediaResultKind.Still else MediaResultKind.Poster,
                imageUrl = imageUrl(this, if (episode) STILL_WIDTH_PX else POSTER_WIDTH_PX),
                name = item.name,
                subtitle = item.subtitle(),
                rating = item.communityRating?.toFloat(),
                meta = item.officialRating,
                favourite = item.userData.favourite,
                watched = item.userData.played,
            )
        }
    }

@Composable
private fun BrowseItem.subtitle(): String =
    when (kind) {
        ItemKind.Episode ->
            if (seriesName != null && seasonNumber != null && episodeNumber != null) {
                stringResource(R.string.client_search_episode_of, seriesName.orEmpty(), seasonNumber ?: 0, episodeNumber ?: 0)
            } else {
                seriesName ?: stringResource(R.string.client_search_kind_episode)
            }
        ItemKind.Series -> listOfNotNull(stringResource(R.string.client_search_kind_series), year?.toString()).joinToString(" · ")
        else -> listOfNotNull(stringResource(R.string.client_search_kind_movie), year?.toString()).joinToString(" · ")
    }
