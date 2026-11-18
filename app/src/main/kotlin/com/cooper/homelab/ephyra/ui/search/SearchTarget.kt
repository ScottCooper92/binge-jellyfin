package com.cooper.homelab.ephyra.ui.search

import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.ephyra.server.JellyfinAccount
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.ui.console.ConsolePage

/**
 * Where an index entry opens, in this app's terms: the console [page], the [icon] its result draws, and a gate of its own, for a setting
 * its page hides on some servers or accounts. The page's own gate is applied besides ([available]).
 */
internal class SearchTarget(
    val page: ConsolePage,
    val icon: ImageVector,
    val shown: (JellyfinServerProfile, JellyfinAccount) -> Boolean = { _, _ -> true },
)

/** Whether the search may offer this entry: its page is open to the user, and its own gate, if it has one, passes. */
internal fun SettingEntry<SearchTarget>.available(
    profile: JellyfinServerProfile,
    account: JellyfinAccount,
): Boolean = target.page.available(profile, account) && target.shown(profile, account)
