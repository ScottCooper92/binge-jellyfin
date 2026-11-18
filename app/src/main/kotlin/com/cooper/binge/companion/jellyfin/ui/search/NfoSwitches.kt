package com.cooper.binge.companion.jellyfin.ui.search

import com.cooper.binge.companion.jellyfin.server.NFO_EXTRA_THUMBS
import com.cooper.binge.companion.jellyfin.server.NFO_PATH_SUBSTITUTION
import com.cooper.binge.companion.jellyfin.server.NFO_SAVE_IMAGE_PATHS
import com.cooper.binge.companion.jellyfin.server.bool

/**
 * A switch of the NFO page, which saves as made. It reads null where the server did not send its key, as the page hides a row it was
 * not sent. The entries are the page's own (already in the index): they gain the row the switch is found by.
 */
private fun nfoSpec(key: String): Pair<String, SwitchSpec> =
    SettingRows.nfo(key) to SwitchSpec(ConfigKind.Nfo, read = { if (key in it) it.bool(key) ?: false else null }, change = topLevel(key))

internal val NFO_SPECS: Map<String, SwitchSpec> =
    mapOf(
        nfoSpec(NFO_SAVE_IMAGE_PATHS),
        nfoSpec(NFO_PATH_SUBSTITUTION),
        nfoSpec(NFO_EXTRA_THUMBS),
    )
