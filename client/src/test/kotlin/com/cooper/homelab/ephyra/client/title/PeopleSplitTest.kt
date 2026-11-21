package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.PersonKind
import com.cooper.homelab.ephyra.browse.TitleDetail
import com.cooper.homelab.ephyra.browse.TitlePerson
import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleSplitTest {
    private val people =
        listOf(
            TitlePerson("a", "Actor", "Ripley", PersonKind.Actor, null),
            TitlePerson("g", "Guest", "Cat", PersonKind.GuestStar, null),
            TitlePerson("d", "Director", null, PersonKind.Director, null),
            TitlePerson("w", "Writer", null, PersonKind.Writer, null),
            TitlePerson("p", "Producer", null, PersonKind.Producer, null),
            TitlePerson("c", "Composer", null, PersonKind.Composer, null),
            TitlePerson("cr", "Creator", null, PersonKind.Creator, null),
            TitlePerson("o", "Other", null, PersonKind.Other, null),
        )

    private fun detail(kind: ItemKind) =
        TitleDetail(
            item = BrowseItem("t1", "Alien", kind),
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
            people = people,
            versions = emptyList(),
            chapters = emptyList(),
            seasonName = null,
        )

    @Test
    fun `cast is the actors and guest stars and crew the four crafts`() {
        val d = detail(ItemKind.Movie)
        assertEquals(listOf("a", "g"), castOf(d).map { it.id })
        assertEquals(listOf("d", "w", "p", "c"), crewOf(d).map { it.id })
    }

    @Test
    fun `the movie page the series page the episode page and the people page give the same split`() {
        val movie = detail(ItemKind.Movie)
        val series = detail(ItemKind.Series)
        val m = movieUi(movie, emptyList(), null)
        val s = seriesUi(series, emptyList(), null, emptyList(), null, EpisodesState.Loading)
        val e = episodePageUi(detail(ItemKind.Episode), null)
        val p = titlePeopleUi(movie)
        assertEquals(m.cast, s.cast)
        assertEquals(m.cast, p.cast)
        assertEquals(m.cast, e.guestCast)
        assertEquals(m.crew, s.crew)
        assertEquals(m.crew, p.crew)
        assertEquals(m.crew, e.crew)
    }
}
