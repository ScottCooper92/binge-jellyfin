package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ENCODING_DECODING_CODECS
import com.cooper.binge.companion.jellyfin.server.ENCODING_DOWNMIX_BOOST
import com.cooper.binge.companion.jellyfin.server.ENCODING_SEGMENT_KEEP
import com.cooper.binge.companion.jellyfin.server.ENCODING_THROTTLE_DELAY
import com.cooper.binge.companion.jellyfin.server.ENCODING_TONEMAPPING_DESAT
import com.cooper.binge.companion.jellyfin.server.ENCODING_TONEMAPPING_PARAM
import com.cooper.binge.companion.jellyfin.server.ENCODING_TONEMAPPING_PEAK
import com.cooper.binge.companion.jellyfin.server.ENCODING_VPP_BRIGHTNESS
import com.cooper.binge.companion.jellyfin.server.ENCODING_VPP_CONTRAST
import com.cooper.binge.companion.jellyfin.server.SERVER_BITRATE_LIMIT
import com.cooper.binge.companion.jellyfin.server.SERVER_MAX_AUDIOBOOK_RESUME
import com.cooper.binge.companion.jellyfin.server.SERVER_MAX_RESUME_PERCENT
import com.cooper.binge.companion.jellyfin.server.SERVER_MIN_AUDIOBOOK_RESUME
import com.cooper.binge.companion.jellyfin.server.SERVER_MIN_RESUME_PERCENT
import com.cooper.binge.companion.jellyfin.server.SERVER_MIN_RESUME_SECONDS
import com.cooper.binge.companion.jellyfin.server.encodingConfiguration
import com.cooper.binge.companion.jellyfin.server.jsonStrings
import com.cooper.binge.companion.jellyfin.server.saveEncodingConfiguration
import com.cooper.binge.companion.jellyfin.server.saveServerConfiguration
import com.cooper.binge.companion.jellyfin.server.serverConfiguration
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Which of the server's two configurations a setting is in. */
enum class ConfigurationKind { Encoding, Server }

private const val BITS_PER_MEGABIT = 1_000_000

/** What a number counts, for its sheet's hint. */
enum class NumberUnit { None, Seconds, Percent, Minutes, Mbps }

/**
 * A number typed into a sheet, with its range and unit. [min] and [max] are jellyfin-web's where it sets them (the
 * tone mapping, downmix, throttle and segment ranges) and this app's own where it doesn't. [whole] is a whole number,
 * else a decimal; [scale] is how many of the stored unit make one shown (the bitrate is stored in bits, shown in Mbps).
 */
enum class NumberField(
    val kind: ConfigurationKind,
    val key: String,
    val min: Double,
    val max: Double,
    val whole: Boolean,
    val unit: NumberUnit,
    val scale: Int = 1,
) {
    DownmixBoost(ConfigurationKind.Encoding, ENCODING_DOWNMIX_BOOST, 0.5, 3.0, false, NumberUnit.None),
    ThrottleDelay(ConfigurationKind.Encoding, ENCODING_THROTTLE_DELAY, 10.0, 3600.0, true, NumberUnit.Seconds),
    SegmentKeep(ConfigurationKind.Encoding, ENCODING_SEGMENT_KEEP, 15.0, 3600.0, true, NumberUnit.Seconds),
    Desaturation(ConfigurationKind.Encoding, ENCODING_TONEMAPPING_DESAT, 0.0, 1000.0, false, NumberUnit.None),
    PeakBrightness(ConfigurationKind.Encoding, ENCODING_TONEMAPPING_PEAK, 0.0, 10000.0, false, NumberUnit.None),
    ToneMappingParam(ConfigurationKind.Encoding, ENCODING_TONEMAPPING_PARAM, 0.0, 1000.0, false, NumberUnit.None),
    VppBrightness(ConfigurationKind.Encoding, ENCODING_VPP_BRIGHTNESS, 0.0, 100.0, false, NumberUnit.None),
    VppContrast(ConfigurationKind.Encoding, ENCODING_VPP_CONTRAST, 1.0, 2.0, false, NumberUnit.None),
    MinResumePercent(ConfigurationKind.Server, SERVER_MIN_RESUME_PERCENT, 0.0, 100.0, true, NumberUnit.Percent),
    MaxResumePercent(ConfigurationKind.Server, SERVER_MAX_RESUME_PERCENT, 0.0, 100.0, true, NumberUnit.Percent),
    MinResumeSeconds(ConfigurationKind.Server, SERVER_MIN_RESUME_SECONDS, 0.0, 86_400.0, true, NumberUnit.Seconds),
    MinAudiobookResume(ConfigurationKind.Server, SERVER_MIN_AUDIOBOOK_RESUME, 0.0, 1_440.0, true, NumberUnit.Minutes),
    MaxAudiobookResume(ConfigurationKind.Server, SERVER_MAX_AUDIOBOOK_RESUME, 0.0, 1_440.0, true, NumberUnit.Minutes),
    BitrateLimit(ConfigurationKind.Server, SERVER_BITRATE_LIMIT, 0.0, 1_000.0, false, NumberUnit.Mbps, BITS_PER_MEGABIT),
    ;

    /** [text] as the value to store, or null if it isn't a number in this field's range. */
    fun parse(text: String): JsonPrimitive? {
        val shown =
            text
                .trim()
                .replace(',', '.')
                .toDoubleOrNull()
                ?.takeIf { it in min..max } ?: return null
        if (whole) return shown.takeIf { it % 1.0 == 0.0 }?.let { JsonPrimitive(it.toInt()) }
        return if (scale == 1) JsonPrimitive(shown) else JsonPrimitive((shown * scale).toInt())
    }
}

/** A text typed into a sheet. */
enum class TextField(
    val key: String,
) {
    VaapiDevice("VaapiDevice"),
    QsvDevice("QsvDevice"),
    FallbackFont("FallbackFontPath"),
}

/** The sheet open over the page: a number, a text, or the temporary files folder. */
sealed interface TranscodingSheet {
    data class Number(
        val field: NumberField,
    ) : TranscodingSheet

    data class Text(
        val field: TextField,
    ) : TranscodingSheet

    data object TempFolder : TranscodingSheet
}

sealed interface TranscodingUiState {
    data object Loading : TranscodingUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : TranscodingUiState

    /** The two configurations as shown, the sheet open, and the last save that failed and was put back. */
    data class Ready(
        val encoding: JsonObject,
        val server: JsonObject,
        val sheet: TranscodingSheet? = null,
        val saveFailed: ConsoleFailure? = null,
    ) : TranscodingUiState
}

/**
 * The server's transcoding, resume and streaming settings. The transcoding options are the server's encoding
 * configuration and the rest are in its main one; each is saved as it's made through its own [ConfigurationSaves],
 * so a change sends that whole configuration with one key changed, and a key this app doesn't know goes back as
 * it came. A number or text saves with its sheet.
 */
class TranscodingViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<TranscodingUiState>(TranscodingUiState.Loading)
    val state: StateFlow<TranscodingUiState> = _state.asStateFlow()

    private val encoding =
        ConfigurationSaves(connection, TAG, { _, client, json -> client.saveEncodingConfiguration(json) }) { shown, failure ->
            update { copy(encoding = shown, saveFailed = failure) }
        }
    private val server =
        ConfigurationSaves(connection, TAG, { _, client, json -> client.saveServerConfiguration(json) }) { shown, failure ->
            update { copy(server = shown, saveFailed = failure) }
        }

    init {
        load()
    }

    fun load() {
        _state.value = TranscodingUiState.Loading
        viewModelScope.launch {
            connection.awaitSaves()
            _state.value =
                connection
                    .consoleCall(TAG) { _, client ->
                        coroutineScope {
                            val server = async { client.serverConfiguration() }
                            client.encodingConfiguration() to server.await()
                        }
                    }.fold(
                        onSuccess = { (encodingConfiguration, serverConfiguration) ->
                            encoding.loaded(encodingConfiguration)
                            server.loaded(serverConfiguration)
                            TranscodingUiState.Ready(encodingConfiguration, serverConfiguration)
                        },
                        onFailure = { TranscodingUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /** Changes one setting of the encoding configuration, and saves it. */
    fun setEncoding(
        key: String,
        value: JsonElement,
    ) = encoding.set(key, value)

    /** Changes one setting of the server's main configuration, and saves it. */
    fun setServer(
        key: String,
        value: JsonElement,
    ) = server.set(key, value)

    /** Opens [sheet], or closes whichever is open for null. */
    fun sheet(sheet: TranscodingSheet?) = update { copy(sheet = sheet) }

    /** Saves the number [text] in [field], and closes its sheet; text that isn't in range keeps it open and saves nothing. */
    fun saveNumber(
        field: NumberField,
        text: String,
    ) {
        val value = field.parse(text) ?: return
        if (field.kind == ConfigurationKind.Encoding) encoding.set(field.key, value) else server.set(field.key, value)
        update { copy(sheet = null) }
    }

    /** Switches a hardware decoding codec on or off: the list holds the ones on. */
    fun setCodec(
        codec: String,
        on: Boolean,
    ) {
        val codecs = (encoding.shown[ENCODING_DECODING_CODECS] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
        encoding.set(ENCODING_DECODING_CODECS, jsonStrings(if (on) (codecs + codec).distinct() else codecs - codec))
    }

    private fun update(change: TranscodingUiState.Ready.() -> TranscodingUiState.Ready) =
        _state.update { (it as? TranscodingUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinTranscoding"
    }
}
