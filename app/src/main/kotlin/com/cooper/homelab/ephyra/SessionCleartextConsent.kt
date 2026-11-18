package com.cooper.homelab.ephyra

import com.cooper.homelab.core.server.network.CleartextConsent
import com.cooper.homelab.core.server.network.isLocalOrPrivateHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The public host a saved server's address reaches over plain HTTP, or null when it is HTTPS, a local or private host, or not an
 * address. Only such a host needs the user's agreement.
 */
internal fun insecurePublicHost(baseUrl: String?): String? =
    baseUrl
        ?.toHttpUrlOrNull()
        ?.takeIf { !it.isHttps && !isLocalOrPrivateHost(it.host) }
        ?.host

/**
 * Keeps the consent to the saved server alone: whenever the session changes (a sign-in, a sign-out, another server), every host
 * but the saved server's public plain-HTTP one is forgotten, and all of them when there is no session. Without it a host agreed
 * to once stays agreed to, and typing that address again later is let through without asking.
 */
internal fun CleartextConsent.followSession(
    baseUrls: Flow<String?>,
    scope: CoroutineScope,
): Job =
    scope.launch {
        baseUrls.map(::insecurePublicHost).distinctUntilChanged().collect { retainOnly(it) }
    }
