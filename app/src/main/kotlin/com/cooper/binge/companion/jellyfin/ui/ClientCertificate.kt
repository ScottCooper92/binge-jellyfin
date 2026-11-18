package com.cooper.binge.companion.jellyfin.ui

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.server.network.JvmTransportFailures

private const val MAX_DEPTH = 8

/**
 * Whether any of [failures], their causes, or anything suppressed on either, is the server asking for a client certificate. The
 * exception a call ends with is its last attempt's, and a retry can fail in a way that hides the more telling failure before it, so
 * the earlier ones are read too. A coroutine that resumes with an exception may hand on a copy that holds the original as its cause.
 */
internal fun asksForClientCertificate(failures: List<Throwable>): Boolean = failures.any { asks(it, MAX_DEPTH) }

private fun asks(
    failure: Throwable,
    depth: Int,
): Boolean =
    JvmTransportFailures.read(failure) == ServerFailure.ClientCertificateNeeded ||
        (depth > 0 && listOfNotNull(failure.cause).plus(failure.suppressed).any { asks(it, depth - 1) })
