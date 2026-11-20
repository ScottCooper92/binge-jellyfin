package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinItem
import com.cooper.homelab.ephyra.server.episodes
import com.cooper.homelab.ephyra.server.item

private const val SPECIALS = 0

/**
 * The episode that follows the one playing, for the player's Up next: the next numbered episode of the series the server has a
 * file for, in series order and across seasons. `GET /Shows/{seriesId}/Episodes` lists them, so a missing episode is skipped.
 * `/Shows/NextUp` answers what to watch next, which is not what follows this one when a series is rewatched. A film has none, and
 * neither has a special, which belongs to no run of episodes.
 */
class JellyfinNextEpisodes(
    private val client: JellyfinClient,
    private val userId: String,
) : NextEpisodes {
    override suspend fun after(item: PlayItem): PlayItem? {
        val current = client.item(userId, item.itemId)?.takeIf { it.type == "Episode" } ?: return null
        val seriesId = current.seriesId ?: return null
        val position = current.position() ?: return null
        return client
            .episodes(userId, seriesId)
            .filter { episode -> episode.position()?.let { it > position } == true }
            .minByOrNull { checkNotNull(it.position()) }
            ?.let { next ->
                val (season, number) = checkNotNull(next.position())
                PlayItem(next.id, item.title.copy(seasonNumber = season, episodeNumber = number, episodeTitle = next.name))
            }
    }

    /** Where an episode sits in the series, as season and number; null for a special or one with no number. */
    private fun JellyfinItem.position(): EpisodePosition? {
        val season = seasonNumber?.takeIf { it != SPECIALS } ?: return null
        val number = episodeNumber ?: return null
        return EpisodePosition(season, number)
    }
}

private data class EpisodePosition(
    val season: Int,
    val number: Int,
) : Comparable<EpisodePosition> {
    override fun compareTo(other: EpisodePosition): Int = compareValuesBy(this, other, { it.season }, { it.number })
}
