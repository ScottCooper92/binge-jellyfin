package com.cooper.binge.companion.jellyfin.ui.search

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.NETWORK_AUTO_DISCOVERY
import com.cooper.binge.companion.jellyfin.server.NETWORK_IGNORE_VIRTUAL
import com.cooper.binge.companion.jellyfin.server.NETWORK_PUBLISHED_BY_REQUEST
import com.cooper.binge.companion.jellyfin.server.NETWORK_UPNP
import com.cooper.binge.companion.jellyfin.server.NetworkSettings
import com.cooper.binge.companion.jellyfin.server.networkKeys
import com.cooper.binge.companion.jellyfin.server.networkSettings
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.homelab.core.presentation.search.SettingEntry

/**
 * The names the four switches below read by do not change between releases (only the ports and IPv4 and IPv6 have two sets), so the
 * page's own decoder is given the current names to read them with.
 */
private val SWITCH_KEYS = networkKeys(JellyfinServerProfile.from("10.11.6"))

/**
 * A switch of the Network page that saves as made, as the page's own rows do. The page's other switches (HTTPS, remote access, the
 * address filter, IPv4 and IPv6) wait for its Save changes, since a wrong value can lock a client out, so they have none here. A switch
 * reads null where the server did not send its key, as the page hides a row it was not sent.
 */
private fun networkSpec(
    key: String,
    gate: (JellyfinServerProfile) -> Boolean = { true },
    value: (NetworkSettings) -> Boolean,
): Pair<String, SwitchSpec> =
    SettingRows.network(key) to
        SwitchSpec(
            ConfigKind.Network,
            gate = gate,
            read = { if (key in it) value(it.networkSettings(SWITCH_KEYS)) else null },
            change = topLevel(key),
        )

internal val NETWORK_SPECS: Map<String, SwitchSpec> =
    mapOf(
        networkSpec(NETWORK_AUTO_DISCOVERY) { it.autoDiscovery },
        networkSpec(NETWORK_UPNP, gate = { networkKeys(it).upnp }) { it.upnp },
        networkSpec(NETWORK_PUBLISHED_BY_REQUEST) { it.publishedByRequest },
        networkSpec(NETWORK_IGNORE_VIRTUAL) { it.ignoreVirtual },
    )

internal fun Resources.indexNetworkSwitches(): List<SettingEntry<SearchTarget>> =
    listOf(
        networkEntry(R.string.network_discovery, Icons.Outlined.Sync, NETWORK_AUTO_DISCOVERY),
        networkEntry(R.string.network_upnp, Icons.Outlined.Router, NETWORK_UPNP) { profile, _ -> networkKeys(profile).upnp },
        networkEntry(R.string.network_published_request, Icons.Outlined.Language, NETWORK_PUBLISHED_BY_REQUEST),
        networkEntry(R.string.network_ignore_virtual, Icons.Outlined.Wifi, NETWORK_IGNORE_VIRTUAL),
    )

private fun Resources.networkEntry(
    @StringRes label: Int,
    icon: ImageVector,
    key: String,
    shown: (JellyfinServerProfile, JellyfinAccount) -> Boolean = { _, _ -> true },
) = setting(
    ConsolePage.Network,
    label,
    icon,
    R.string.advanced_title,
    R.string.network_title,
    shown = shown,
    row = SettingRows.network(key),
)
