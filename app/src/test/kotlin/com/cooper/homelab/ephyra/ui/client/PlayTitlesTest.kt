package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.home.HomeCaption
import com.cooper.homelab.ephyra.client.home.HomeCard
import com.cooper.homelab.ephyra.client.title.PlayKind
import com.cooper.homelab.ephyra.client.title.SeriesPlay
import com.cooper.homelab.player.api.PlayTitle
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the player is told an item is called: an episode is named by its series, its numbers and its own name. */
class PlayTitlesTest {
    private fun card(
        kind: ItemKind,
        title: String,
        caption: HomeCaption,
    ) = HomeCard(
        id = "i1",
        kind = kind,
        title = title,
        caption = caption,
        poster = null,
        backdrop = null,
        progress = null,
        rating = null,
        resumes = true,
    )

    @Test
    fun `a series' play is titled by the series with the episode's own name`() {
        val item = SeriesPlay.Episode(PlayKind.Play, "e3", 1, 3, null, "Pulse").playItem("Test Signals")
        assertEquals("e3", item.itemId)
        assertEquals(PlayTitle("Test Signals", seasonNumber = 1, episodeNumber = 3, episodeTitle = "Pulse"), item.title)
    }

    @Test
    fun `a Continue watching episode is titled with its numbers and its own name`() {
        val title = card(ItemKind.Episode, "Test Signals", HomeCaption.Episode(1, 3, "Pulse")).playTitle()
        assertEquals(PlayTitle("Test Signals", seasonNumber = 1, episodeNumber = 3, episodeTitle = "Pulse"), title)
    }

    @Test
    fun `a Continue watching film is titled by its name alone`() {
        assertEquals(PlayTitle("Colour Bars"), card(ItemKind.Movie, "Colour Bars", HomeCaption.Year(2020)).playTitle())
    }
}
