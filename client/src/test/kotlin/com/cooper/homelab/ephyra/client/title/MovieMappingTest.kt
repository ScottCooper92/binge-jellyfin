package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemUserData
import com.cooper.homelab.ephyra.browse.MediaTrack
import com.cooper.homelab.ephyra.browse.MediaVersion
import com.cooper.homelab.ephyra.browse.PersonKind
import com.cooper.homelab.ephyra.browse.TitleDetail
import com.cooper.homelab.ephyra.browse.TitlePerson
import com.cooper.homelab.ephyra.browse.TrackKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieMappingTest {
    private val minute = 600_000_000L

    private fun item(
        played: Boolean = false,
        position: Long = 0L,
        runtime: Long? = 120 * minute,
    ) = BrowseItem(
        id = "m1",
        name = "Alien",
        kind = ItemKind.Movie,
        year = 1979,
        communityRating = 8.5,
        officialRating = "R",
        runTimeTicks = runtime,
        imageTags = mapOf(ImageKind.Backdrop to "b1", ImageKind.Primary to "p1"),
        userData = ItemUserData(played = played, favourite = true, positionTicks = position),
    )

    private fun detail(
        item: BrowseItem = item(),
        people: List<TitlePerson> = emptyList(),
        versions: List<MediaVersion> = emptyList(),
    ) = TitleDetail(
        item = item,
        originalTitle = null,
        overview = "In space.",
        taglines = listOf("", "No one can hear you."),
        genres = listOf("Horror"),
        studios = listOf("Brandywine"),
        criticRating = null,
        premiereDate = "1979-05-25T00:00:00Z",
        endDate = null,
        status = null,
        externalIds = emptyMap(),
        people = people,
        versions = versions,
        chapters = emptyList(),
        seasonName = null,
    )

    @Test
    fun `the page carries the title's facts, with a blank tagline passed over`() {
        val movie = movieUi(detail(), emptyList(), null)
        assertEquals("Alien", movie.title)
        assertEquals("No one can hear you.", movie.tagline)
        assertEquals(MovieStats(8.5, 1979, 120, "R"), movie.stats)
        assertEquals(TitleImage("m1", ImageKind.Backdrop, "b1"), movie.backdrop)
        assertTrue(movie.favourite)
        assertFalse(movie.watched)
        assertEquals(TitleFacts(null, listOf("Brandywine"), "1979-05-25T00:00:00Z"), movie.facts)
    }

    @Test
    fun `a film with nothing started plays, a film part watched resumes with what is left, and a watched one plays again`() {
        assertEquals(PlayAction(PlayKind.Play, null), playAction(item()))
        assertEquals(PlayAction(PlayKind.Resume, 90), playAction(item(position = 30 * minute)))
        assertEquals(PlayAction(PlayKind.Resume, null), playAction(item(position = 30 * minute, runtime = null)))
        assertEquals(PlayAction(PlayKind.Play, null), playAction(item(played = true, position = 30 * minute)))
        assertEquals(PlayAction(PlayKind.Resume, 0), playAction(item(position = 200 * minute)))
    }

    @Test
    fun `a runtime under a minute is left out and one of a minute or more is kept`() {
        assertNull(movieUi(detail(item = item(runtime = 59 * minute / 60)), emptyList(), null).stats.runtimeMinutes)
        assertEquals(1, movieUi(detail(item = item(runtime = minute)), emptyList(), null).stats.runtimeMinutes)
        assertNull(runtimeMinutes(0L))
        assertEquals(90, runtimeMinutes(90 * minute + 1))
    }

    @Test
    fun `stats with nothing are empty`() {
        val bare = movieUi(detail(item = BrowseItem("x", "Bare", ItemKind.Movie)), emptyList(), null)
        assertTrue(bare.stats.isEmpty)
        assertNull(bare.backdrop)
    }

    @Test
    fun `cast and crew are split by what each did, and a crew member's subtitle is the job`() {
        val people =
            listOf(
                TitlePerson("a", "Sigourney Weaver", "Ripley", PersonKind.Actor, "t"),
                TitlePerson("b", "Guest", null, PersonKind.GuestStar, null),
                TitlePerson("c", "Ridley Scott", null, PersonKind.Director, null),
                TitlePerson("d", "Someone", null, PersonKind.Other, null),
            )
        val movie = movieUi(detail(people = people), emptyList(), null)
        assertEquals(listOf("a", "b"), movie.cast.map { it.id })
        assertEquals(listOf("c"), movie.crew.map { it.id })
        assertEquals("Ripley", movie.cast.first().subtitle)
        assertNull(movie.crew.single().subtitle)
        assertEquals(CrewJob.Director, movie.crew.single().job)
        assertNull(movie.cast.first().job)
        assertEquals(TitleImage("a", ImageKind.Primary, "t"), movie.cast.first().photo)
        assertNull(movie.cast.last().photo)
    }

    private fun video(
        width: Int?,
        range: String? = null,
    ) = MediaTrack(0, TrackKind.Video, codec = "hevc", width = width, videoRangeType = range)

    private fun version(video: MediaTrack?) =
        MediaVersion(
            id = "v",
            name = " ",
            container = "mkv",
            sizeBytes = 5L,
            bitrate = null,
            runTimeTicks = null,
            tracks =
                listOfNotNull(
                    video,
                    MediaTrack(1, TrackKind.Audio, codec = "eac3", language = "eng", channelLayout = "5.1", isDefault = true),
                    MediaTrack(2, TrackKind.Audio, displayTitle = "Spanish - AC3", codec = "ac3"),
                    MediaTrack(3, TrackKind.Subtitle, isForced = true, isExternal = true),
                ),
            defaultAudioIndex = 1,
            defaultSubtitleIndex = null,
        )

    @Test
    fun `a version names its resolution by width, shows a range other than SDR, and lists its streams`() {
        assertEquals(Resolution.Uhd, versionUi(version(video(3840, "HDR10"))).resolution)
        assertEquals("HDR10", versionUi(version(video(3840, "HDR10"))).range)
        assertEquals(Resolution.FullHd, versionUi(version(video(1920, "SDR"))).resolution)
        assertNull(versionUi(version(video(1920, "SDR"))).range)
        assertEquals(Resolution.FullHd, versionUi(version(video(1920))).resolution)
        assertEquals(Resolution.Hd, versionUi(version(video(1280))).resolution)
        assertEquals(Resolution.Sd, versionUi(version(video(720))).resolution)
        assertNull(versionUi(version(video(null))).resolution)
        val none = versionUi(version(null))
        assertNull(none.resolution)
        assertNull(none.name)
        assertEquals("mkv", none.container)
        assertEquals(listOf(1, 2), none.audio.map { it.index })
        assertEquals("ENG · EAC3 · 5.1", none.audio.first().label)
        assertEquals("Spanish - AC3", none.audio.last().label)
        assertTrue(none.audio.first().isDefault)
        val subtitle = none.subtitles.single()
        assertNull(subtitle.label)
        assertTrue(subtitle.isForced && subtitle.isExternal)
        assertEquals(1, none.defaultAudioIndex)
    }

    @Test
    fun `a card has the title, year, rating and poster of an item`() {
        val card = titleCard(item(played = true))
        assertEquals(TitleCard("m1", ItemKind.Movie, "Alien", 1979, 8.5, TitleImage("m1", ImageKind.Primary, "p1"), true), card)
    }
}
