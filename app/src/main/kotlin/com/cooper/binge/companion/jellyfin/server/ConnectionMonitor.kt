package com.cooper.binge.companion.jellyfin.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Whether the saved server has been answering, as the last few calls to it said. */
enum class Reach {
    /** Nothing has spoken to the server since the session began, which is where every start begins. */
    Unchecked,

    /** The last call was answered, whatever the answer was. */
    Reached,

    /** A run of calls in a row got no answer: the server is offline, off the network, or its address has changed. */
    Unreached,
}

/**
 * The write side of [ConnectionMonitor]: the interceptor reports each call's outcome here without seeing the read side.
 */
interface ReachReporter {
    fun reportAnswered()

    fun reportSilent()
}

/** A single call without an answer can be a blip; only a run of them with no answer between reads as unreached (as binge-seerr's does). */
private const val UNREACHED_STREAK = 2

/**
 * Live reach of the saved server, fed by every call the signed-in client makes through [ReachInterceptor]. Observe-only:
 * nothing waits on it. [UNREACHED_STREAK] calls in a row with no answer read as [Reach.Unreached], and any answer reads as
 * [Reach.Reached] again. The home turns this into its problem page while the server is gone, and lets the page heal itself.
 */
class ConnectionMonitor : ReachReporter {
    private val lock = Any()
    private var silentStreak = 0

    private val state = MutableStateFlow(Reach.Unchecked)
    val reach: StateFlow<Reach> = state.asStateFlow()

    /** Reported from OkHttp threads, so the streak and the state move together under the lock. */
    override fun reportAnswered() =
        synchronized(lock) {
            silentStreak = 0
            state.value = Reach.Reached
        }

    override fun reportSilent() =
        synchronized(lock) {
            silentStreak++
            if (silentStreak >= UNREACHED_STREAK) state.value = Reach.Unreached
        }

    /** The session changed: whatever the last call said no longer describes this server. */
    fun reset() =
        synchronized(lock) {
            silentStreak = 0
            state.value = Reach.Unchecked
        }
}

private const val HTTP_BAD_GATEWAY = 502
private const val HTTP_SERVICE_UNAVAILABLE = 503
private const val HTTP_GATEWAY_TIMEOUT = 504

/**
 * Reports each saved-server call's outcome to the monitor and returns the response untouched. A transport failure, or a
 * gateway that says the server behind it is down (502, 503 or 504, as a reverse proxy answers when Jellyfin is stopped),
 * is no answer; a call cancelled by its caller is neither. Any other response, an error included, means the server was reached: binge-seerr also counts 429 and every
 * 5xx, but here a server that answers with a failure is the home's "couldn't load" and not "can't reach".
 */
class ReachInterceptor(
    private val reporter: ReachReporter,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response =
            try {
                chain.proceed(chain.request())
            } catch (e: IOException) {
                // A call its caller cancelled says nothing about the server.
                if (!chain.call().isCanceled()) reporter.reportSilent()
                throw e
            }
        when (response.code) {
            HTTP_BAD_GATEWAY, HTTP_SERVICE_UNAVAILABLE, HTTP_GATEWAY_TIMEOUT -> reporter.reportSilent()
            else -> reporter.reportAnswered()
        }
        return response
    }
}
