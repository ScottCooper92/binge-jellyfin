package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.server.TUNER_COUNT
import com.cooper.binge.companion.jellyfin.server.TUNER_FALLBACK_BITRATE
import com.cooper.binge.companion.jellyfin.server.TUNER_FAVORITES_ONLY
import com.cooper.binge.companion.jellyfin.server.TUNER_HW_TRANSCODING
import com.cooper.binge.companion.jellyfin.server.TUNER_NAME
import com.cooper.binge.companion.jellyfin.server.TUNER_STREAM_OPTIONS
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_HDHOMERUN
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_M3U
import com.cooper.binge.companion.jellyfin.server.TUNER_URL
import com.cooper.binge.companion.jellyfin.server.TUNER_USER_AGENT
import com.cooper.binge.companion.jellyfin.server.bool
import com.cooper.binge.companion.jellyfin.server.int
import com.cooper.binge.companion.jellyfin.server.long
import com.cooper.binge.companion.jellyfin.server.string
import com.cooper.binge.companion.jellyfin.server.with
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode

private const val MAX_STREAM_LIMIT = 99

/**
 * A tuner host as its form shows it. [id] is the host's own, or null for one not added yet. [url] may carry an account (an
 * M3U playlist's address often does), so it is left out of [toString] and no log or state dump holds it.
 */
data class TunerDraft(
    val type: String,
    val id: String? = null,
    val name: String = "",
    val url: String = "",
    val userAgent: String = "",
    val streamLimit: String = "",
    val favoritesOnly: Boolean = false,
    val hardwareTranscoding: Boolean = true,
    /** The stream options an M3U host has, by key, as the server sent them: a key it did not send is not here, and is not shown. */
    val options: Map<String, Boolean> = emptyMap(),
    /** The fallback streaming bitrate in Mbps as typed, or null when the server did not send the key (before 10.10). */
    val fallbackMbps: String? = null,
) {
    val isHdHomeRun: Boolean get() = type.equals(TUNER_TYPE_HDHOMERUN, ignoreCase = true)
    val isM3u: Boolean get() = type.equals(TUNER_TYPE_M3U, ignoreCase = true)

    /** The stream limit as the server stores it (0 is no limit), or null when what was typed is not a whole number in range. */
    val streamLimitValue: Int? get() =
        if (streamLimit.isBlank()) {
            0
        } else {
            streamLimit.trim().toIntOrNull()?.takeIf {
                it in
                    0..MAX_STREAM_LIMIT
            }
        }

    /** Whether the form can be submitted: an address, and a stream limit that reads. */
    val valid: Boolean get() = url.isNotBlank() && (!isM3u || streamLimitValue != null) && !fallbackInvalid

    /**
     * The fallback bitrate as the server stores it (bits a second, an Int there), or null when it is not offered or what was typed
     * is not a positive number that fits. Decimal arithmetic, so a figure like 8.2 is 8,200,000 and not 8,199,999.
     */
    val fallbackBits: Long? get() =
        fallbackMbps
            ?.trim()
            ?.replace(',', '.')
            ?.toBigDecimalOrNull()
            ?.movePointRight(MEGA_DIGITS)
            ?.setScale(0, RoundingMode.HALF_UP)
            ?.takeIf { it.signum() > 0 && it <= BigDecimal(Int.MAX_VALUE) }
            ?.toLong()

    /** Whether the fallback bitrate is offered and what was typed is not a positive number. */
    val fallbackInvalid: Boolean get() = fallbackMbps != null && fallbackBits == null

    override fun toString(): String = "TunerDraft(type=$type, id=$id)"

    /** [host] (or a new host) with this draft's changes: any key the form does not show stays as the server sent it. */
    fun applyTo(host: JsonObject?): JsonObject {
        var out = (host ?: JsonObject(emptyMap())).with(TUNER_TYPE, JsonPrimitive(type)).with(TUNER_URL, JsonPrimitive(url.trim()))
        if (isM3u) {
            out = out.with(TUNER_NAME, JsonPrimitive(name.trim())).with(TUNER_USER_AGENT, JsonPrimitive(userAgent.trim()))
            out = out.with(TUNER_COUNT, JsonPrimitive(streamLimitValue ?: 0))
        }
        if (isHdHomeRun) out = out.with(TUNER_FAVORITES_ONLY, JsonPrimitive(favoritesOnly))
        if (isM3u) options.forEach { (key, on) -> out = out.with(key, JsonPrimitive(on)) }
        fallbackBits?.let { out = out.with(TUNER_FALLBACK_BITRATE, JsonPrimitive(it)) }
        return out.with(TUNER_HW_TRANSCODING, JsonPrimitive(hardwareTranscoding))
    }

    companion object {
        /** The form for [host], as the server holds it. */
        fun of(host: JsonObject): TunerDraft =
            TunerDraft(
                type = host.string(TUNER_TYPE).orEmpty(),
                id = host.string("Id"),
                name = host.string(TUNER_NAME).orEmpty(),
                url = host.string(TUNER_URL).orEmpty(),
                userAgent = host.string(TUNER_USER_AGENT).orEmpty(),
                streamLimit =
                    host
                        .int(TUNER_COUNT)
                        ?.takeIf { it != 0 }
                        ?.toString()
                        .orEmpty(),
                favoritesOnly = host.bool(TUNER_FAVORITES_ONLY) ?: false,
                hardwareTranscoding = host.bool(TUNER_HW_TRANSCODING) ?: true,
                options = TUNER_STREAM_OPTIONS.mapNotNull { key -> host.bool(key)?.let { key to it } }.toMap(),
                fallbackMbps = host.long(TUNER_FALLBACK_BITRATE)?.let { mbps(it) },
            )
    }
}

/** The sheet open over the Live TV page. */
sealed interface TunerSheet {
    /** What to add: an HDHomeRun, an M3U playlist, or the tuners the server finds on the network. */
    data object Choose : TunerSheet

    /** The server is listening for tuners, which takes a few seconds. */
    data object Discovering : TunerSheet

    /** What the server found, which may be nothing. */
    data class Found(
        val devices: List<TunerDraft>,
    ) : TunerSheet

    /** Discovery failed; its cause stays alongside Retry in the search sheet. */
    data class Failed(
        val failure: ConsoleFailure,
    ) : TunerSheet

    /**
     * A tuner's form: a new one, or the one with [TunerDraft.id]. [from] is the sheet it was opened from (the choice, or what the
     * search found), which Back returns to; null for a tuner opened from the page's own row.
     */
    data class Form(
        val draft: TunerDraft,
        val from: TunerSheet? = null,
    ) : TunerSheet
}

/** The sheet Back returns to from this one, or null where it has none and Back closes the sheet. */
internal fun TunerSheet.previous(): TunerSheet? =
    when (this) {
        TunerSheet.Choose -> null
        TunerSheet.Discovering, is TunerSheet.Found, is TunerSheet.Failed -> TunerSheet.Choose
        is TunerSheet.Form -> from
    }

/** What went wrong with a tuner change, which decides how the page says it. */
enum class TunerProblem {
    /** The server could not save the tuner, almost always because it could not reach its address. A dialog says why and what to do. */
    NotSaved,

    /** The server itself could not be reached. A snackbar names it. */
    ServerUnreachable,

    /** The change did not go through, and there is nothing more to say than that. */
    NotChanged,
}

private const val MEGA_DIGITS = 6

/** [bits] a second as the megabits the field shows: whole when it is, else with its decimals. */
private fun mbps(bits: Long): String = BigDecimal(bits).movePointLeft(MEGA_DIGITS).stripTrailingZeros().toPlainString()
