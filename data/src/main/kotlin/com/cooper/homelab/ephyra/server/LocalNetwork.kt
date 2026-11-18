package com.cooper.homelab.ephyra.server

import com.cooper.homelab.core.server.network.isLocalNetworkHost
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The host of this server address when reaching it needs the local-network permission; null for any other, or one that does not parse. */
fun String.localNetworkHostOrNull(): String? = toHttpUrlOrNull()?.host?.takeIf { isLocalNetworkHost(it) }
