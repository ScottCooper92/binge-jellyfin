package com.cooper.binge.companion.jellyfin.ui

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.itemCounts
import com.cooper.binge.companion.jellyfin.server.nowPlaying
import com.cooper.binge.companion.jellyfin.server.serverInfo
import com.cooper.binge.companion.jellyfin.server.users
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * What the home's server card says about the server, as far as it has been read. [playing], [users], [movies] and
 * [series] are null until their read answers, and stay null if it fails: the card shows a placeholder, never an
 * error. A flag the server didn't send is false, so no chip. [connected] is true once any read has worked.
 */
data class ServerCardState(
    val connected: Boolean = false,
    val updateAvailable: Boolean = false,
    val restartPending: Boolean = false,
    val shuttingDown: Boolean = false,
    val playing: Int? = null,
    val users: Int? = null,
    val movies: Int? = null,
    val series: Int? = null,
)

/** How long the figures stand before the home asks again, so reopening it doesn't re-ask. */
private const val CARD_FRESH_MILLIS = 5 * 60 * 1000L

/**
 * Reads the server card's figures, each by itself so a slow or refused one never holds up another, and remembers when
 * it last did. `/System/Info` (the flags) and the user count are an administrator's; the rest are anyone's, and the
 * server answers with what that user may see: their own sessions, and the library their access allows.
 */
class ServerCardLoader(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var loadedFor: String? = null
    private var loadedAt = 0L

    /** Whether [key] (the server and user) has no figures from the last few minutes. */
    fun due(key: String): Boolean = loadedFor != key || clock() - loadedAt >= CARD_FRESH_MILLIS

    /** Reads every figure [key] may see, handing each to [update] as it arrives. */
    suspend fun load(
        key: String,
        client: JellyfinClient,
        userId: String,
        administrator: Boolean,
        update: ((ServerCardState) -> ServerCardState) -> Unit,
    ) {
        loadedFor = key
        loadedAt = clock()
        coroutineScope {
            if (administrator) {
                launchRead({ client.serverInfo() }, update) { info ->
                    copy(
                        connected = true,
                        updateAvailable = info.updateAvailable,
                        restartPending = info.restartPending,
                        shuttingDown = info.shuttingDown,
                    )
                }
                launchRead({ client.users().size }, update) { copy(connected = true, users = it) }
            }
            launchRead({ client.nowPlaying().size }, update) { copy(connected = true, playing = it) }
            launchRead({ client.itemCounts(userId) }, update) { copy(connected = true, movies = it.movies, series = it.series) }
        }
    }

    private fun <T> kotlinx.coroutines.CoroutineScope.launchRead(
        read: suspend () -> T,
        update: ((ServerCardState) -> ServerCardState) -> Unit,
        apply: ServerCardState.(T) -> ServerCardState,
    ) {
        async { attempt { read() }.onSuccess { value -> update { it.apply(value) } } }
    }
}
