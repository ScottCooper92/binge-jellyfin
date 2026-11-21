package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinUnreadableAnswer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// What the server sends, as the OpenAPI document names it, and nothing else. The public models in BrowseModels.kt are
// what the app reads; these map to them. A field the server adds is ignored, one it leaves out is null, and an enum
// the server grows is carried as text and read as `Other`, so none of them fails a page.

/** The one decoder for every browse read. */
internal val BrowseJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

/** Decodes [element] as [strategy], or throws [JellyfinUnreadableAnswer] for [path] when the shape is wrong. */
internal fun <T> decodeAnswer(
    strategy: DeserializationStrategy<T>,
    element: JsonElement,
    path: String,
): T =
    try {
        BrowseJson.decodeFromJsonElement(strategy, element)
    } catch (_: SerializationException) {
        throw JellyfinUnreadableAnswer(path)
    } catch (_: IllegalArgumentException) {
        throw JellyfinUnreadableAnswer(path)
    }

@Serializable
internal data class ViewsWire(
    @SerialName("Items") val items: List<ViewWire> = emptyList(),
)

@Serializable
internal data class ViewWire(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
)

@Serializable
internal data class ItemsWire(
    @SerialName("Items") val items: List<ItemWire> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int? = null,
)

@Serializable
internal data class ItemWire(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("SortName") val sortName: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropImageTags: List<String> = emptyList(),
    @SerialName("ParentThumbItemId") val parentThumbItemId: String? = null,
    @SerialName("ParentThumbImageTag") val parentThumbImageTag: String? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("UserData") val userData: UserDataWire? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    // What only a title page reads (the item route sends all of it; a list read asks for none of it).
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    @SerialName("Taglines") val taglines: List<String> = emptyList(),
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("Studios") val studios: List<NameWire> = emptyList(),
    @SerialName("People") val people: List<PersonWire> = emptyList(),
    @SerialName("ProviderIds") val providerIds: Map<String, String?> = emptyMap(),
    @SerialName("MediaSources") val mediaSources: List<SourceWire> = emptyList(),
    @SerialName("Chapters") val chapters: List<ChapterWire> = emptyList(),
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("CriticRating") val criticRating: Double? = null,
    @SerialName("SeasonName") val seasonName: String? = null,
    @SerialName("ProductionLocations") val productionLocations: List<String> = emptyList(),
    @SerialName("RemoteTrailers") val remoteTrailers: List<UrlWire> = emptyList(),
    @SerialName("ExternalUrls") val externalUrls: List<UrlWire> = emptyList(),
    @SerialName("Tags") val tags: List<String> = emptyList(),
)

/** A trailer (`RemoteTrailers`) or an external link (`ExternalUrls`): the two arrays share one shape. */
@Serializable
internal data class UrlWire(
    @SerialName("Name") val name: String? = null,
    @SerialName("Url") val url: String? = null,
)

@Serializable
internal data class NameWire(
    @SerialName("Name") val name: String? = null,
)

@Serializable
internal data class PersonWire(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Role") val role: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

@Serializable
internal data class SourceWire(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Bitrate") val bitrate: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("DefaultAudioStreamIndex") val defaultAudioStreamIndex: Int? = null,
    @SerialName("DefaultSubtitleStreamIndex") val defaultSubtitleStreamIndex: Int? = null,
    @SerialName("MediaStreams") val mediaStreams: List<StreamWire> = emptyList(),
)

@Serializable
internal data class StreamWire(
    @SerialName("Index") val index: Int? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("IsDefault") val isDefault: Boolean? = null,
    @SerialName("IsForced") val isForced: Boolean? = null,
    @SerialName("IsExternal") val isExternal: Boolean? = null,
    @SerialName("IsHearingImpaired") val isHearingImpaired: Boolean? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("ChannelLayout") val channelLayout: String? = null,
    @SerialName("BitRate") val bitRate: Int? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("VideoRange") val videoRange: String? = null,
    @SerialName("VideoRangeType") val videoRangeType: String? = null,
    @SerialName("BitDepth") val bitDepth: Int? = null,
    @SerialName("AverageFrameRate") val averageFrameRate: Double? = null,
    @SerialName("Profile") val profile: String? = null,
)

@Serializable
internal data class ChapterWire(
    @SerialName("StartPositionTicks") val startPositionTicks: Long? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("ImageTag") val imageTag: String? = null,
)

@Serializable
internal data class UserDataWire(
    @SerialName("Played") val played: Boolean? = null,
    @SerialName("IsFavorite") val isFavorite: Boolean? = null,
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long? = null,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("UnplayedItemCount") val unplayedItemCount: Int? = null,
)

@Serializable
internal data class FiltersWire(
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("Years") val years: List<Int> = emptyList(),
    @SerialName("OfficialRatings") val officialRatings: List<String> = emptyList(),
)

internal fun ViewWire.toLibraryView(): LibraryView? =
    id?.let {
        LibraryView(
            id = it,
            name = name.orEmpty(),
            kind = LibraryKind.fromServer(collectionType),
            primaryImageTag = imageTags["Primary"],
            thumbImageTag = imageTags["Thumb"],
            backdropImageTag = backdropImageTags.firstOrNull(),
        )
    }

internal fun ItemWire.toBrowseItem(): BrowseItem? {
    val id = id ?: return null
    val tags =
        buildMap {
            imageTags["Primary"]?.let { put(ImageKind.Primary, it) }
            imageTags["Thumb"]?.let { put(ImageKind.Thumb, it) }
            imageTags["Logo"]?.let { put(ImageKind.Logo, it) }
            backdropImageTags.firstOrNull()?.let { put(ImageKind.Backdrop, it) }
        }
    val kind = ItemKind.fromServer(type)
    return BrowseItem(
        id = id,
        name = name.orEmpty(),
        kind = kind,
        year = productionYear,
        communityRating = communityRating,
        officialRating = officialRating,
        runTimeTicks = runTimeTicks,
        imageTags = tags,
        primaryAspectRatio = primaryImageAspectRatio,
        seriesId = seriesId,
        seriesName = seriesName,
        seriesPrimaryImageTag = seriesPrimaryImageTag,
        parentBackdropItemId = parentBackdropItemId,
        parentBackdropImageTag = parentBackdropImageTags.firstOrNull(),
        parentThumbItemId = parentThumbItemId,
        parentThumbImageTag = parentThumbImageTag,
        // A season is numbered by its own index; an episode by its parent's (the season) and its own.
        seasonNumber = if (kind == ItemKind.Season) indexNumber else parentIndexNumber,
        episodeNumber = indexNumber.takeIf { kind != ItemKind.Season },
        seasonId = seasonId,
        overview = overview,
        childCount = childCount,
        userData =
            ItemUserData(
                played = userData?.played ?: false,
                favourite = userData?.isFavorite ?: false,
                positionTicks = userData?.playbackPositionTicks ?: 0L,
                playedPercentage = userData?.playedPercentage,
                unplayedCount = userData?.unplayedItemCount,
            ),
        sortName = sortName,
    )
}

internal fun ItemsWire.toPage(
    startIndex: Int,
    withTotal: Boolean,
): Page<BrowseItem> =
    Page(
        items = items.mapNotNull { it.toBrowseItem() },
        total = if (withTotal) totalRecordCount else null,
        startIndex = startIndex,
    )

internal fun ItemWire.toTitleDetail(): TitleDetail? {
    val item = toBrowseItem() ?: return null
    return TitleDetail(
        item = item,
        originalTitle = originalTitle?.takeIf { it != name },
        overview = overview,
        taglines = taglines,
        genres = genres,
        studios = studios.mapNotNull { it.name },
        criticRating = criticRating,
        premiereDate = premiereDate,
        endDate = endDate,
        status = status,
        externalIds = providerIds.mapNotNull { (key, value) -> value?.let { key to it } }.toMap(),
        people =
            people.mapNotNull { person ->
                val id = person.id ?: return@mapNotNull null
                TitlePerson(
                    id,
                    person.name.orEmpty(),
                    person.role?.takeIf(String::isNotBlank),
                    PersonKind.fromServer(person.type),
                    person.primaryImageTag,
                )
            },
        versions = mediaSources.mapNotNull { it.toVersion() },
        chapters = chapters.map { TitleChapter(it.startPositionTicks ?: 0L, it.name?.takeIf(String::isNotBlank), it.imageTag) },
        seasonName = seasonName,
        trailerUrl = remoteTrailers.firstNotNullOfOrNull { it.url.webAddress() },
        links = externalUrls.toLinks(),
        tags = tags.map(String::trim).filter(String::isNotEmpty).distinct(),
    )
}

private fun String?.webAddress(): String? = this?.trim()?.takeIf { it.toHttpUrlOrNull() != null }

private fun List<UrlWire>.toLinks(): List<TitleLink> {
    val seen = HashSet<String>()
    val links =
        mapNotNull { wire ->
            val url = wire.url.webAddress() ?: return@mapNotNull null
            if (!seen.add(url)) return@mapNotNull null
            val host =
                url
                    .toHttpUrlOrNull()
                    ?.host
                    .orEmpty()
                    .removePrefix("www.")
            val kind =
                when {
                    host == "imdb.com" || host.endsWith(".imdb.com") -> TitleLinkKind.Imdb
                    host == "themoviedb.org" || host.endsWith(".themoviedb.org") -> TitleLinkKind.Tmdb
                    else -> TitleLinkKind.Other
                }
            TitleLink(kind, wire.name?.trim()?.takeIf(String::isNotEmpty) ?: host, url)
        }
    return links.sortedBy { it.kind.ordinal }
}

private fun SourceWire.toVersion(): MediaVersion? {
    val id = id ?: return null
    return MediaVersion(
        id = id,
        name = name,
        container = container,
        sizeBytes = size,
        bitrate = bitrate,
        runTimeTicks = runTimeTicks,
        tracks = mediaStreams.mapNotNull { it.toTrack() },
        defaultAudioIndex = defaultAudioStreamIndex,
        defaultSubtitleIndex = defaultSubtitleStreamIndex,
    )
}

private fun StreamWire.toTrack(): MediaTrack? {
    val index = index ?: return null
    return MediaTrack(
        index = index,
        kind = TrackKind.fromServer(type),
        codec = codec,
        language = language,
        title = title,
        displayTitle = displayTitle,
        isDefault = isDefault ?: false,
        isForced = isForced ?: false,
        isExternal = isExternal ?: false,
        isHearingImpaired = isHearingImpaired ?: false,
        channels = channels,
        channelLayout = channelLayout,
        bitrate = bitRate,
        width = width,
        height = height,
        videoRange = videoRange,
        videoRangeType = videoRangeType,
        bitDepth = bitDepth,
        frameRate = averageFrameRate,
        profile = profile,
    )
}

internal fun ItemWire.toPersonDetail(): PersonDetail? {
    val id = id ?: return null
    return PersonDetail(
        id = id,
        name = name.orEmpty(),
        overview = overview,
        birthDate = premiereDate,
        deathDate = endDate,
        birthPlace = productionLocations.firstOrNull(),
        imageTag = imageTags["Primary"],
    )
}
