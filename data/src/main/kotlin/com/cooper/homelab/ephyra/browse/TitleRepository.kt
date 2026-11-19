package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile

/** A title and its parts. Failures are the transport's, as in [LibraryRepository]. */
interface TitleRepository {
    /** A movie, series, season or episode with everything a page shows. An id the server does not have throws its 404. */
    suspend fun title(id: String): TitleDetail?

    /** A series' seasons, in the server's order. Specials are season 0; where they sit is the page's choice. */
    suspend fun seasons(seriesId: String): List<BrowseItem>

    /** A series' episodes, or one season's when [seasonId] is given, in the server's order. */
    suspend fun episodes(
        seriesId: String,
        seasonId: String? = null,
    ): List<BrowseItem>

    /** What to play next in a series, and why. See [NextEpisode]. */
    suspend fun nextEpisode(seriesId: String): NextEpisode
}

/** [TitleRepository] over the user's own server. */
class JellyfinTitleRepository(
    private val client: JellyfinClient,
    private val userId: String,
    private val profile: JellyfinServerProfile,
) : TitleRepository {
    override suspend fun title(id: String): TitleDetail? = client.browseTitle(userId, profile, id)

    override suspend fun seasons(seriesId: String): List<BrowseItem> = client.browseSeasons(userId, seriesId)

    override suspend fun episodes(
        seriesId: String,
        seasonId: String?,
    ): List<BrowseItem> = client.browseEpisodes(userId, seriesId, seasonId)

    override suspend fun nextEpisode(seriesId: String): NextEpisode = chooseNextEpisode(client.browseEpisodes(userId, seriesId, null))
}
