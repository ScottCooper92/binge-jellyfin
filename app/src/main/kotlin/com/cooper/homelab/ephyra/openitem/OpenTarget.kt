package com.cooper.homelab.ephyra.openitem

import com.cooper.homelab.ephyra.browse.ItemKind

/** Where an [OpenItemRequest] leads: a title's page, or the home a normal launch shows. */
sealed interface OpenTarget {
    /** The page of the [kind] (a movie or a series) with the server's id [id]. */
    data class Title(
        val kind: ItemKind,
        val id: String,
    ) : OpenTarget

    /** Nothing matched, so the app opens as it does on a normal launch. */
    data object Home : OpenTarget
}
