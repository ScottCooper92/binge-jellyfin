package com.cooper.homelab.ephyra.openitem

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.NextEpisode
import com.cooper.homelab.ephyra.browse.TitleDetail
import com.cooper.homelab.ephyra.browse.TitleRepository
import com.cooper.homelab.ephyra.server.IndexKey
import com.cooper.homelab.ephyra.server.IndexType
import com.cooper.homelab.ephyra.server.JellyfinItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where a request leads: the id on this server, then the TMDB id, then the home. */
class OpenItemResolverTest {
    private val server = "http://jelly.lan:8096"
    private val movieId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val seriesId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    private val episodeId = "cccccccccccccccccccccccccccccccc"
    private val indexedId = "dddddddddddddddddddddddddddddddd"

    private class Titles(
        private val kinds: Map<String, ItemKind>,
        private val failing: Boolean = false,
    ) : TitleRepository {
        val asked = mutableListOf<String>()

        override suspend fun title(id: String): TitleDetail? {
            asked += id
            check(!failing) { "server unreachable" }
            return kinds[id]?.let { detail(id, it) }
        }

        override suspend fun seasons(seriesId: String): List<BrowseItem> = error("not used")

        override suspend fun episodes(
            seriesId: String,
            seasonId: String?,
        ): List<BrowseItem> = error("not used")

        override suspend fun nextEpisode(seriesId: String): NextEpisode = error("not used")

        override suspend fun similar(
            id: String,
            limit: Int,
        ): List<BrowseItem> = error("not used")

        private fun detail(
            id: String,
            kind: ItemKind,
        ) = TitleDetail(
            item = BrowseItem(id, "A title", kind),
            originalTitle = null,
            overview = null,
            taglines = emptyList(),
            genres = emptyList(),
            studios = emptyList(),
            criticRating = null,
            premiereDate = null,
            endDate = null,
            status = null,
            externalIds = emptyMap(),
            people = emptyList(),
            versions = emptyList(),
            chapters = emptyList(),
            seasonName = null,
        )
    }

    private val titles = Titles(mapOf(movieId to ItemKind.Movie, seriesId to ItemKind.Series, episodeId to ItemKind.Episode))
    private val indexed = mutableMapOf<IndexKey, String>()
    private val lookups = mutableListOf<IndexKey>()

    private fun resolve(
        request: OpenItemRequest?,
        repository: TitleRepository = titles,
        base: String = server,
    ) = runBlocking {
        resolveOpenTarget(request!!, base, repository) { key ->
            lookups += key
            indexed[key]?.let { JellyfinItem(it, "A title", "Movie", key.tmdbId, null, null) }
        }
    }

    private fun request(
        url: String? = server,
        item: String? = movieId,
        type: String? = "movie",
        tmdb: Int? = 603,
    ) = OpenItemRequest.parse(url, item, type, tmdb)

    @Test
    fun `an item id on this server opens its page`() {
        assertEquals(OpenTarget.Title(ItemKind.Movie, movieId), resolve(request()))
        assertEquals(OpenTarget.Title(ItemKind.Series, seriesId), resolve(request(item = seriesId, type = "tv", tmdb = 1399)))
    }

    @Test
    fun `the id is found for a server spelled another way`() {
        assertEquals(OpenTarget.Title(ItemKind.Movie, movieId), resolve(request(url = "http://JELLY.lan:8096/"), base = "jelly.lan:8096"))
    }

    @Test
    fun `the item id is not asked of a different server and the TMDB id is used`() {
        indexed[IndexKey(IndexType.MOVIE, 603)] = indexedId
        val target = resolve(request(url = "http://other.lan:8096"))
        assertEquals(OpenTarget.Title(ItemKind.Movie, indexedId), target)
        assertEquals(emptyList<String>(), titles.asked)
    }

    @Test
    fun `no server address means the id is not asked and the TMDB id is used`() {
        indexed[IndexKey(IndexType.MOVIE, 603)] = indexedId
        assertEquals(OpenTarget.Title(ItemKind.Movie, indexedId), resolve(request(url = null)))
        assertEquals(emptyList<String>(), titles.asked)
    }

    @Test
    fun `an id the server does not have falls to the TMDB id`() {
        indexed[IndexKey(IndexType.SERIES, 1399)] = indexedId
        val target = resolve(request(item = "e".repeat(32), type = "tv", tmdb = 1399))
        assertEquals(OpenTarget.Title(ItemKind.Series, indexedId), target)
    }

    @Test
    fun `an id of another kind than the caller named falls through`() {
        assertEquals(OpenTarget.Home, resolve(request(item = seriesId, type = "movie", tmdb = 603)))
    }

    @Test
    fun `an episode is not a movie or a series so it falls through to the TMDB id`() {
        indexed[IndexKey(IndexType.SERIES, 1399)] = indexedId
        assertEquals(OpenTarget.Title(ItemKind.Series, indexedId), resolve(request(item = episodeId, type = "tv", tmdb = 1399)))
        assertEquals(OpenTarget.Home, resolve(request(item = episodeId, type = null, tmdb = null)))
    }

    @Test
    fun `an id with no TMDB type opens whichever of a movie or a series it is`() {
        assertEquals(OpenTarget.Title(ItemKind.Series, seriesId), resolve(request(item = seriesId, type = null, tmdb = null)))
    }

    @Test
    fun `a TMDB id alone opens by the index`() {
        indexed[IndexKey(IndexType.MOVIE, 603)] = indexedId
        assertEquals(OpenTarget.Title(ItemKind.Movie, indexedId), resolve(request(url = null, item = null)))
        assertEquals(listOf(IndexKey(IndexType.MOVIE, 603)), lookups)
    }

    @Test
    fun `nothing that matches opens the home`() {
        assertEquals(OpenTarget.Home, resolve(request(item = "e".repeat(32))))
        assertEquals(OpenTarget.Home, resolve(request(url = null, item = null)))
    }

    @Test
    fun `a server that cannot be read opens the home and never fails`() {
        val down = Titles(emptyMap(), failing = true)
        assertEquals(OpenTarget.Home, resolve(request(), repository = down))
    }
}
