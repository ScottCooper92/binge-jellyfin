package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.sameItem

// Putting a change the server reported over what a list already holds, so a page needs no refetch. No Android types and
// no page knowledge: a page applies these to the list it shows. Applying the same change twice leaves the same list, so
// a write's own answer and the socket's message for it can both arrive without a second effect.

/** [this] with what [change] states; a field it leaves out keeps its value. */
fun ItemUserData.with(change: UserDataChange): ItemUserData =
    copy(
        played = change.played ?: played,
        favourite = change.favourite ?: favourite,
        positionTicks = change.positionTicks ?: positionTicks,
        playedPercentage = change.playedPercentage ?: playedPercentage,
        unplayedCount = change.unplayedCount ?: unplayedCount,
    )

/** [this] with [change] applied when it is for this item; the same instance otherwise. */
fun BrowseItem.with(change: UserDataChange): BrowseItem = if (sameItem(id, change.itemId)) copy(userData = userData.with(change)) else this

/** Every item of [this] that [changes] name, updated. The list is the same instance when none is named. */
fun List<BrowseItem>.withChanges(changes: Collection<UserDataChange>): List<BrowseItem> {
    if (changes.isEmpty()) return this
    val updated = map { item -> changes.fold(item) { current, change -> current.with(change) } }
    return if (updated == this) this else updated
}

/** [this] with [changes] applied to its items. */
fun Page<BrowseItem>.withChanges(changes: Collection<UserDataChange>): Page<BrowseItem> = copy(items = items.withChanges(changes))
