package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.TitleLink
import com.cooper.homelab.ephyra.browse.TrackKind

/** A picture to show, named by item, kind and tag. The screen turns it into a URL at the size it draws it. */
data class TitleImage(
    val itemId: String,
    val kind: ImageKind,
    val tag: String,
)

/** What the page's one wide button does. */
enum class PlayKind {
    Play,
    Resume,
}

/** The primary action. For a resume, [minutesLeft] is what remains of the film, when the server says how long it is. */
data class PlayAction(
    val kind: PlayKind,
    val minutesLeft: Int?,
)

/** The headline stats under the hero. A stat the server did not give is null and not drawn. */
data class MovieStats(
    val rating: Double?,
    val year: Int?,
    val runtimeMinutes: Int?,
    val certification: String?,
) {
    val isEmpty: Boolean get() = rating == null && year == null && runtimeMinutes == null && certification == null
}

/** The facts in the details grid. Each is null or empty when the server gave none, and then is not drawn. */
data class TitleFacts(
    val originalTitle: String?,
    val studios: List<String>,
    /** The premiere date as the server sends it (`yyyy-MM-dd…`); the screen words it in the user's locale. */
    val premiereDate: String?,
    val directors: List<String> = emptyList(),
    /** A series' creators. */
    val creators: List<String> = emptyList(),
    /** A series' "Continuing" or "Ended", as the server says it. */
    val status: String? = null,
    /** When a series ended, as the server sends it. */
    val endDate: String? = null,
)

/** What a crew member did. The screen words it from its own strings, so it reads in the user's language; the server sends no text for it. */
enum class CrewJob {
    Director,
    Writer,
    Producer,
    Composer,
}

/** A person in the cast or crew. */
data class PersonUi(
    val id: String,
    val name: String,
    /** The character an actor plays, when the server says. It is the server's own text. */
    val subtitle: String?,
    val photo: TitleImage?,
    /** What a crew member did, or null for an actor. */
    val job: CrewJob? = null,
)

/** One stream of a file, as the versions sheet lists it. [label] is the server's own description, else a short one made from the stream. */
data class TrackUi(
    val index: Int,
    val kind: TrackKind,
    val label: String?,
    val isDefault: Boolean,
    val isForced: Boolean,
    val isExternal: Boolean,
    val isHearingImpaired: Boolean,
)

/** A resolution, as a person names it. */
enum class Resolution(
    val label: String,
) {
    Uhd("4K"),
    FullHd("1080p"),
    Hd("720p"),
    Sd("SD"),
}

/** One file the server can play. */
data class VersionUi(
    val id: String,
    val name: String?,
    val container: String?,
    val resolution: Resolution?,
    /** The video's range when it is not plain SDR, as the server names it: `HDR10`, `DOVI`, `HLG`. */
    val range: String?,
    val sizeBytes: Long?,
    val audio: List<TrackUi>,
    val subtitles: List<TrackUi>,
    val defaultAudioIndex: Int?,
    val defaultSubtitleIndex: Int?,
)

/** A poster card of a row (similar titles, a collection's parts). */
data class TitleCard(
    val id: String,
    val kind: ItemKind,
    val title: String,
    val year: Int?,
    val rating: Double?,
    val poster: TitleImage?,
    val played: Boolean,
)

/** The collection this film belongs to, and its parts. */
data class CollectionRow(
    val id: String,
    val name: String,
    val parts: List<TitleCard>,
)

/** Everything the movie page shows. */
data class MovieUi(
    val id: String,
    val title: String,
    val tagline: String?,
    val genres: List<String>,
    val backdrop: TitleImage?,
    /** The poster, which the wide header shows beside the title. */
    val poster: TitleImage?,
    val stats: MovieStats,
    val play: PlayAction,
    val watched: Boolean,
    val favourite: Boolean,
    val overview: String?,
    val facts: TitleFacts,
    val versions: List<VersionUi>,
    val cast: List<PersonUi>,
    val crew: List<PersonUi>,
    val collection: CollectionRow?,
    val similar: List<TitleCard>,
    /** The critics' score out of 100, when the server holds one. */
    val criticsPercent: Int? = null,
    /** The first trailer address the server holds (http or https), or null. */
    val trailerUrl: String? = null,
    /** The title's public page to share, or null when the server holds no TMDb or IMDb id. */
    val shareUrl: String? = null,
    /** What the server tagged the title with. */
    val tags: List<String> = emptyList(),
    /** The title's pages on other sites, IMDb first, then TMDb, then the rest. */
    val links: List<TitleLink> = emptyList(),
)

/** What the movie page shows. */
sealed interface MovieUiState {
    /** The title has not answered yet. */
    data object Loading : MovieUiState

    data class Ready(
        val movie: MovieUi,
    ) : MovieUiState

    /** The title could not be read. [failure] is why, as the screen classifies it. */
    data class Failed(
        val failure: ServerFailure,
    ) : MovieUiState
}

/** A one-off thing the page tells the user, as a snackbar. */
sealed interface TitleEvent {
    /** A watched or favourite mark did not reach the server. */
    data class WriteFailed(
        val failure: ServerFailure,
    ) : TitleEvent
}
