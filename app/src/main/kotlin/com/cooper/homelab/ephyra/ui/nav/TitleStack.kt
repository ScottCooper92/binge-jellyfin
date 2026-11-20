package com.cooper.homelab.ephyra.ui.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.search.SearchKind

/** The route for an item of [kind], or null for a kind that has no page (the server's other types). */
fun titleRoute(
    kind: ItemKind,
    itemId: String,
): TitleRoute? =
    when (kind) {
        ItemKind.Movie -> MovieRoute(itemId)
        ItemKind.Series -> SeriesRoute(itemId)
        ItemKind.Season -> SeasonRoute(itemId)
        ItemKind.Episode -> EpisodeRoute(itemId)
        ItemKind.BoxSet -> CollectionRoute(itemId)
        ItemKind.Other -> null
    }

/**
 * The page of item [itemId], which is of [kind], over the shell. A kind with no page opens nothing, and a second tap on the title
 * already on top adds nothing. A person is not an [ItemKind]: see [openPerson].
 */
fun NavBackStack<NavKey>.openTitle(
    kind: ItemKind,
    itemId: String,
) {
    val key = titleRoute(kind, itemId) ?: return
    if (lastOrNull() != key) add(key)
}

/** A person's page, over the shell. */
fun NavBackStack<NavKey>.openPerson(itemId: String) {
    val key = PersonRoute(itemId)
    if (lastOrNull() != key) add(key)
}

/** A search result's page, over the shell: a title opens its own page and a person theirs. */
fun NavBackStack<NavKey>.openSearchHit(
    kind: SearchKind,
    id: String,
) {
    when (kind) {
        SearchKind.Movie -> openTitle(ItemKind.Movie, id)
        SearchKind.Series -> openTitle(ItemKind.Series, id)
        SearchKind.Episode -> openTitle(ItemKind.Episode, id)
        SearchKind.Person -> openPerson(id)
    }
}
