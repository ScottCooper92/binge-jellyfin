package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.TitleDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleLinksTest {
    private fun detail(ids: Map<String, String>) =
        TitleDetail(
            item = BrowseItem("m1", "Alien", ItemKind.Movie),
            originalTitle = null,
            overview = null,
            taglines = emptyList(),
            genres = emptyList(),
            studios = emptyList(),
            criticRating = null,
            premiereDate = null,
            endDate = null,
            status = null,
            externalIds = ids,
            people = emptyList(),
            versions = emptyList(),
            chapters = emptyList(),
            seasonName = null,
        )

    @Test
    fun `the share address is the TMDb page, by movie or tv`() {
        val ids = mapOf("Tmdb" to "348", "Imdb" to "tt0078748")
        assertEquals("https://www.themoviedb.org/movie/348", detail(ids).shareUrl(series = false))
        assertEquals("https://www.themoviedb.org/tv/348", detail(ids).shareUrl(series = true))
    }

    @Test
    fun `the provider names match whatever their case`() {
        assertEquals("https://www.themoviedb.org/movie/348", detail(mapOf("TMDB" to "348")).shareUrl(series = false))
        assertEquals("https://www.imdb.com/title/tt1/", detail(mapOf("imdb" to "tt1")).shareUrl(series = false))
    }

    @Test
    fun `without a TMDb id the share address is the IMDb page`() {
        assertEquals("https://www.imdb.com/title/tt0078748/", detail(mapOf("Imdb" to "tt0078748")).shareUrl(series = false))
    }

    @Test
    fun `an id that is not an id is never put in an address, and no ids means nothing to share`() {
        assertEquals(
            "https://www.imdb.com/title/tt1/",
            detail(mapOf("Tmdb" to "348/../x", "Imdb" to "tt1")).shareUrl(series = false),
        )
        assertNull(detail(mapOf("Tmdb" to "http://evil", "Imdb" to "nm1")).shareUrl(series = false))
        assertNull(detail(emptyMap()).shareUrl(series = true))
    }
}
