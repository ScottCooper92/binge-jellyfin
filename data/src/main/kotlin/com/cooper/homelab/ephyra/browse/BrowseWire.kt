package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinUnreadableAnswer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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
    id?.let { LibraryView(it, name.orEmpty(), LibraryKind.fromServer(collectionType), imageTags["Primary"]) }

internal fun ItemWire.toBrowseItem(): BrowseItem? {
    val id = id ?: return null
    val tags =
        buildMap {
            imageTags["Primary"]?.let { put(ImageKind.Primary, it) }
            imageTags["Thumb"]?.let { put(ImageKind.Thumb, it) }
            imageTags["Logo"]?.let { put(ImageKind.Logo, it) }
            backdropImageTags.firstOrNull()?.let { put(ImageKind.Backdrop, it) }
        }
    return BrowseItem(
        id = id,
        name = name.orEmpty(),
        kind = ItemKind.fromServer(type),
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
        seasonNumber = parentIndexNumber,
        episodeNumber = indexNumber,
        userData =
            ItemUserData(
                played = userData?.played ?: false,
                favourite = userData?.isFavorite ?: false,
                positionTicks = userData?.playbackPositionTicks ?: 0L,
                playedPercentage = userData?.playedPercentage,
                unplayedCount = userData?.unplayedItemCount,
            ),
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
