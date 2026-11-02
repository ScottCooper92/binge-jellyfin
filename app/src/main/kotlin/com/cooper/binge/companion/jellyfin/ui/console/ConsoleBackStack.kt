package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope

/** One console page on the stack, and the opening that made it: its view models are kept under [opening]. */
data class ConsoleEntry(
    val page: ConsolePage,
    val opening: Int,
)

/**
 * The console pages that are open, the last on top. A page that opens another stays under it, so Back returns to
 * the page that opened the current one, and to the home from the first. Each entry has its own opening number,
 * which `ConsolePageStores` keeps that page's view models under while it is on the stack.
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

    /** [page] opened from the page on top, or from the home. Opening the page already on top does nothing. */
    fun open(page: ConsolePage): ConsoleBackStack =
        if (top?.page == page) this else copy(entries = entries + ConsoleEntry(page, next), next = next + 1)

    /** Back: the page on top closes, and the one under it, or the home, shows. */
    fun back(): ConsoleBackStack = copy(entries = entries.dropLast(1))

    /** Every page closes, as when the user signs out. */
    fun clear(): ConsoleBackStack = copy(entries = emptyList())

    /** The stack without the pages [allowed] says the server or the user lost: Back skips them. */
    fun keeping(allowed: (ConsolePage) -> Boolean): ConsoleBackStack =
        entries.filter { allowed(it.page) }.let { if (it.size == entries.size) this else copy(entries = it) }

    companion object {
        /** Saves as the next opening number, then each entry's page (by its name) and opening. */
        val Saver: Saver<ConsoleBackStack, List<Any>> =
            object : Saver<ConsoleBackStack, List<Any>> {
                override fun SaverScope.save(value: ConsoleBackStack): List<Any> =
                    listOf<Any>(value.next) + value.entries.flatMap { listOf(it.page.name, it.opening) }

                override fun restore(value: List<Any>): ConsoleBackStack? {
                    val next = value.firstOrNull() as? Int ?: return null
                    val entries =
                        value.drop(1).chunked(2).map { (name, opening) ->
                            val page = ConsolePage.entries.firstOrNull { it.name == name } ?: return null
                            ConsoleEntry(page, opening as? Int ?: return null)
                        }
                    return ConsoleBackStack(entries, next)
                }
            }
    }
}
