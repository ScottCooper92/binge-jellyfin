package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.MediaTrack
import com.cooper.homelab.ephyra.browse.MediaVersion
import com.cooper.homelab.ephyra.browse.PersonKind
import com.cooper.homelab.ephyra.browse.TitleDetail
import com.cooper.homelab.ephyra.browse.TitlePerson
import com.cooper.homelab.ephyra.browse.TrackKind
import com.cooper.homelab.ephyra.server.TICKS_PER_MINUTE
import kotlin.math.roundToInt

private const val UHD_WIDTH = 3_000
private const val FULL_HD_WIDTH = 1_800
private const val HD_WIDTH = 1_200
private const val SDR = "SDR"

/** The movie page's state from what the server answered for the title, its similar titles and its collection. */
fun movieUi(
    detail: TitleDetail,
    similar: List<BrowseItem>,
    collection: CollectionRow?,
): MovieUi {
    val item = detail.item
    return MovieUi(
        id = item.id,
        title = item.name,
        tagline = detail.taglines.firstOrNull { it.isNotBlank() },
        genres = detail.genres,
        backdrop = item.imageTags[ImageKind.Backdrop]?.let { TitleImage(item.id, ImageKind.Backdrop, it) },
        poster = item.imageTags[ImageKind.Primary]?.let { TitleImage(item.id, ImageKind.Primary, it) },
        stats =
            MovieStats(
                rating = item.communityRating,
                year = item.year,
                runtimeMinutes = item.runTimeTicks?.let(::runtimeMinutes),
                certification = item.officialRating?.takeIf { it.isNotBlank() },
            ),
        play = playAction(item),
        watched = item.userData.played,
        favourite = item.userData.favourite,
        overview = detail.overview?.takeIf { it.isNotBlank() },
        facts =
            TitleFacts(
                detail.originalTitle,
                detail.studios,
                detail.premiereDate,
                directors = detail.people.filter { it.kind == PersonKind.Director }.map { it.name },
            ),
        versions = detail.versions.map(::versionUi),
        cast = detail.people.filter { it.kind == PersonKind.Actor || it.kind == PersonKind.GuestStar }.map(::personUi),
        crew = detail.people.filter { it.kind in CREW_KINDS }.map(::personUi),
        collection = collection,
        similar = similar.map(::titleCard),
        criticsPercent = detail.criticRating?.roundToInt(),
        trailerUrl = detail.trailerUrl,
        shareUrl = detail.shareUrl(series = false),
        tags = detail.tags,
        links = detail.links,
    )
}

private val CREW_KINDS = setOf(PersonKind.Director, PersonKind.Writer, PersonKind.Producer, PersonKind.Composer)

private fun minutes(ticks: Long): Int = (ticks / TICKS_PER_MINUTE).toInt()

/** Resume where the user stopped, with what is left; otherwise play from the start. A watched film plays again from the start. */
fun playAction(item: BrowseItem): PlayAction {
    val position = item.userData.positionTicks
    val length = item.runTimeTicks
    return if (!item.userData.played && position > 0) {
        PlayAction(PlayKind.Resume, length?.let { minutes((it - position).coerceAtLeast(0L)) })
    } else {
        PlayAction(PlayKind.Play, null)
    }
}

/** A person as a card: the actor's character from the server, and for a crew member the job as a [CrewJob] for the screen to word. */
fun personUi(person: TitlePerson): PersonUi =
    PersonUi(
        id = person.id,
        name = person.name,
        subtitle = person.role,
        photo = person.imageTag?.let { TitleImage(person.id, ImageKind.Primary, it) },
        job = person.kind.crewJob(),
    )

private fun PersonKind.crewJob(): CrewJob? =
    when (this) {
        PersonKind.Director -> CrewJob.Director
        PersonKind.Writer -> CrewJob.Writer
        PersonKind.Producer -> CrewJob.Producer
        PersonKind.Composer -> CrewJob.Composer
        PersonKind.Actor, PersonKind.GuestStar, PersonKind.Creator, PersonKind.Other -> null
    }

/** A poster card for a similar title or a collection's part. */
fun titleCard(item: BrowseItem): TitleCard =
    TitleCard(
        id = item.id,
        kind = item.kind,
        title = item.name,
        year = item.year,
        rating = item.communityRating,
        poster = item.imageTags[ImageKind.Primary]?.let { TitleImage(item.id, ImageKind.Primary, it) },
        played = item.userData.played,
    )

/** A file as the versions sheet lists it: what it is, and its audio and subtitle streams. */
fun versionUi(version: MediaVersion): VersionUi {
    val video = version.video
    return VersionUi(
        id = version.id,
        name = version.name?.takeIf { it.isNotBlank() },
        container = version.container?.takeIf { it.isNotBlank() },
        resolution = video?.let(::resolution),
        range = video?.videoRangeType?.takeIf { it.isNotBlank() && !it.equals(SDR, ignoreCase = true) },
        sizeBytes = version.sizeBytes,
        audio = version.audio.map(::trackUi),
        subtitles = version.subtitles.map(::trackUi),
        defaultAudioIndex = version.defaultAudioIndex,
        defaultSubtitleIndex = version.defaultSubtitleIndex,
    )
}

/** The video's resolution by its width, since a wide film is shorter than its name: a 1080p scope film is 1920 wide and 800 tall. */
private fun resolution(video: MediaTrack): Resolution? {
    val width = video.width ?: return null
    return when {
        width >= UHD_WIDTH -> Resolution.Uhd
        width >= FULL_HD_WIDTH -> Resolution.FullHd
        width >= HD_WIDTH -> Resolution.Hd
        else -> Resolution.Sd
    }
}

/** A stream: the server's own display title when it has one, else its language, codec and channel layout. */
fun trackUi(track: MediaTrack): TrackUi =
    TrackUi(
        index = track.index,
        kind = track.kind,
        label =
            track.displayTitle?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(
                    track.language?.takeIf { it.isNotBlank() }?.uppercase(),
                    track.codec?.takeIf { it.isNotBlank() }?.uppercase(),
                    track.channelLayout?.takeIf { it.isNotBlank() && track.kind == TrackKind.Audio },
                ).joinToString(" · ").ifBlank { null },
        isDefault = track.isDefault,
        isForced = track.isForced,
        isExternal = track.isExternal,
        isHearingImpaired = track.isHearingImpaired,
    )
