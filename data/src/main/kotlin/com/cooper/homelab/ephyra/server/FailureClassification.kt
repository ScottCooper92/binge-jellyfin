package com.cooper.homelab.ephyra.server

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.server.network.FailureMapper
import com.cooper.homelab.core.server.network.NoBody
import com.cooper.homelab.core.server.network.RequestFacts
import com.cooper.homelab.core.server.network.TransportFailureReader
import java.io.IOException
import java.io.InterruptedIOException

/** A call that did not get an answer: a socket, a name, a timeout, or an answer that was not Jellyfin's. */
private val transport =
    TransportFailureReader { failure ->
        if (failure is IOException) ServerFailure.Unreachable(timedOut = failure is InterruptedIOException) else null
    }

private val mapper = FailureMapper(transport)

/**
 * Why a call to the server failed, by the shared library's rules: a 5xx is the server failing (worth another try), a 401 or
 * 403 is not permitted, a 404 is gone, any other answer is a refusal, and anything that is not an answer is unreachable. The
 * server's own words stay in [JellyfinHttpException.reason]; this keeps no text. A cancellation is rethrown.
 */
internal suspend fun Throwable.serverFailure(): ServerFailure =
    if (this is JellyfinHttpException) {
        mapper.fromStatus(code, NoBody, RequestFacts(method = "PUT", path = ""))
    } else {
        mapper.fromThrowable(this)
    }
