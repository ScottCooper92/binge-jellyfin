package com.cooper.binge.companion.jellyfin.demo

import okhttp3.HttpUrl

/**
 * The demo server's address. `.invalid` is reserved (RFC 2606) and never resolves, so a request that somehow
 * escaped the demo transport would fail rather than reach a stranger's host.
 */
internal const val DEMO_HOST = "demo.ephyra.invalid"

/** The base URL the demo session is saved under, in the same shape as any other server's. */
const val DEMO_ORIGIN = "https://$DEMO_HOST"

/** What the demo's sign-in answers with. Neither is a secret: the demo is the same sample data everywhere. */
internal const val DEMO_TOKEN = "demo-token"
internal const val DEMO_SERVER_ID = "demo0000000000000000000000000001"
internal const val DEMO_SERVER_NAME = "Ephyra demo server"
internal const val DEMO_VERSION = "10.10.7"

/** Whether [this] is the demo server's address. */
fun String.isDemoOrigin(): Boolean = trimEnd('/').equals(DEMO_ORIGIN, ignoreCase = true)

internal fun HttpUrl.isDemo(): Boolean = host == DEMO_HOST
