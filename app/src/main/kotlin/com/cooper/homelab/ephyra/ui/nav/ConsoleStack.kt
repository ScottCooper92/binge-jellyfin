package com.cooper.homelab.ephyra.ui.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.ui.console.ConsoleEntry
import com.cooper.homelab.ephyra.ui.console.ConsolePage
import kotlin.random.Random

/*
 * The console's pages and items on the app's back stack: the keys above the [HubRoute] at its root. Each function acts on
 * the stack it is given, so a tab's own stack can take them later. A page that opens another stays under it, so Back
 * returns to the page that opened the current one, and to the home from the first.
 */

/** A fresh opening id not on [this] stack. Random, so an id is never reused while a closed page's view models are still being cleared. */
private fun NavBackStack<NavKey>.freshOpening(nextOpening: () -> Int): Int {
    val used = openings()
    return generateSequence(nextOpening).first { it !in used }
}

/** The id source: positive, so it never collides with the 0 a screenshot or test uses for a fixed entry. */
val RandomOpening: () -> Int = { Random.nextInt(1, Int.MAX_VALUE) }

/** The console keys on the stack, in order: every key above the root that is a page or an item. */
val NavBackStack<NavKey>.consoleKeys: List<NavKey> get() = drop(1).filter { it is PageRoute || it is ItemRoute }

private fun NavKey.entry(): ConsoleEntry? =
    when (this) {
        is PageRoute -> ConsoleEntry(page, opening, row = row)
        is ItemRoute -> ConsoleEntry(page, opening, item)
        else -> null
    }

/** The console entries on the stack, in order. */
fun NavBackStack<NavKey>.entries(): List<ConsoleEntry> = consoleKeys.mapNotNull { it.entry() }

/** The page or item showing, or null for the home. */
val NavBackStack<NavKey>.top: ConsoleEntry? get() = lastOrNull()?.entry()

/** The item showing in the entry on top, if one is. */
val NavBackStack<NavKey>.topItem: ConsoleEntry? get() = top?.takeIf { it.item != null }

/** The openings still on the stack: the ones whose view models are kept. */
fun NavBackStack<NavKey>.openings(): Set<Int> = entries().map { it.opening }.toSet()

/** Makes the console part of the stack [entries], keeping the keys already in place so only what changed recomposes. */
private fun NavBackStack<NavKey>.setEntries(entries: List<NavKey>) {
    val keep = (1 until size).takeWhile { it - 1 < entries.size && this[it] == entries[it - 1] }.size
    while (size > 1 + keep) removeAt(lastIndex)
    entries.drop(keep).forEach { add(it) }
}

/**
 * [page] opened from the entry on top, or from the home, on [row] if one is named. Opening the page already on top does
 * nothing, except that a [row] it is not on is shown.
 */
fun NavBackStack<NavKey>.openPage(
    page: ConsolePage,
    row: String? = null,
    nextOpening: () -> Int = RandomOpening,
) {
    val on = top
    when {
        on == null || on.page != page -> add(PageRoute(page, freshOpening(nextOpening), row))
        row != null && on.item == null && on.row != row -> this[lastIndex] = PageRoute(page, on.opening, row)
    }
}

/**
 * [item] opened in the page on top, above its own entry. One item is open at a time: opening another replaces the one
 * open, and anything opened above it, and opening the one showing does nothing. Back from it returns to the page's list.
 */
fun NavBackStack<NavKey>.openItem(item: String) {
    val at = indexOfLast { it is PageRoute }
    if (at < 0) return
    if (lastIndex > at && (last() as? ItemRoute)?.item == item) return
    val page = this[at] as PageRoute
    while (lastIndex > at) removeAt(lastIndex)
    add(ItemRoute(page.page, page.opening, item))
}

/**
 * [item] opened above the item showing, as a user's section is above the user: Back returns to that item. With none
 * showing there is nothing to open it over.
 */
fun NavBackStack<NavKey>.openSubItem(item: String) {
    val on = last() as? ItemRoute ?: return
    if (on.item == item) return
    add(ItemRoute(on.page, on.opening, item))
}

/**
 * [item] of [page] opened from the entry on top, as a user is from the activity log: the page, then its item over it, so
 * Back returns to the item's list and then to the page that opened it.
 */
fun NavBackStack<NavKey>.openPageItem(
    page: ConsolePage,
    item: String,
    nextOpening: () -> Int = RandomOpening,
) {
    openPage(page, nextOpening = nextOpening)
    openItem(item)
}

/**
 * [page] opened from the home. Beside the home a page replaces the one open, and what that opened, so Back from it returns
 * to the home rather than walking every page visited on the way. The page already at the bottom stays, with its view
 * models, and only what it opened closes.
 */
fun NavBackStack<NavKey>.openFromHome(
    page: ConsolePage,
    row: String? = null,
    nextOpening: () -> Int = RandomOpening,
) {
    val first = getOrNull(1) as? PageRoute
    if (first?.page == page) {
        setEntries(listOf(PageRoute(page, first.opening, row)))
    } else {
        setEntries(listOf(PageRoute(page, freshOpening(nextOpening), row)))
    }
}

/** [libraryId]'s screen, over the shell. A second tap on the same tile while it opens adds nothing. */
fun NavBackStack<NavKey>.openLibrary(libraryId: String) {
    val key = LibraryRoute(libraryId)
    if (lastOrNull() != key) add(key)
}

/** Back: the entry on top closes, and the one under it, or the home, shows. The root never closes. */
fun NavBackStack<NavKey>.closeTop() {
    if (size > 1) removeAt(lastIndex)
}

/** Every page and item closes, as when the user signs out. */
fun NavBackStack<NavKey>.closeAll() {
    while (size > 1) removeAt(lastIndex)
}

/** The stack without the pages [allowed] says the server or the user lost, and their items: Back skips them. */
fun NavBackStack<NavKey>.keeping(allowed: (ConsolePage) -> Boolean) {
    val keys = consoleKeys
    val kept =
        keys.filter {
            when (it) {
                is PageRoute -> allowed(it.page)
                is ItemRoute -> allowed(it.page)
                else -> true
            }
        }
    if (kept.size != keys.size) setEntries(kept)
}
