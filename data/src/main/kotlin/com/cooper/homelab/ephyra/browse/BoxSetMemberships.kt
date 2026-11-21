package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A box set and the ids of the titles in it. */
internal data class BoxSetMembers(
    val box: BrowseItem,
    val memberIds: Set<String>,
)

/**
 * Which box sets hold a title, for a server before 12.0, where no route says. The user's box sets and their members are read once
 * for the signed-in user and server, and every film page after that is answered from them. Pages that ask together share one read.
 * A read that fails is not kept. The answer is dropped when it is older than [maxAgeMillis], when [invalidate] says the library
 * changed, when [clear] ends the session, and when another server or user asks.
 */
class BoxSetMemberships(
    private val now: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = MAX_AGE_MILLIS,
) {
    private val lock = Mutex()
    private var owner: String? = null
    private var builtAt = 0L
    private var members: List<BoxSetMembers> = emptyList()

    internal suspend fun containing(
        client: JellyfinClient,
        userId: String,
        id: String,
    ): List<BrowseItem> =
        lock.withLock {
            val owned = "${client.baseUrl}|$userId"
            if (owner != owned || now() - builtAt >= maxAgeMillis) {
                members = client.browseBoxSetMembers(userId)
                owner = owned
                builtAt = now()
            }
            members.filter { id in it.memberIds }.map { it.box }
        }

    /** Marks what is held stale, so the next page reads again: the server said its library changed. */
    suspend fun invalidate() = lock.withLock { builtAt = 0L }

    suspend fun clear() =
        lock.withLock {
            owner = null
            members = emptyList()
            builtAt = 0L
        }

    private companion object {
        const val MAX_AGE_MILLIS = 15 * 60_000L
    }
}
