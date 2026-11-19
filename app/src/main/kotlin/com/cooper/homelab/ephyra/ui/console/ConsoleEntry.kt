package com.cooper.homelab.ephyra.ui.console

/**
 * One console page or item as a screen is given it: the page, the opening its view models are kept under, the [item]
 * open in the page (a task, a log, a user) or null for the page's own entry, and the [row] the page was opened on.
 */
data class ConsoleEntry(
    val page: ConsolePage,
    val opening: Int,
    val item: String? = null,
    /** The setting the page was opened on, by its row key, for a search result: the page scrolls to it and washes it once. */
    val row: String? = null,
)
