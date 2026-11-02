package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.answers
import kotlinx.coroutines.delay

/** How a console page follows the server through a restart: how often it asks, and how long it waits at each end. */
internal data class RestartTiming(
    val pollMillis: Long = POLL_MILLIS,
    val downWaitMillis: Long = DOWN_WAIT_MILLIS,
    val backWaitMillis: Long = BACK_WAIT_MILLIS,
) {
    companion object {
        const val POLL_MILLIS = 2_000L
        const val DOWN_WAIT_MILLIS = 15_000L
        const val BACK_WAIT_MILLIS = 120_000L
    }
}

/**
 * Waits for the server to go down, then to answer again. A server that never seems to go down within
 * [RestartTiming.downWaitMillis] restarted too quickly to see, and counts as back. False if it isn't back within
 * [RestartTiming.backWaitMillis].
 */
internal suspend fun JellyfinClient.awaitBack(timing: RestartTiming): Boolean {
    var waited = 0L
    var wentDown = false
    while (waited < timing.backWaitMillis) {
        delay(timing.pollMillis)
        waited += timing.pollMillis
        val up = answers()
        if (!up) wentDown = true
        if (up && (wentDown || waited >= timing.downWaitMillis)) return true
    }
    return false
}
