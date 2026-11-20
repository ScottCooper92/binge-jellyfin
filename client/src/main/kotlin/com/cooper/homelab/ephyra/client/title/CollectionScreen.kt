package com.cooper.homelab.ephyra.client.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.MediaResult
import com.binge.designsystem.component.MediaResultKind
import com.binge.designsystem.template.CollectionDetail
import com.binge.designsystem.template.CollectionDetailCallbacks
import com.binge.designsystem.template.CollectionDetailScreen
import com.binge.designsystem.template.CollectionDetailState
import com.cooper.homelab.ephyra.client.R
import com.cooper.homelab.ephyra.client.home.canRetry
import com.cooper.homelab.ephyra.client.home.errorKind
import com.binge.designsystem.R as DesR

/** What the collection page does when the user acts. A part is handed back as its card. */
class CollectionActions(
    val onBack: (() -> Unit)?,
    val onRetry: () -> Unit,
    val onOpenTitle: (TitleCard) -> Unit,
)

private const val BACKDROP_WIDTH = 1280
private const val POSTER_WIDTH = 300
private const val HERO_POSTER_WIDTH = 600

/**
 * The collection page for [state], on the design system's collection template: mapping and callbacks only. The hero's picture, the count line
 * ("3 of 5 watched"), the overview and the parts as rows are the template's; [imageUrl] turns a picture into a URL at a width in pixels, or
 * null when there is none, so this screen holds no server and no token.
 */
@Composable
fun CollectionScreen(
    state: CollectionUiState,
    imageUrl: (TitleImage, Int) -> String?,
    actions: CollectionActions,
) {
    val meta =
        (state as? CollectionUiState.Ready)
            ?.collection
            ?.let {
                stringResource(R.string.client_collection_watched, it.watchedCount, it.parts.size)
            }.orEmpty()
    val emptyMessage = stringResource(R.string.client_collection_empty)
    val mapped =
        when (state) {
            CollectionUiState.Loading -> CollectionDetailState.Loading()
            is CollectionUiState.Failed ->
                CollectionDetailState.Failed(
                    kind = state.failure.errorKind(),
                    retryable = state.failure.canRetry(),
                )
            is CollectionUiState.Ready -> CollectionDetailState.Content(collectionDetail(state.collection, meta, emptyMessage, imageUrl))
        }
    val byKey =
        remember(state) {
            (state as? CollectionUiState.Ready)
                ?.collection
                ?.parts
                .orEmpty()
                .associate { it.card.id to it.card }
        }
    CollectionDetailScreen(
        state = mapped,
        callbacks =
            CollectionDetailCallbacks(
                onBack = actions.onBack,
                onRetry = actions.onRetry,
                onOpenPart = { key -> byKey[key]?.let(actions.onOpenTitle) },
            ),
    )
}

@Composable
internal fun collectionDetail(
    page: CollectionPageUi,
    meta: String,
    emptyMessage: String,
    imageUrl: (TitleImage, Int) -> String?,
): CollectionDetail =
    CollectionDetail(
        title = page.name,
        // A hand-made box set often has only a poster: the template draws it behind the title when there is no backdrop, and beside it when wide.
        backdropUrl = page.backdrop?.let { imageUrl(it, BACKDROP_WIDTH) },
        posterUrl = page.poster?.let { imageUrl(it, HERO_POSTER_WIDTH) },
        metaText = meta,
        overview = page.overview,
        parts =
            page.parts.map { part ->
                MediaResult(
                    key = part.card.id,
                    kind = MediaResultKind.Poster,
                    imageUrl = part.card.poster?.let { imageUrl(it, POSTER_WIDTH) },
                    name = part.card.title,
                    subtitle = part.card.year?.toString(),
                    rating = part.card.rating?.toFloat(),
                    meta = part.runtimeMinutes?.let { stringResource(DesR.string.detail_runtime_minutes, it) },
                    watched = part.card.played,
                )
            },
        emptyMessage = emptyMessage,
    )
