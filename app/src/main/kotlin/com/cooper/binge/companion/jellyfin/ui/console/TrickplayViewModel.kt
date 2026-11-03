package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_HW_ACCELERATION
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_HW_ENCODING
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_INTERVAL
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_JPEG_QUALITY
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_KEY_FRAME_ONLY
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_OPTIONS
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_PRIORITIES
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_PROCESS_PRIORITY
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_QSCALE
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_SCAN_BEHAVIOR
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_SCAN_BEHAVIORS
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_THREADS
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_TILE_HEIGHT
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_TILE_WIDTH
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_WIDTHS
import com.cooper.binge.companion.jellyfin.server.TrickplaySettings
import com.cooper.binge.companion.jellyfin.server.saveServerConfiguration
import com.cooper.binge.companion.jellyfin.server.serverConfiguration
import com.cooper.binge.companion.jellyfin.server.trickplayOptions
import com.cooper.binge.companion.jellyfin.server.trickplaySettings
import com.cooper.binge.companion.jellyfin.server.withTrickplayOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * A number (or the widths) edited in a dialog, with the range Jellyfin's own settings page allows, so
 * a value the server would refuse is never sent. [Interval] is in milliseconds; [Widths] is a list.
 */
enum class TrickplayField(
    val key: String,
    val min: Int,
    val max: Int,
) {
    Interval(TRICKPLAY_INTERVAL, 1, MAX_INTERVAL_MILLIS),
    Widths(TRICKPLAY_WIDTHS, 1, MAX_WIDTH),
    TileWidth(TRICKPLAY_TILE_WIDTH, 1, MAX_TILE),
    TileHeight(TRICKPLAY_TILE_HEIGHT, 1, MAX_TILE),
    Qscale(TRICKPLAY_QSCALE, 2, MAX_QSCALE),
    JpegQuality(TRICKPLAY_JPEG_QUALITY, 1, MAX_JPEG_QUALITY),
    Threads(TRICKPLAY_THREADS, 0, MAX_THREADS),
    ;

    /** [text] as this field's number, or null if it isn't a whole number in range. Not for [Widths]. */
    fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in min..max }
}

/** [text] as a list of widths (`320, 640`), smallest first, or null if any isn't a width or there are none. */
fun parseWidths(text: String): List<Int>? {
    val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val widths = parts.map { it.toIntOrNull()?.takeIf { w -> w in TrickplayField.Widths.min..TrickplayField.Widths.max } ?: return null }
    return widths.distinct().sorted().takeIf { it.isNotEmpty() }
}

sealed interface TrickplayUiState {
    data object Loading : TrickplayUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : TrickplayUiState

    /**
     * The options as shown, the field being edited in a sheet, where the saves stand ([save]), and the last save the
     * server refused, which was put back ([refused]). A sheet that is [submitting] stays open while its save runs; if the
     * server refuses it, [sheetFailed] says why in the sheet.
     */
    data class Ready(
        val settings: TrickplaySettings,
        val editing: TrickplayField? = null,
        val save: SaveProgress = SaveProgress(),
        val refused: ConsoleFailure? = null,
        val submitting: Boolean = false,
        val sheetFailed: ConsoleFailure? = null,
    ) : TrickplayUiState
}

/**
 * The server's trickplay options, each saved as it's made (see [ConfigurationSaves]); a number saves
 * when its dialog does. The options are one object in the server's configuration, so each change sends
 * the whole object with that one option changed, and the configuration around it untouched.
 */
class TrickplayViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<TrickplayUiState>(TrickplayUiState.Loading)
    val state: StateFlow<TrickplayUiState> = _state.asStateFlow()

    private val saves =
        ConfigurationSaves(
            connection,
            viewModelScope,
            TAG,
            { _, client, json -> client.saveServerConfiguration(json) },
            onProgress = { progress ->
                update {
                    // A save that landed closes the sheet that was waiting on it.
                    if (progress.status == SaveStatus.Saved && submitting) {
                        copy(save = progress, submitting = false, editing = null)
                    } else {
                        copy(save = progress)
                    }
                }
            },
        ) { shown, failure ->
            update { copy(settings = shown.trickplaySettings()).routed(failure) }
        }

    init {
        load()
    }

    fun load() {
        _state.value = TrickplayUiState.Loading
        viewModelScope.launch {
            connection.awaitSaves()
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.serverConfiguration() }
                    .fold(
                        onSuccess = {
                            saves.loaded(it)
                            TrickplayUiState.Ready(it.trickplaySettings())
                        },
                        onFailure = { TrickplayUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /** Opens the sheet for [field], or closes it for null. A save the closed sheet was waiting on reports on the page. */
    fun edit(field: TrickplayField?) = update { copy(editing = field, submitting = false, sheetFailed = null) }

    /**
     * Saves what the sheet for [field] holds; text that isn't in range keeps it open and saves nothing. The sheet stays
     * open while the save runs: it closes when the server takes it, stays open with what was typed and says why if the
     * server refuses it, and closes if the server can't be reached, the change then waiting unsent on the page. A value
     * that is unchanged closes it.
     */
    fun save(
        field: TrickplayField,
        text: String,
    ) {
        val value: JsonElement =
            if (field == TrickplayField.Widths) {
                parseWidths(text)?.let { widths -> JsonArray(widths.map(::JsonPrimitive)) }
            } else {
                field.parse(text)?.let(::JsonPrimitive)
            } ?: return
        if (saves.shown.trickplayOptions()[field.key] == value) {
            update { copy(editing = null) }
            return
        }
        update { copy(submitting = true, sheetFailed = null, refused = null) }
        change(field.key, value)
    }

    /**
     * Where [failure] shows. A refusal of what an open sheet submitted shows in the sheet, which keeps what was typed; an
     * unreachable server closes it, the change unsent with Retry on the page. Anything else, or a sheet already
     * dismissed, shows on the page.
     */
    private fun TrickplayUiState.Ready.routed(failure: ConsoleFailure?): TrickplayUiState.Ready =
        when {
            submitting && editing != null && failure == ConsoleFailure.Refused ->
                copy(submitting = false, sheetFailed = failure, refused = null)
            submitting && failure == ConsoleFailure.Unreachable -> copy(submitting = false, editing = null)
            else -> copy(refused = refusedAfter(refused, failure))
        }

    fun setHwAcceleration(on: Boolean) = change(TRICKPLAY_HW_ACCELERATION, JsonPrimitive(on))

    fun setHwEncoding(on: Boolean) = change(TRICKPLAY_HW_ENCODING, JsonPrimitive(on))

    /** Only for a server that has the option (10.10): the page doesn't offer it to one that doesn't. */
    fun setKeyFrameOnly(on: Boolean) = change(TRICKPLAY_KEY_FRAME_ONLY, JsonPrimitive(on))

    fun setScanBehavior(value: String) {
        if (value in TRICKPLAY_SCAN_BEHAVIORS) change(TRICKPLAY_SCAN_BEHAVIOR, JsonPrimitive(value))
    }

    fun setProcessPriority(value: String) {
        if (value in TRICKPLAY_PRIORITIES) change(TRICKPLAY_PROCESS_PRIORITY, JsonPrimitive(value))
    }

    /** A preset interval, in milliseconds. */
    fun setInterval(millis: Int) {
        if (TrickplayField.Interval.parse(millis.toString()) != null) change(TRICKPLAY_INTERVAL, JsonPrimitive(millis))
    }

    private fun change(
        key: String,
        value: JsonElement,
    ) = saves.set(TRICKPLAY_OPTIONS, saves.shown.withTrickplayOption(key, value))

    fun retrySave() = saves.retry()

    /** A change still waiting out its delay is sent now, where this page's scope can no longer cancel it. */
    override fun onCleared() {
        saves.cleared()
    }

    private fun update(change: TrickplayUiState.Ready.() -> TrickplayUiState.Ready) =
        _state.update { (it as? TrickplayUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinTrickplay"
    }
}

// The interval, width, tile and thread caps are this app's own; jellyfin-web sets only their minimums.
// The quality scale (2 to 31) and JPEG quality (1 to 100) ceilings are jellyfin-web's.
private const val MAX_INTERVAL_MILLIS = 3_600_000
private const val MAX_WIDTH = 7680
private const val MAX_TILE = 1000
private const val MAX_QSCALE = 31
private const val MAX_JPEG_QUALITY = 100
private const val MAX_THREADS = 256
