package com.cooper.homelab.ephyra

import com.cooper.homelab.core.diagnostics.RedactionLiterals
import com.cooper.homelab.ephyra.auth.JellyfinSession

/**
 * What the signed-in session holds that must not reach the log, for the shared redaction follower: the server's address, the access
 * token and the user's name wherever they appear, and the host (with its port) only as a whole word, since a single-label name such as
 * `jellyfin` is also in this app's own tags.
 */
internal fun JellyfinSession.toRedactionLiterals(): RedactionLiterals {
    val address = baseUrl.trim().trimEnd('/')
    val host = address.substringAfter("://").substringBefore('/').takeIf { it.isNotBlank() && it != address }
    return RedactionLiterals(anywhere = listOf(address, token, userName), wholeToken = listOfNotNull(host))
}
