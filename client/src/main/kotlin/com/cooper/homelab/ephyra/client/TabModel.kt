package com.cooper.homelab.ephyra.client

import kotlinx.coroutines.flow.StateFlow

/** What a client tab's view model offers its host: the state it shows, whether a refresh is running, and the two ways to read. */
interface TabModel<S> {
    /** What the tab shows now. */
    val state: StateFlow<S>

    /** Whether a [refresh] is still running, for a pull-to-refresh indicator. */
    val refreshing: StateFlow<Boolean>

    /** Reads what the tab shows. Call it once when the tab opens. */
    fun start()

    /** Reads it again, keeping what is on screen until the new answer lands. After a failure it is a retry. */
    fun refresh()
}
