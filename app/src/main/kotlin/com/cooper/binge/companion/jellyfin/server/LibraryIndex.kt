package com.cooper.binge.companion.jellyfin.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The TMDB ids the server holds, as Binge names a title: movie or series, and its id. */
enum class IndexType { MOVIE, SERIES }

data class IndexKey(
    val type: IndexType,
    val tmdbId: Int,
)

/**
 * TMDB id → Jellyfin item, for the signed-in user. Jellyfin has no query by TMDB id (only
 * `hasTmdbId`), so the index is every movie and series with one, paged in, held in memory and
 * refreshed when it is older than [maxAgeMillis]. A miss on a recent index is answered from it; a
 * miss on one older than [missRefreshMillis] refreshes first, so a title added a minute ago is found.
 */
class LibraryIndex(
    private val now: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = MAX_AGE_MILLIS,
    private val missRefreshMillis: Long = MISS_REFRESH_MILLIS,
) {
    private val lock = Mutex()
    private var owner: String? = null
    private var builtAt = 0L
    private var byKey: Map<IndexKey, JellyfinItem> = emptyMap()
    private var byId: Map<String, IndexKey> = emptyMap()

    /** How many titles the last build found; null before one has run. */
    val size: Int? get() = if (owner == null) null else byKey.size

    suspend fun find(
        client: JellyfinClient,
        userId: String,
        key: IndexKey,
    ): JellyfinItem? {
        ensure(client, userId, maxAgeMillis)
        lock.withLock { byKey[key] }?.let { return it }
        ensure(client, userId, missRefreshMillis)
        return lock.withLock { byKey[key] }
    }

    /** The TMDB key of a series or movie by its Jellyfin id, from the index as it stands. */
    suspend fun keyOf(
        client: JellyfinClient,
        userId: String,
        itemId: String,
    ): IndexKey? {
        ensure(client, userId, maxAgeMillis)
        return lock.withLock { byId[itemId] }
    }

    suspend fun refresh(
        client: JellyfinClient,
        userId: String,
    ) = ensure(client, userId, maxAge = 0L)

    /** Marks the index stale, so the next lookup rebuilds it: the server said its library changed. */
    suspend fun invalidate() = lock.withLock { builtAt = 0L }

    suspend fun clear() =
        lock.withLock {
            owner = null
            byKey = emptyMap()
            byId = emptyMap()
            builtAt = 0L
        }

    private suspend fun ensure(
        client: JellyfinClient,
        userId: String,
        maxAge: Long,
    ) {
        lock.withLock {
            val owned = "${client.baseUrl}|$userId"
            if (owner == owned && now() - builtAt < maxAge) return
            val items = mutableListOf<JellyfinItem>()
            var start = 0
            while (true) {
                val (page, total) = client.libraryPage(userId, start, PAGE)
                items += page
                start += page.size
                if (page.isEmpty() || start >= total) break
            }
            val keyed =
                items.mapNotNull { item ->
                    val type =
                        when (item.type) {
                            "Movie" -> IndexType.MOVIE
                            "Series" -> IndexType.SERIES
                            else -> null
                        }
                    val tmdb = item.tmdbId
                    if (type == null || tmdb == null) null else IndexKey(type, tmdb) to item
                }
            byKey = keyed.toMap()
            byId = keyed.associate { (key, item) -> item.id to key }
            owner = owned
            builtAt = now()
        }
    }

    private companion object {
        const val PAGE = 500
        const val MAX_AGE_MILLIS = 15 * 60_000L
        const val MISS_REFRESH_MILLIS = 60_000L
    }
}
