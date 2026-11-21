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

    /** Titles like [id], as the server judges them. */
    suspend fun similar(
        id: String,
        limit: Int = DEFAULT_SIMILAR,
    ): List<BrowseItem>

    companion object {
        const val DEFAULT_SIMILAR = 12
    }
}

/** People: who they are, and what on this server they are in. */
interface PersonRepository {
    /** A person by their item id (a title's [TitlePerson.id]). An id the server does not have throws its 404. */
    suspend fun person(id: String): PersonDetail?

    /** The movies and series a person appears in, paged. */
    suspend fun titlesWith(
        personId: String,
        startIndex: Int = 0,
        limit: Int = ItemsRepository.DEFAULT_PAGE_SIZE,
        withTotal: Boolean = false,
    ): Page<BrowseItem>
}

/** Collections (box sets) and their parts. */
interface CollectionRepository {
    /** A collection's parts, in release order. The collection itself is a [TitleRepository.title]. */
    suspend fun parts(collectionId: String): List<BrowseItem>

    /**
     * The collections [id] belongs to. From 12.0 the server names them; before it the user's box sets are each asked for their
     * members, which costs a read per box set, so a page loads this on its own, after the title.
     */
    suspend fun collectionsOf(id: String): List<BrowseItem>
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

    override suspend fun similar(
        id: String,
        limit: Int,
    ): List<BrowseItem> = client.browseSimilar(userId, id, limit)
}

/** [PersonRepository] over the user's own server. */
class JellyfinPersonRepository(
    private val client: JellyfinClient,
    private val userId: String,
    private val profile: JellyfinServerProfile,
) : PersonRepository {
    override suspend fun person(id: String): PersonDetail? = client.browsePerson(userId, profile, id)

    override suspend fun titlesWith(
        personId: String,
        startIndex: Int,
        limit: Int,
        withTotal: Boolean,
    ): Page<BrowseItem> = client.browseTitlesWith(userId, personId, startIndex, limit, withTotal)
}

/** [CollectionRepository] over the user's own server. */
class JellyfinCollectionRepository(
    private val client: JellyfinClient,
    private val userId: String,
    private val profile: JellyfinServerProfile,
    private val memberships: BoxSetMemberships = BoxSetMemberships(),
) : CollectionRepository {
    override suspend fun parts(collectionId: String): List<BrowseItem> = client.browseCollectionParts(userId, collectionId)

    override suspend fun collectionsOf(id: String): List<BrowseItem> = client.browseCollectionsOf(userId, profile, id, memberships)
}
