package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.NextEpisode
import com.cooper.homelab.ephyra.browse.PersonKind
import com.cooper.homelab.ephyra.browse.TitleDetail
import kotlin.math.roundToInt

private const val PERCENT = 100f

/** The series page from what the server answered. [selectedSeasonId] and [episodes] are the season shown and its episodes so far. */
fun seriesUi(
    detail: TitleDetail,
    seasons: List<BrowseItem>,
    next: NextEpisode?,
    similar: List<BrowseItem>,
    selectedSeasonId: String?,
    episodes: EpisodesState,
): SeriesUi {
    val item = detail.item
    val seasonRows = seasons.map(::seasonUi)
    return SeriesUi(
        id = item.id,
        title = item.name,
        tagline = detail.taglines.firstOrNull { it.isNotBlank() },
        genres = detail.genres,
        backdrop = item.imageTags[ImageKind.Backdrop]?.let { TitleImage(item.id, ImageKind.Backdrop, it) },
        poster = item.imageTags[ImageKind.Primary]?.let { TitleImage(item.id, ImageKind.Primary, it) },
        stats =
            SeriesStats(
                rating = item.communityRating,
                year = item.year,
                seasonCount = seasonRows.count { !it.isSpecials },
                certification = item.officialRating?.takeIf { it.isNotBlank() },
                episodeCount =
                    seasonRows
                        .filter { !it.isSpecials }
                        .mapNotNull { it.episodeCount }
                        .takeIf { it.isNotEmpty() }
                        ?.sum(),
            ),
        play = seriesPlay(next),
        watched = item.userData.played,
        favourite = item.userData.favourite,
        overview = detail.overview?.takeIf { it.isNotBlank() },
        facts =
            TitleFacts(
                detail.originalTitle,
                detail.studios,
                detail.premiereDate,
                creators = detail.people.filter { it.kind == PersonKind.Creator }.map { it.name },
                status = detail.status?.takeIf { it.isNotBlank() },
                endDate = detail.endDate.takeIf { detail.status.equals("Ended", ignoreCase = true) },
            ),
        cast = detail.people.filter { it.kind == PersonKind.Actor || it.kind == PersonKind.GuestStar }.map(::personUi),
        crew = detail.people.filter { it.kind in CREW_KINDS }.map(::personUi),
        seasons = seasonRows,
        selectedSeasonId = selectedSeasonId,
        episodes = episodes,
        similar = similar.map(::titleCard),
        criticsPercent = detail.criticRating?.roundToInt(),
        trailerUrl = detail.trailerUrl,
        shareUrl = detail.shareUrl(series = true),
        tags = detail.tags,
        links = detail.links,
    )
}

private val CREW_KINDS = setOf(PersonKind.Director, PersonKind.Writer, PersonKind.Producer, PersonKind.Composer)

/** What the one wide button does, from what [next] says to play. Null when [next] is not known yet, which is not "no episodes". */
fun seriesPlay(next: NextEpisode?): SeriesPlay? =
    when (next) {
        null -> null
        NextEpisode.None -> SeriesPlay.NoEpisodes
        is NextEpisode.AllWatched -> SeriesPlay.AllWatched(episodePlay(PlayKind.Play, next.first))
        is NextEpisode.Start -> episodePlay(PlayKind.Play, next.episode)
        is NextEpisode.Next -> episodePlay(PlayKind.Play, next.episode)
        is NextEpisode.Resume -> episodePlay(PlayKind.Resume, next.episode)
    }

private fun episodePlay(
    kind: PlayKind,
    episode: BrowseItem,
): SeriesPlay.Episode =
    SeriesPlay.Episode(
        kind = kind,
        episodeId = episode.id,
        season = episode.seasonNumber,
        episode = episode.episodeNumber,
        minutesLeft = if (kind == PlayKind.Resume) playAction(episode).minutesLeft else null,
        name = episode.name,
    )

/** A season as a row: its own number (0 for specials), name, how many episodes it has and how many are unwatched. */
fun seasonUi(season: BrowseItem): SeasonUi =
    SeasonUi(
        id = season.id,
        number = season.seasonNumber ?: 0,
        name = season.name,
        episodeCount = season.childCount,
        unwatched = season.userData.unplayedCount,
        watched = season.userData.played,
        poster = season.imageTags[ImageKind.Primary]?.let { TitleImage(season.id, ImageKind.Primary, it) },
    )

/** An episode as a row. It shows its own still, else its series' backdrop; progress is how far through it the user is. */
fun episodeUi(episode: BrowseItem): EpisodeUi {
    val still =
        episode.imageTags[ImageKind.Primary]?.let { TitleImage(episode.id, ImageKind.Primary, it) }
            ?: episode.parentBackdropItemId?.let { parent ->
                episode.parentBackdropImageTag?.let { TitleImage(parent, ImageKind.Backdrop, it) }
            }
    return EpisodeUi(
        id = episode.id,
        season = episode.seasonNumber,
        number = episode.episodeNumber,
        title = episode.name,
        overview = episode.overview?.takeIf { it.isNotBlank() },
        still = still,
        runtimeMinutes = episode.runTimeTicks?.let(::runtimeMinutes),
        watched = episode.userData.played,
        progress = episodeProgress(episode),
    )
}

private fun episodeProgress(episode: BrowseItem): Float? {
    if (episode.userData.played) return null
    val fraction =
        episode.userData.playedPercentage?.let { (it / PERCENT).toFloat() }
            ?: episode.runTimeTicks?.takeIf { it > 0 }?.let { episode.userData.positionTicks.toFloat() / it }
    return fraction?.coerceIn(0f, 1f)?.takeIf { it > 0f }
}

/**
 * The season to show first: the one holding the episode to play, else the first season that is not specials, else the first there is.
 * The page may put specials last; this does not sort.
 */
fun initialSeason(
    seasons: List<BrowseItem>,
    next: NextEpisode?,
): String? {
    val playing = next?.episode?.seasonId
    return seasons.firstOrNull { it.id == playing }?.id
        ?: seasons.firstOrNull { (it.seasonNumber ?: 0) != 0 }?.id
        ?: seasons.firstOrNull()?.id
}
