package com.cooper.homelab.ephyra.openitem

/**
 * The names other apps use to open a title here: see `OpenItemActivity` for what they mean. They are a public contract, so one never
 * changes once released.
 */
object OpenItem {
    const val ACTION = "com.cooper.homelab.ephyra.action.OPEN_ITEM"
    const val EXTRA_SERVER_URL = "com.cooper.homelab.ephyra.extra.SERVER_URL"
    const val EXTRA_ITEM_ID = "com.cooper.homelab.ephyra.extra.ITEM_ID"
    const val EXTRA_TMDB_TYPE = "com.cooper.homelab.ephyra.extra.TMDB_TYPE"
    const val EXTRA_TMDB_ID = "com.cooper.homelab.ephyra.extra.TMDB_ID"
}
