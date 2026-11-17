package com.cooper.binge.companion.jellyfin.server

import com.cooper.homelab.core.server.address.ServerAddress
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * How a typed server address becomes the addresses to try, in order. [Legacy] is what the released versions do: one address, with
 * `http://` added when no scheme was typed. [Shared] is the shared library's: a scheme left off is `http` for a local host and
 * `https` for any other, the base path is kept, and an address typed with no port is tried again on the server's own port.
 */
fun interface AddressPolicy {
    fun candidates(raw: String): List<HttpUrl>

    object Legacy : AddressPolicy {
        override fun candidates(raw: String): List<HttpUrl> = listOfNotNull(JellyfinClient.parseBaseUrl(raw))
    }

    object Shared : AddressPolicy {
        override fun candidates(raw: String): List<HttpUrl> =
            ServerAddress
                .parse(raw)
                ?.candidates(httpPort = DEFAULT_HTTP_PORT, httpsPort = DEFAULT_HTTPS_PORT)
                .orEmpty()
                .mapNotNull { it.url.toHttpUrlOrNull() }
    }
}
