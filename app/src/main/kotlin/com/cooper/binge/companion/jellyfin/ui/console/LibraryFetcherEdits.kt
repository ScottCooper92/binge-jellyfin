package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.server.FetcherKind
import com.cooper.binge.companion.jellyfin.server.FetcherType
import com.cooper.binge.companion.jellyfin.server.LIBRARY_TYPE_OPTIONS
import com.cooper.binge.companion.jellyfin.server.Provider
import com.cooper.binge.companion.jellyfin.server.moved
import com.cooper.binge.companion.jellyfin.server.withFetchers

/**
 * A library's per-type fetcher changes: switching a metadata downloader or an image fetcher on or off, and moving it in its type's
 * order. Each change goes through [saves] as the whole `TypeOptions` array with that one type's entry rewritten, so the first change
 * to a type writes the entry out in full and the next builds on it. [types] is what the server offers the library now.
 */
internal class LibraryFetcherEdits(
    private val saves: ConfigurationSaves,
    private val types: () -> List<FetcherType>,
) {
    /** Switches [name], one of [type]'s [kind] fetchers, on or off. */
    fun set(
        kind: FetcherKind,
        type: String,
        name: String,
        enabled: Boolean,
    ) = change(kind, type) { fetchers -> fetchers.map { if (it.name.equals(name, ignoreCase = true)) it.copy(enabled = enabled) else it } }

    /** Moves [name] [by] places in [type]'s [kind] order: -1 earlier, 1 later. */
    fun move(
        kind: FetcherKind,
        type: String,
        name: String,
        by: Int,
    ) = change(kind, type) { fetchers ->
        fetchers.moved(name, by) { it.name }
    }

    private fun change(
        kind: FetcherKind,
        type: String,
        change: (List<Provider>) -> List<Provider>,
    ) {
        val known = types().firstOrNull { it.type.equals(type, ignoreCase = true) } ?: return
        saves.set(LIBRARY_TYPE_OPTIONS, saves.shown.withFetchers(known, kind, change))
    }
}
