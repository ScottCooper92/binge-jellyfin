package com.cooper.binge.companion.jellyfin.server

import com.binge.companion.sdk.attempt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A tuner host as the Live TV page lists it. [address] is where it is, without anything a link can carry in it: an M3U playlist's
 * address often has the provider's username and password in its path or query, so a web address is shortened to its scheme, host and
 * port. A file path is shown as it is.
 */
data class TunerHostSummary(
    val id: String,
    val name: String,
    /** The server's name for its kind: `hdhomerun`, `m3u`, or whatever a plugin adds. */
    val type: String,
    val address: String,
)

/** A guide data provider: XMLTV (a path or an address) or Schedules Direct (an account). [label] is what tells two of them apart. */
data class GuideProviderSummary(
    val id: String,
    val type: String,
    val label: String,
    /** A Schedules Direct account saved but with no lineup chosen yet: the setup was not finished, and the server's next guide refresh has nothing to read. */
    val lineupPending: Boolean = false,
)

/**
 * What the server's Live TV setup holds, read from `LiveTvOptions` (`/System/Configuration/livetv`) and `GuideInfo`. The providers'
 * `Password` is a secret and is never read into this: a later save sends the configuration back as the server sent it.
 */
data class LiveTvSummary(
    val tuners: List<TunerHostSummary> = emptyList(),
    val providers: List<GuideProviderSummary> = emptyList(),
    /** When the guide data the server holds ends, or null when it holds none or the user may not ask. */
    val guideEndMillis: Long? = null,
)

/** The server's Live TV configuration, whole: a later save sends back what it didn't change. Any signed-in user may read it. */
suspend fun JellyfinClient.liveTvConfiguration(): JsonObject = getObject(LIVE_TV_CONFIGURATION)

/**
 * Saves the Live TV configuration. The server takes administrators only. The whole object goes back, so the tuner hosts and guide
 * providers (and the password a provider holds) return as they came.
 */
suspend fun JellyfinClient.saveLiveTvConfiguration(configuration: JsonObject) {
    post(LIVE_TV_CONFIGURATION, configuration)
}

const val LIVE_TV_RECORDING_PATH = "RecordingPath"
const val LIVE_TV_MOVIE_PATH = "MovieRecordingPath"
const val LIVE_TV_SERIES_PATH = "SeriesRecordingPath"
const val LIVE_TV_PRE_PADDING = "PrePaddingSeconds"
const val LIVE_TV_POST_PADDING = "PostPaddingSeconds"
const val LIVE_TV_SUBFOLDERS = "EnableRecordingSubfolders"
const val LIVE_TV_ORIGINAL_AUDIO = "EnableOriginalAudioWithEncodedRecordings"
const val LIVE_TV_SAVE_NFO = "SaveRecordingNFO"
const val LIVE_TV_SAVE_IMAGES = "SaveRecordingImages"
const val LIVE_TV_GUIDE_DAYS = "GuideDays"

/**
 * The Live TV setup, with when the guide ends where the user has Live TV access (the server refuses `GuideInfo` to anyone else, and
 * a guide read that fails leaves the rest of the page standing).
 */
suspend fun JellyfinClient.liveTvSummary(withGuide: Boolean): LiveTvSummary = liveTvRead(withGuide).summary

/** What a read of the Live TV setup holds: the [summary] the page shows, and the tuner [hosts] as the server holds them, by `Id`, for an edit. */
data class LiveTvRead(
    val summary: LiveTvSummary,
    val hosts: Map<String, JsonObject>,
)

suspend fun JellyfinClient.liveTvRead(withGuide: Boolean): LiveTvRead {
    val configuration = liveTvConfiguration()
    val summary = configuration.liveTvSummary()
    val end = if (withGuide) attempt { getObject(GUIDE_INFO) }.getOrNull()?.string("EndDate")?.epochMillisOrNull() else null
    return LiveTvRead(summary.copy(guideEndMillis = end), configuration.tunerHosts())
}

/** The tuner hosts as the server holds them, by `Id`: what an edit sends back with its changes. Kept apart from [LiveTvSummary], which holds nothing secret. */
internal fun JsonObject.tunerHosts(): Map<String, JsonObject> =
    array("TunerHosts").objects().mapNotNull { host -> host.string("Id")?.let { it to host } }.toMap()

internal fun JsonObject.liveTvSummary(): LiveTvSummary =
    LiveTvSummary(
        tuners = array("TunerHosts").objects().mapNotNull { it.tunerHost() },
        providers = array("ListingProviders").objects().mapNotNull { it.guideProvider() },
    )

private fun JsonObject.tunerHost(): TunerHostSummary? {
    val id = string("Id") ?: return null
    val url = string("Url").orEmpty()
    return TunerHostSummary(
        id = id,
        name = string("FriendlyName")?.takeIf { it.isNotBlank() } ?: url.withoutSecrets(),
        type = string("Type").orEmpty(),
        address = url.withoutSecrets(),
    )
}

private fun JsonObject.guideProvider(): GuideProviderSummary? {
    val id = string("Id") ?: return null
    val type = string("Type").orEmpty()
    val label = string("Username")?.takeIf { it.isNotBlank() } ?: string("Path").orEmpty().withoutSecrets()
    val pending = type.equals(GUIDE_TYPE_SCHEDULES_DIRECT, ignoreCase = true) && string("ListingsId").isNullOrBlank()
    return GuideProviderSummary(id, type, label, pending)
}

/** [this] as a web address shortened to scheme, host and port, or as it is when it is a file path or not an address at all. */
internal fun String.withoutSecrets(): String {
    val url = toHttpUrlOrNull() ?: return this
    val port = if (url.port == defaultPort(url.scheme)) "" else ":${url.port}"
    return "${url.scheme}://${url.host}$port"
}

private fun defaultPort(scheme: String): Int = if (scheme == "https") HTTPS_PORT else HTTP_PORT

private fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }

private const val LIVE_TV_CONFIGURATION = "System/Configuration/livetv"
private const val GUIDE_INFO = "LiveTv/GuideInfo"
private const val HTTP_PORT = 80
private const val HTTPS_PORT = 443
