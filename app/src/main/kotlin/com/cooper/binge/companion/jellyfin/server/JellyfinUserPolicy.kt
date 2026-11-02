package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

// A user's `UserPolicy`, as the server's own class names its fields (read at the release tags;
// docs/server-compatibility.md says which release added the gated ones). The page changes one key at a
// time and sends the whole policy back (`savePolicy`), so a key this app doesn't know goes back untouched,
// and a key the server didn't send is never shown or sent.

const val POLICY_REMOTE_ACCESS = "EnableRemoteAccess"
const val POLICY_HIDDEN = "IsHidden"
const val POLICY_PREFERENCE_ACCESS = "EnableUserPreferenceAccess"
const val POLICY_LOCKOUT = "LoginAttemptsBeforeLockout"
const val POLICY_MAX_SESSIONS = "MaxActiveSessions"
const val POLICY_PLAYBACK = "EnableMediaPlayback"
const val POLICY_AUDIO_TRANSCODING = "EnableAudioPlaybackTranscoding"
const val POLICY_VIDEO_TRANSCODING = "EnableVideoPlaybackTranscoding"
const val POLICY_REMUXING = "EnablePlaybackRemuxing"
const val POLICY_FORCE_REMOTE_TRANSCODING = "ForceRemoteSourceTranscoding"
const val POLICY_BITRATE_LIMIT = "RemoteClientBitrateLimit"
const val POLICY_DOWNLOADING = "EnableContentDownloading"
const val POLICY_DELETION = "EnableContentDeletion"
const val POLICY_DELETION_FOLDERS = "EnableContentDeletionFromFolders"
const val POLICY_CONVERSION = "EnableMediaConversion"
const val POLICY_PUBLIC_SHARING = "EnablePublicSharing"
const val POLICY_COLLECTIONS = "EnableCollectionManagement"
const val POLICY_SUBTITLES = "EnableSubtitleManagement"
const val POLICY_LYRICS = "EnableLyricManagement"
const val POLICY_REMOTE_CONTROL = "EnableRemoteControlOfOtherUsers"
const val POLICY_SHARED_DEVICE_CONTROL = "EnableSharedDeviceControl"
const val POLICY_SYNC_PLAY = "SyncPlayAccess"
const val POLICY_LIVE_TV_ACCESS = "EnableLiveTvAccess"
const val POLICY_LIVE_TV_MANAGEMENT = "EnableLiveTvManagement"
const val POLICY_ALL_CHANNELS = "EnableAllChannels"
const val POLICY_CHANNELS = "EnabledChannels"
const val POLICY_ALL_DEVICES = "EnableAllDevices"
const val POLICY_DEVICES = "EnabledDevices"
const val POLICY_MAX_RATING = "MaxParentalRating"
const val POLICY_MAX_SUB_RATING = "MaxParentalSubRating"
const val POLICY_BLOCK_UNRATED = "BlockUnratedItems"
const val POLICY_BLOCKED_TAGS = "BlockedTags"
const val POLICY_ALLOWED_TAGS = "AllowedTags"
const val POLICY_SCHEDULES = "AccessSchedules"

/** The kinds of item a user can be blocked from when they have no rating, as the server names them. */
val UNRATED_ITEM_TYPES =
    listOf("Movie", "Trailer", "Series", "Music", "Book", "LiveTvChannel", "LiveTvProgram", "ChannelContent", "Other")

/** What a user may do in SyncPlay (watching together), most first. */
val SYNC_PLAY_ACCESS = listOf("CreateAndJoinGroups", "JoinGroups", "None")

/** The days an access schedule can name, as the server does: one day, or a group of them. */
val SCHEDULE_DAYS =
    listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Everyday", "Weekday", "Weekend")

/** Whether the server sent [key] in this policy: a setting it doesn't have isn't offered. */
fun JsonObject.has(key: String): Boolean = key in this

/** [key]'s switch, off when it isn't there. */
fun JsonObject.flag(
    key: String,
    default: Boolean = false,
): Boolean = bool(key) ?: default

/** [key]'s list of ids or names. */
fun JsonObject.strings(key: String): List<String> = array(key).mapNotNull { (it as? JsonPrimitive)?.content }

/** [values] as the JSON array a policy holds. */
fun jsonStrings(values: List<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))

/** One parental rating the server knows, with the score it is ranked by; [subScore] is the 10.11 second one. */
data class ParentalRating(
    val name: String,
    val value: Int,
    val subScore: Int? = null,
)

/** The ratings a limit can be set to, as `/Localization/ParentalRatings` lists them. */
suspend fun JellyfinClient.parentalRatings(): List<ParentalRating> {
    val answer = get(PARENTAL_RATINGS) as? JsonArray ?: throw JellyfinUnreadableAnswer(PARENTAL_RATINGS)
    return answer.mapNotNull { element ->
        val json = element as? JsonObject ?: return@mapNotNull null
        val score = json.obj("RatingScore")
        val value = json.int("Value") ?: score?.int("score") ?: return@mapNotNull null
        val name = json.string("Name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        ParentalRating(name, value, score?.int("subScore"))
    }
}

/** A live TV channel, for the channels a user may watch. */
data class LiveChannel(
    val id: String,
    val name: String,
)

suspend fun JellyfinClient.liveTvChannels(): List<LiveChannel> {
    val answer = getObject(LIVE_TV_CHANNELS)
    return answer.array("Items").mapNotNull { element ->
        val json = element as? JsonObject ?: return@mapNotNull null
        LiveChannel(json.string("Id") ?: return@mapNotNull null, json.string("Name").orEmpty())
    }
}

/** A day and hours a user may use the server: `Everyday`, `Weekday` and `Weekend` stand for several days. */
data class AccessSchedule(
    val day: String,
    val startHour: Double,
    val endHour: Double,
)

fun JsonObject.accessSchedules(): List<AccessSchedule> = array(POLICY_SCHEDULES).mapNotNull { it.asAccessSchedule() }

/** [this] as a schedule, or null if it isn't one. */
fun JsonElement.asAccessSchedule(): AccessSchedule? {
    val json = this as? JsonObject ?: return null
    return AccessSchedule(
        json.string("DayOfWeek") ?: return null,
        (json["StartHour"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        (json["EndHour"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
    )
}

/** [schedule] as an entry the server takes: it sets its own id and the user. */
fun AccessSchedule.json(): JsonObject =
    buildJsonObject {
        put("DayOfWeek", day)
        put("StartHour", startHour)
        put("EndHour", endHour)
    }

private const val PARENTAL_RATINGS = "Localization/ParentalRatings"
private const val LIVE_TV_CHANNELS = "LiveTv/Channels"
