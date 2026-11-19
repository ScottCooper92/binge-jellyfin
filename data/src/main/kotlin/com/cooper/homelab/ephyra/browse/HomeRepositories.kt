package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient

/** Continue watching. Failures are the transport's, as in [LibraryRepository]. */
interface ResumeRepository {
    /** What the user started and did not finish, most recent first; of one [library] when given. */
    suspend fun resume(
        library: LibraryView? = null,
        startIndex: Int = 0,
        limit: Int = DEFAULT_LIMIT,
        withTotal: Boolean = false,
    ): Page<BrowseItem>

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}

/** Next up: the next episode of each series the user is watching. */
interface NextUpRepository {
    suspend fun nextUp(
        library: LibraryView? = null,
        startIndex: Int = 0,
        limit: Int = DEFAULT_LIMIT,
        withTotal: Boolean = false,
    ): Page<BrowseItem>

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}

/** What is new in a library. */
interface LatestRepository {
    /** The newest titles in [library]; [hidePlayed] leaves out what the user has watched. */
    suspend fun latest(
        library: LibraryView,
        limit: Int = DEFAULT_LIMIT,
        hidePlayed: Boolean = false,
    ): List<BrowseItem>

    companion object {
        const val DEFAULT_LIMIT = 16
    }
}

class JellyfinResumeRepository(
    private val client: JellyfinClient,
    private val userId: String,
) : ResumeRepository {
    override suspend fun resume(
        library: LibraryView?,
        startIndex: Int,
        limit: Int,
        withTotal: Boolean,
    ): Page<BrowseItem> = client.browseResume(userId, library, startIndex, limit, withTotal)
}

class JellyfinNextUpRepository(
    private val client: JellyfinClient,
    private val userId: String,
) : NextUpRepository {
    override suspend fun nextUp(
        library: LibraryView?,
        startIndex: Int,
        limit: Int,
        withTotal: Boolean,
    ): Page<BrowseItem> = client.browseNextUp(userId, library, startIndex, limit, withTotal)
}

class JellyfinLatestRepository(
    private val client: JellyfinClient,
    private val userId: String,
) : LatestRepository {
    override suspend fun latest(
        library: LibraryView,
        limit: Int,
        hidePlayed: Boolean,
    ): List<BrowseItem> = client.browseLatest(userId, library, limit, hidePlayed, groupItems = true)
}
