package com.cooper.homelab.ephyra.client.libraries

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.SortField

/** What the bubble shows for a title whose name does not start with a letter. */
internal const val NON_LETTER_BUBBLE = "#"

/**
 * What the fast scrollbar's bubble says for the title at an index while the thumb is held, for a grid ordered by [sort] and holding [items]:
 * the first letter of the title's sort name when ordered by name, so it names the section the grid is in, and the year when ordered by
 * release date. The sort name is the one the server orders by, which drops a leading "The" or "A", and the title's own name stands in
 * when the server sent none. A name that starts with anything but a letter reads [NON_LETTER_BUBBLE]. Any other order, an index past the
 * titles (the grid's footer) and a title with no year have no bubble. Null when [sort] has none at all.
 */
fun libraryBubbleText(
    sort: SortField,
    items: List<BrowseItem>,
): ((Int) -> String?)? =
    when (sort) {
        SortField.Name -> { index -> items.getOrNull(index)?.let(::nameBubble) }
        SortField.ReleaseDate -> { index -> items.getOrNull(index)?.year?.toString() }
        else -> null
    }

private fun nameBubble(item: BrowseItem): String? {
    val first = (item.sortName ?: item.name).trim().firstOrNull() ?: return null
    return if (first.isLetter()) first.uppercaseChar().toString() else NON_LETTER_BUBBLE
}
