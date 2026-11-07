package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope

/**
 * One console page on the stack, and the opening that made it: its view models are kept under [opening]. An entry with an
 * [item] is the item open in that page (a task, a log, a user), stacked above the page's own entry under the same
 * opening, so the page and its items share view models.
 */
data class ConsoleEntry(
    val page: ConsolePage,
    val opening: Int,
    val item: String? = null,
)

/** What Navigation 3 shows: the home, or one open page under its opening number. A key's identity is its content. */
sealed interface ConsoleKey {
    /** The home before sign-in, and while the server is being worked out: always the whole window. */
    data object Home : ConsoleKey

    /** The signed-in home: the list pane the pages open beside on a wide window, the whole window on a narrow one. */
    data object Hub : ConsoleKey

    data class Page(
        val page: ConsolePage,
        val opening: Int,
    ) : ConsoleKey

    /** An item open in [page], above its [Page]: what the page's list opened, as a screen of its own. */
    data class Item(
        val page: ConsolePage,
        val opening: Int,
        val item: String,
    ) : ConsoleKey
}

/**
 * The console pages that are open, the last on top. A page that opens another stays under it, so Back returns to
 * the page that opened the current one, and to the home from the first. Each entry has its own opening number,
 * which Navigation 3 keeps that page's view models under while it is on the stack.
 *
 * A value: every change makes a new stack. It saves through [Saver] across a rotation and a process death.
 */
data class ConsoleBackStack(
    val entries: List<ConsoleEntry> = emptyList(),
    private val next: Int = 0,
) {
    /** The page showing, or null for the home. */
    val top: ConsoleEntry? get() = entries.lastOrNull()

    /** The openings still on the stack: the ones whose view models are kept. */
    val openings: Set<Int> get() = entries.map { it.opening }.toSet()

    /**
     * The stack as Navigation 3 shows it: the home at the bottom, then each open page. The home is the [ConsoleKey.Hub]
     * once [signedIn], since an entry's pane role is fixed when it is built, so a change of role is a change of key.
     */
    fun keys(signedIn: Boolean = false): List<ConsoleKey> =
        listOf(if (signedIn) ConsoleKey.Hub else ConsoleKey.Home) +
            entries.map { entry ->
                entry.item?.let { ConsoleKey.Item(entry.page, entry.opening, it) } ?: ConsoleKey.Page(entry.page, entry.opening)
            }

    /** The item showing in the page on top, if one is. */
    val topItem: ConsoleEntry? get() = top?.takeIf { it.item != null }

    /**
     * [item] opened in the page on top, above its own entry. One item is open at a time: opening another replaces the one
     * open, and anything opened above it, and opening the one showing does nothing. Back from it returns to the page's list.
     */
    fun openItem(item: String): ConsoleBackStack {
        val at = entries.indexOfLast { it.item == null }
        if (at < 0) return this
        if (entries.lastIndex > at && top?.item == item) return this
        val page = entries[at]
        return copy(entries = entries.take(at + 1) + ConsoleEntry(page.page, page.opening, item))
    }

    /**
     * [item] opened above the item showing, as a user's section is above the user: Back returns to that item. With none
     * showing there is nothing to open it over.
     */
    fun openSubItem(item: String): ConsoleBackStack {
        val on = top?.takeIf { it.item != null } ?: return this
        if (on.item == item) return this
        return copy(entries = entries + ConsoleEntry(on.page, on.opening, item))
    }

    /** [page] opened from the page on top, or from the home. Opening the page already on top does nothing. */
    fun open(page: ConsolePage): ConsoleBackStack =
        if (top?.page == page) this else copy(entries = entries + ConsoleEntry(page, next), next = next + 1)

    /**
     * [item] of [page] opened from the page on top, as a user is from the activity log: the page, then its item over it, so
     * Back returns to the item's list and then to the page that opened it.
     */
    fun openPageItem(
        page: ConsolePage,
        item: String,
    ): ConsoleBackStack = open(page).openItem(item)

    /**
     * [page] opened from the home. Beside the home a page replaces the one open, and what that opened, so Back from
     * it returns to the home rather than walking every page visited on the way. The page already at the bottom
     * stays, with its view models, and only what it opened closes.
     */
    fun openFromHome(page: ConsolePage): ConsoleBackStack =
        if (entries.firstOrNull()?.page == page) {
            copy(entries = entries.take(1))
        } else {
            copy(entries = listOf(ConsoleEntry(page, next)), next = next + 1)
        }

    /** Back: the page on top closes, and the one under it, or the home, shows. */
    fun back(): ConsoleBackStack = copy(entries = entries.dropLast(1))

    /** Every page closes, as when the user signs out. */
    fun clear(): ConsoleBackStack = copy(entries = emptyList())

    /** The stack without the pages [allowed] says the server or the user lost: Back skips them. */
    fun keeping(allowed: (ConsolePage) -> Boolean): ConsoleBackStack =
        entries.filter { allowed(it.page) }.let { if (it.size == entries.size) this else copy(entries = it) }

    companion object {
        /** Saves as the next opening number, then each entry's page (by its name), opening and item (empty for none). */
        val Saver: Saver<ConsoleBackStack, List<Any>> =
            object : Saver<ConsoleBackStack, List<Any>> {
                override fun SaverScope.save(value: ConsoleBackStack): List<Any> =
                    listOf<Any>(value.next) + value.entries.flatMap { listOf(it.page.name, it.opening, it.item.orEmpty()) }

                override fun restore(value: List<Any>): ConsoleBackStack? {
                    val next = value.firstOrNull() as? Int ?: return null
                    val entries =
                        value.drop(1).chunked(3).map { chunk ->
                            if (chunk.size != 3) return null
                            val page = ConsolePage.entries.firstOrNull { it.name == chunk[0] } ?: return null
                            ConsoleEntry(page, chunk[1] as? Int ?: return null, (chunk[2] as? String ?: return null).ifEmpty { null })
                        }
                    return ConsoleBackStack(entries, next)
                }
            }
    }
}
