package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.FakeItem
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.player.api.PlayTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Up next, read from the server's episode list. */
class JellyfinNextEpisodesTest {
    private val jellyfin = FakeJellyfin()
    private val client =
        JellyfinClient(
            OkHttpClient(),
            jellyfin.baseUrl.toHttpUrl(),
            ClientIdentity("Ephyra", "Pixel", "device-1", "1"),
            Dispatchers.IO,
            token = "token-1",
            logger = DiscardLogger,
        )
    private val next = JellyfinNextEpisodes(client, "user-1")
    private val show = PlayTitle("Harbour Lights", backdropUrl = "https://jf.example/backdrop", attribution = "Home server")

    private fun episode(
        id: String,
        season: Int?,
        number: Int?,
        locationType: String = "FileSystem",
    ) = FakeItem(id, "Episode $id", "Episode", seriesId = "series-1", season = season, episode = number, locationType = locationType)

    @Before
    fun seed() {
        jellyfin.items +=
            listOf(
                episode("s1e2", 1, 2),
                episode("s2e1", 2, 1),
                episode("s1e1", 1, 1),
                episode("s1e3", 1, 3, locationType = "Virtual"),
                episode("special", 0, 1),
                episode("unnumbered", 1, null),
                FakeItem("film", "A Film", "Movie"),
            )
    }

    @After
    fun close() = jellyfin.close()

    private fun after(id: String) = runBlocking { next.after(PlayItem(id, show)) }

    @Test
    fun `the next episode is the next number in the season, whatever order the server lists them in`() {
        val following = after("s1e1")
        assertEquals("s1e2", following?.itemId)
        assertEquals(1, following?.title?.seasonNumber)
        assertEquals(2, following?.title?.episodeNumber)
        assertEquals("Episode s1e2", following?.title?.episodeTitle)
    }

    @Test
    fun `the player's title carries over, so Up next names the same show with the same artwork`() {
        val title = after("s1e1")!!.title
        assertEquals("Harbour Lights", title.title)
        assertEquals("https://jf.example/backdrop", title.backdropUrl)
        assertEquals("Home server", title.attribution)
    }

    @Test
    fun `a missing episode is skipped, and the season after follows the last of the one before`() {
        assertEquals("s2e1", after("s1e2")?.itemId)
    }

    @Test
    fun `the last episode, a special, a film and an item the server does not know have none`() {
        assertNull(after("s2e1"))
        assertNull(after("special"))
        assertNull(after("film"))
        assertNull(after("nobody"))
    }

    @Test
    fun `an episode with no number is not a place to go`() {
        assertNull(after("unnumbered"))
        assertEquals("s1e2", after("s1e1")?.itemId)
    }
}
