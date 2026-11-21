package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinUnreadableAnswer
import com.cooper.homelab.ephyra.server.JellyfinVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleReadsTest {
    private val jellyfin = FakeJellyfin()
    private val identity = ClientIdentity("Ephyra", "Pixel", "device-1", "1.0")
    private val client =
        JellyfinClient(OkHttpClient(), jellyfin.baseUrl.toHttpUrl(), identity, Dispatchers.IO, logger = DiscardLogger)
            .withToken("token-1")
    private val titles = JellyfinTitleRepository(client, "user-1", JellyfinServerProfile(JellyfinVersion(10, 11, 6)))
    private val oldTitles = JellyfinTitleRepository(client, "user-1", JellyfinServerProfile(JellyfinVersion(10, 8, 13)))

    @After
    fun close() = jellyfin.close()

    private fun add(title: FakeTitle) = jellyfin.browse.titles.add(title)

    private fun episode(
        id: String,
        season: Int,
        number: Int,
        played: Boolean = false,
        position: Long = 0L,
    ) = add(
        FakeTitle(
            id,
            "Episode $number",
            "lib-shows",
            "Episode",
            played = played,
            positionTicks = position,
            seriesId = "s1",
            seriesName = "Show",
            season = season,
            episode = number,
            seasonId = "season-$season",
        ),
    )

    private val movieJson =
        """
        "OriginalTitle":"Alien","Overview":"In space.","Taglines":["No one can hear you."],"Genres":["Horror","Sci-Fi"],
        "Studios":[{"Name":"Brandywine","Id":"x"}],"CriticRating":93.5,"PremiereDate":"1979-05-25T00:00:00.0000000Z",
        "ProviderIds":{"Tmdb":"348","Imdb":"tt0078748","Gone":null},
        "People":[{"Id":"p1","Name":"Sigourney Weaver","Role":"Ripley","Type":"Actor","PrimaryImageTag":"pt"},
                  {"Id":"p2","Name":"Ridley Scott","Type":"Director"},{"Name":"No id","Type":"Actor"},
                  {"Id":"p3","Name":"Odd","Type":"Lyricist"}],
        "Chapters":[{"StartPositionTicks":0,"Name":"Chapter 1","ImageTag":"c1"},{"StartPositionTicks":6000000000}],
        "MediaSources":[
          {"Id":"v1","Name":"Alien 4K","Container":"mkv","Size":9000000000,"Bitrate":20000000,"RunTimeTicks":7000000000,
           "DefaultAudioStreamIndex":1,"DefaultSubtitleStreamIndex":3,
           "MediaStreams":[
             {"Index":0,"Type":"Video","Codec":"hevc","Width":3840,"Height":2160,"VideoRange":"HDR","VideoRangeType":"HDR10","BitDepth":10,"AverageFrameRate":23.976,"Profile":"Main 10"},
             {"Index":1,"Type":"Audio","Codec":"eac3","Language":"eng","Channels":6,"ChannelLayout":"5.1","BitRate":640000,"IsDefault":true,"DisplayTitle":"English - 5.1"},
             {"Index":2,"Type":"Audio","Codec":"ac3","Language":"spa","Channels":2},
             {"Index":3,"Type":"Subtitle","Codec":"subrip","Language":"eng","IsForced":true,"IsExternal":true,"IsHearingImpaired":true},
             {"Index":4,"Type":"Attachment","Codec":"ttf"},
             {"Type":"Audio"}]},
          {"Id":"v2","Container":"mp4"}]
        """.trimIndent()

    @Test
    fun `a movie reads everything a page shows`() =
        runBlocking {
            add(FakeTitle("m1", "Alien", "lib-movies", year = 1979, extra = movieJson))
            val detail = titles.title("m1")!!
            assertEquals("Alien", detail.item.name)
            assertNull(detail.originalTitle)
            assertEquals("In space.", detail.overview)
            assertEquals(listOf("No one can hear you."), detail.taglines)
            assertEquals(listOf("Horror", "Sci-Fi"), detail.genres)
            assertEquals(listOf("Brandywine"), detail.studios)
            assertEquals(93.5, detail.criticRating!!, 0.0)
            assertEquals(mapOf("Tmdb" to "348", "Imdb" to "tt0078748"), detail.externalIds)
            assertEquals(listOf("p1", "p2", "p3"), detail.people.map { it.id })
            assertEquals(listOf(PersonKind.Actor, PersonKind.Director, PersonKind.Other), detail.people.map { it.kind })
            assertEquals("Ripley", detail.people.first().role)
            assertNull(detail.people[1].role)
            assertEquals(listOf("Chapter 1", null), detail.chapters.map { it.name })
            assertEquals(6_000_000_000L, detail.chapters.last().startTicks)
        }

    @Test
    fun `a trailer, web pages and tags read, keeping only http and https addresses`() =
        runBlocking {
            add(
                FakeTitle(
                    "m3",
                    "Linked",
                    "lib-movies",
                    extra =
                        """
                        "RemoteTrailers":[{"Url":"javascript:alert(1)"},{"Name":"Trailer","Url":" https://youtu.be/abc "},{"Url":"https://youtu.be/def"}],
                        "ExternalUrls":[{"Name":"Studio","Url":"https://example.com/film"},{"Name":"Site","Url":"file:///etc/passwd"},
                          {"Name":"TheMovieDb","Url":"https://www.themoviedb.org/movie/348"},
                          {"Name":"IMDb","Url":"https://www.imdb.com/title/tt0078748/"},{"Name":"Again","Url":"https://example.com/film"},
                          {"Name":"Bare","Url":"not a url"},{"Name":"None"}],
                        "Tags":["Slow burn"," ","Slow burn","Space"]
                        """.trimIndent(),
                ),
            )
            val detail = titles.title("m3")!!
            assertEquals("https://youtu.be/abc", detail.trailerUrl)
            assertEquals(listOf(TitleLinkKind.Imdb, TitleLinkKind.Tmdb, TitleLinkKind.Other), detail.links.map { it.kind })
            assertEquals(listOf("IMDb", "TheMovieDb", "Studio"), detail.links.map { it.name })
            assertEquals("https://example.com/film", detail.links.last().url)
            assertEquals(listOf("Slow burn", "Space"), detail.tags)
        }

    @Test
    fun `a title with no trailer, links or tags reads them empty`() =
        runBlocking {
            add(FakeTitle("m4", "Plain", "lib-movies", extra = "\"RemoteTrailers\":[{\"Url\":\"ftp://x/y\"}]"))
            val detail = titles.title("m4")!!
            assertNull(detail.trailerUrl)
            assertTrue(detail.links.isEmpty() && detail.tags.isEmpty())
        }

    @Test
    fun `versions carry what a version and a track picker need`() =
        runBlocking {
            add(FakeTitle("m1", "Alien", "lib-movies", extra = movieJson))
            val versions = titles.title("m1")!!.versions
            assertEquals(listOf("v1", "v2"), versions.map { it.id })
            val first = versions.first()
            assertEquals("mkv", first.container)
            assertEquals(9_000_000_000L, first.sizeBytes)
            assertEquals(1, first.defaultAudioIndex)
            assertEquals(3, first.defaultSubtitleIndex)
            val video = first.video!!
            assertEquals(3840, video.width)
            assertEquals("HDR10", video.videoRangeType)
            assertEquals(10, video.bitDepth)
            assertEquals(listOf(1, 2), first.audio.map { it.index })
            assertEquals(6, first.audio.first().channels)
            assertTrue(first.audio.first().isDefault)
            val subtitle = first.subtitles.single()
            assertTrue(subtitle.isForced && subtitle.isExternal && subtitle.isHearingImpaired)
            assertEquals("eng", subtitle.language)
            // An unknown stream type is Other; a stream with no index is dropped; a bare version has no tracks.
            assertEquals(TrackKind.Other, first.tracks.first { it.index == 4 }.kind)
            assertEquals(5, first.tracks.size)
            assertTrue(versions.last().tracks.isEmpty())
            assertNull(versions.last().video)
        }

    @Test
    fun `a title with only the bare fields reads, with every optional one empty`() =
        runBlocking {
            add(FakeTitle("m2", "Bare", "lib-movies"))
            val detail = titles.title("m2")!!
            assertNull(detail.overview)
            assertTrue(detail.people.isEmpty() && detail.versions.isEmpty() && detail.chapters.isEmpty() && detail.genres.isEmpty())
            assertTrue(detail.externalIds.isEmpty())
        }

    @Test
    fun `the title route is chosen by the profile, and an unknown id is the server's 404`() {
        add(FakeTitle("m1", "Alien", "lib-movies"))
        runBlocking {
            titles.title("m1")
            oldTitles.title("m1")
        }
        val paths = jellyfin.requests.map { it.url.encodedPath }
        assertTrue("/Items/m1" in paths)
        assertTrue("/Users/user-1/Items/m1" in paths)
        assertEquals(
            "user-1",
            jellyfin.browse.itemRequests
                .first()
                .url
                .queryParameter("userId"),
        )
        assertEquals(404, assertThrows(JellyfinHttpException::class.java) { runBlocking { titles.title("nope") } }.code)
        jellyfin.notJson += "/Items/m1"
        assertThrows(JellyfinUnreadableAnswer::class.java) { runBlocking { titles.title("m1") } }
    }

    @Test
    fun `seasons and episodes list in the server's order, with season numbers, ids and overviews`() =
        runBlocking {
            add(
                FakeTitle(
                    "sp",
                    "Specials",
                    "lib-shows",
                    "Season",
                    seriesId = "s1",
                    season = 0,
                    episode = 0,
                    extra = "\"ChildCount\":2,\"IndexNumber\":0",
                ),
            )
            add(FakeTitle("season-1", "Season 1", "lib-shows", "Season", seriesId = "s1", extra = "\"ChildCount\":3,\"IndexNumber\":1"))
            episode("e1", 1, 1)
            episode("e2", 1, 2)
            episode("e3", 2, 1)
            val seasons = titles.seasons("s1")
            assertEquals(listOf("sp", "season-1"), seasons.map { it.id })
            assertEquals(listOf(0, 1), seasons.map { it.seasonNumber })
            assertTrue(seasons.all { it.episodeNumber == null && it.kind == ItemKind.Season })
            assertEquals(3, seasons.last().childCount)
            assertEquals(listOf("e1", "e2", "e3"), titles.episodes("s1").map { it.id })
            val second = titles.episodes("s1", seasonId = "season-1")
            assertEquals(listOf("e1", "e2"), second.map { it.id })
            assertEquals("season-1", second.first().seasonId)
            assertEquals(
                "season-1",
                jellyfin.browse.itemRequests
                    .last()
                    .url
                    .queryParameter("seasonId"),
            )
            assertTrue(titles.episodes("other").isEmpty())
            assertTrue(titles.seasons("other").isEmpty())
        }

    @Test
    fun `next episode, for each state a series can be in`() =
        runBlocking {
            assertEquals(NextEpisode.None, titles.nextEpisode("s1"))
            assertNull(titles.nextEpisode("s1").episode)

            episode("sp1", 0, 1)
            episode("e1", 1, 1)
            episode("e2", 1, 2)
            episode("e3", 1, 3)
            // Untouched: the first episode that is not a special.
            assertEquals("e1", (titles.nextEpisode("s1") as NextEpisode.Start).episode.id)
        }

    @Test
    fun `next episode follows what was watched`() =
        runBlocking {
            episode("e1", 1, 1, played = true)
            episode("e2", 1, 2)
            episode("e3", 1, 3)
            assertEquals("e2", (titles.nextEpisode("s1") as NextEpisode.Next).episode.id)
            jellyfin.browse.titles.clear()
            episode("e1", 1, 1, played = true)
            episode("e2", 1, 2, position = 100)
            episode("e3", 1, 3)
            assertEquals("e2", (titles.nextEpisode("s1") as NextEpisode.Resume).episode.id)
            jellyfin.browse.titles.clear()
            episode("e1", 1, 1, played = true)
            episode("e2", 1, 2, played = true)
            // Everything watched: nothing is next, and the first episode is there to watch again.
            val all = titles.nextEpisode("s1") as NextEpisode.AllWatched
            assertEquals("e1", all.first.id)
            assertNull(all.episode)
            assertFalse(titles.nextEpisode("s1") is NextEpisode.Next)
        }

    @Test
    fun `an earlier unwatched episode is next when the later ones are watched`() =
        runBlocking {
            episode("e1", 1, 1)
            episode("e2", 1, 2, played = true)
            assertEquals("e1", (titles.nextEpisode("s1") as NextEpisode.Next).episode.id)
        }

    @Test
    fun `a series of only specials offers them`() =
        runBlocking {
            episode("sp1", 0, 1)
            assertEquals("sp1", (titles.nextEpisode("s1") as NextEpisode.Start).episode.id)
        }
}
