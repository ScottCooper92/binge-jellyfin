package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.PersonKind
import com.cooper.homelab.ephyra.browse.TitleDetail

private val EPISODE_CREW = setOf(PersonKind.Director, PersonKind.Writer, PersonKind.Producer, PersonKind.Composer)

/** The episode page from what the server answered for the episode and, once read, its season's episodes. */
fun episodePageUi(
    detail: TitleDetail,
    season: EpisodesState?,
): EpisodePageUi {
    val item = detail.item
    val row = episodeUi(item)
    return EpisodePageUi(
        id = item.id,
        title = item.name,
        seriesId = item.seriesId,
        seriesName = item.seriesName,
        seasonId = item.seasonId,
        seasonNumber = item.seasonNumber,
        episodeNumber = item.episodeNumber,
        still = row.still,
        stats =
            EpisodeStats(
                rating = item.communityRating,
                runtimeMinutes = row.runtimeMinutes,
                certification = item.officialRating?.takeIf { it.isNotBlank() },
            ),
        play = playAction(item),
        watched = item.userData.played,
        favourite = item.userData.favourite,
        overview = detail.overview?.takeIf { it.isNotBlank() },
        airDate = detail.premiereDate,
        versions = detail.versions.map(::versionUi),
        guestCast = detail.people.filter { it.kind == PersonKind.GuestStar || it.kind == PersonKind.Actor }.map(::personUi),
        crew = detail.people.filter { it.kind in EPISODE_CREW }.map(::personUi),
        season = season,
    )
}

/** The season page from the season and its episodes so far. */
fun seasonPageUi(
    detail: TitleDetail,
    episodes: List<BrowseItem>?,
    failure: EpisodesState.Failed?,
): SeasonPageUi {
    val item = detail.item
    return SeasonPageUi(
        id = item.id,
        name = item.name,
        seasonNumber = item.seasonNumber ?: 0,
        seriesId = item.seriesId,
        seriesName = item.seriesName,
        poster = item.imageTags[ImageKind.Primary]?.let { TitleImage(item.id, ImageKind.Primary, it) },
        overview = detail.overview?.takeIf { it.isNotBlank() },
        play = episodes?.let(::seasonPlay),
        watched = item.userData.played,
        episodes =
            when {
                failure != null -> failure
                episodes == null -> EpisodesState.Loading
                else -> EpisodesState.Ready(episodes.map(::episodeUi))
            },
    )
}

/**
 * What the season's button does: resume the episode the user stopped part way through, else play the first not watched, else
 * nothing to continue with. The episodes are in the server's order.
 */
fun seasonPlay(episodes: List<BrowseItem>): SeriesPlay {
    val target =
        episodes.firstOrNull { !it.userData.played && it.userData.positionTicks > 0 }
            ?: episodes.firstOrNull { !it.userData.played }
    return when {
        episodes.isEmpty() -> SeriesPlay.NoEpisodes
        target == null ->
            episodes.first().let { first ->
                SeriesPlay.AllWatched(SeriesPlay.Episode(PlayKind.Play, first.id, first.seasonNumber, first.episodeNumber, null))
            }
        else -> {
            val action = playAction(target)
            SeriesPlay.Episode(action.kind, target.id, target.seasonNumber, target.episodeNumber, action.minutesLeft)
        }
    }
}
