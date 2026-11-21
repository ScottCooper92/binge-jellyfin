package com.cooper.homelab.ephyra.browse

/** What a person did on a title, from the server's `PersonKind`. A kind this app does not show is [Other]. */
enum class PersonKind {
    Actor,
    GuestStar,
    Director,
    Writer,
    Producer,
    Composer,
    Creator,
    Other,
    ;

    companion object {
        fun fromServer(type: String?): PersonKind = entries.firstOrNull { it != Other && it.name.equals(type, ignoreCase = true) } ?: Other
    }
}

/** One person in a title's cast or crew. [id] is the person's item id: a card and the person read use it, never the name. */
data class TitlePerson(
    val id: String,
    val name: String,
    /** The character, for an actor. */
    val role: String?,
    val kind: PersonKind,
    val imageTag: String?,
)

enum class TrackKind {
    Video,
    Audio,
    Subtitle,
    Other,
    ;

    companion object {
        fun fromServer(type: String?): TrackKind = entries.firstOrNull { it != Other && it.name.equals(type, ignoreCase = true) } ?: Other
    }
}

/**
 * One stream of a file, with what a track picker and a facts row show. The fields that mean something only to one kind
 * (channels for audio, size and range for video) are null on the others.
 */
data class MediaTrack(
    /** The stream's index in the file: what a playback request names to choose it. */
    val index: Int,
    val kind: TrackKind,
    val codec: String? = null,
    /** The three-letter code, when the file has one. */
    val language: String? = null,
    val title: String? = null,
    /** What the server would show for it, such as "English - Dolby Digital - 5.1 - Default". */
    val displayTitle: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    /** A subtitle file beside the video, not inside it. */
    val isExternal: Boolean = false,
    val isHearingImpaired: Boolean = false,
    val channels: Int? = null,
    val channelLayout: String? = null,
    val bitrate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** The server's `VideoRange` ("SDR", "HDR") and `VideoRangeType` ("HDR10", "DOVI", ...), as text: it grows new values. */
    val videoRange: String? = null,
    val videoRangeType: String? = null,
    val bitDepth: Int? = null,
    val frameRate: Double? = null,
    val profile: String? = null,
)

/**
 * One version of a title: a file the server can play. A title with several has several, and the page offers the choice.
 * Playing one is the playback code's job (`PlaybackSources`), by [id].
 */
data class MediaVersion(
    val id: String,
    val name: String?,
    val container: String?,
    val sizeBytes: Long?,
    val bitrate: Int?,
    val runTimeTicks: Long?,
    val tracks: List<MediaTrack>,
    val defaultAudioIndex: Int?,
    val defaultSubtitleIndex: Int?,
) {
    val video: MediaTrack? get() = tracks.firstOrNull { it.kind == TrackKind.Video }
    val audio: List<MediaTrack> get() = tracks.filter { it.kind == TrackKind.Audio }
    val subtitles: List<MediaTrack> get() = tracks.filter { it.kind == TrackKind.Subtitle }
}

data class TitleChapter(
    val startTicks: Long,
    val name: String?,
    val imageTag: String?,
)

/** Everything a movie, series, season or episode page shows. [item] is the card-level view of the same title. */
data class TitleDetail(
    val item: BrowseItem,
    val originalTitle: String?,
    val overview: String?,
    val taglines: List<String>,
    val genres: List<String>,
    val studios: List<String>,
    val criticRating: Double?,
    /** ISO 8601 as the server sends it. */
    val premiereDate: String?,
    val endDate: String?,
    /** A series' "Continuing" or "Ended". */
    val status: String?,
    /** External ids by provider ("Tmdb", "Imdb", ...). */
    val externalIds: Map<String, String>,
    val people: List<TitlePerson>,
    val versions: List<MediaVersion>,
    val chapters: List<TitleChapter>,
    val seasonName: String?,
    /** The first trailer address the server holds that is http or https, or null. */
    val trailerUrl: String? = null,
    /** The title's web pages (IMDb, TMDb first, then the server's own order), http and https only. */
    val links: List<TitleLink> = emptyList(),
    /** What the server tagged it with, trimmed and without repeats. */
    val tags: List<String> = emptyList(),
)

/** A web page about a title: [kind] says which site when it is one the page names, [name] is the server's label. */
data class TitleLink(
    val kind: TitleLinkKind,
    val name: String,
    val url: String,
)

enum class TitleLinkKind { Imdb, Tmdb, Other }

/** What to play next in a series, and why. The page chooses its button's label from which one it is. */
sealed interface NextEpisode {
    /** The episode to play, or null when there is none. */
    val episode: BrowseItem?

    /** No episode of the series is on the server. */
    data object None : NextEpisode {
        override val episode: BrowseItem? = null
    }

    /** Nothing has been watched: the first episode (not a special). */
    data class Start(
        override val episode: BrowseItem,
    ) : NextEpisode

    /** An episode was started and not finished. */
    data class Resume(
        override val episode: BrowseItem,
    ) : NextEpisode

    /** The first unwatched episode after the last watched one. */
    data class Next(
        override val episode: BrowseItem,
    ) : NextEpisode

    /** Every episode is watched, so none is next. [first] is the first episode (not a special), for watching the series again. */
    data class AllWatched(
        val first: BrowseItem,
    ) : NextEpisode {
        override val episode: BrowseItem? = null
    }
}

/** A person's own page: who they are. Their titles on this server are [PersonRepository.titlesWith]. */
data class PersonDetail(
    val id: String,
    val name: String,
    val overview: String?,
    /** ISO 8601 as the server sends it. */
    val birthDate: String?,
    val deathDate: String?,
    val birthPlace: String?,
    val imageTag: String?,
)
