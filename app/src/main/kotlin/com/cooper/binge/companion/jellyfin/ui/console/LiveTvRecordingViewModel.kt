package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_GUIDE_DAYS
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_MOVIE_PATH
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_POST_PADDING
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_PRE_PADDING
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_RECORDING_PATH
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_SERIES_PATH
import com.cooper.binge.companion.jellyfin.server.liveTvConfiguration
import com.cooper.binge.companion.jellyfin.server.saveLiveTvConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The three folders a recording can go to, by the key each is stored under. */
enum class RecordingFolder(
    val key: String,
) {
    All(LIVE_TV_RECORDING_PATH),
    Movies(LIVE_TV_MOVIE_PATH),
    Series(LIVE_TV_SERIES_PATH),
}

/**
 * A recording number. [seconds] distinguishes padding in seconds from guide length in days; [blankClears] says whether
 * an empty entry clears the setting (the server then chooses) rather than being refused.
 */
enum class RecordingNumber(
    val key: String,
    val min: Int,
    val max: Int,
    val seconds: Boolean,
    val blankClears: Boolean,
) {
    PrePadding(LIVE_TV_PRE_PADDING, 0, 120, seconds = true, blankClears = false),
    PostPadding(LIVE_TV_POST_PADDING, 0, 120, seconds = true, blankClears = false),
    GuideDays(LIVE_TV_GUIDE_DAYS, 1, 14, seconds = false, blankClears = true),
    ;

    /** [text] as the value to store, or null when it is not a whole number in range (an empty entry is [JsonNull] where it clears). */
    fun parse(text: String): JsonElement? {
        if (text.isBlank()) return JsonNull.takeIf { blankClears }
        val shown = text.trim().toIntOrNull()?.takeIf { it in min..max } ?: return null
        return JsonPrimitive(shown)
    }

    /** The stored value of [config] as people read it, or empty when the server has none. */
    fun shown(config: JsonObject): String {
        val stored = (config[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.toIntOrNull() ?: return ""
        return stored.toString()
    }
}

/** The sheet open over the page. */
sealed interface RecordingSheet {
    data class Folder(
        val folder: RecordingFolder,
    ) : RecordingSheet

    data class Number(
        val field: RecordingNumber,
    ) : RecordingSheet
}

sealed interface LiveTvRecordingUiState {
    data object Loading : LiveTvRecordingUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : LiveTvRecordingUiState

    /** The Live TV configuration as shown, the sheet open, where the saves stand, and the last save the server refused ([refused]). */
    data class Ready(
        val config: JsonObject,
        val sheet: RecordingSheet? = null,
        val save: SaveProgress = SaveProgress(),
        val refused: ConsoleFailure? = null,
    ) : LiveTvRecordingUiState
}

/**
 * How the server records, from its Live TV configuration. Each change is saved as it is made with the whole configuration sent back,
 * so the tuners and guide providers, and any key this app does not know, return as the server sent them.
 */
class LiveTvRecordingViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<LiveTvRecordingUiState>(LiveTvRecordingUiState.Loading)
    val state: StateFlow<LiveTvRecordingUiState> = _state.asStateFlow()

    private val saves =
        ConfigurationSaves(
            connection,
            viewModelScope,
            TAG,
            { _, client, json -> client.saveLiveTvConfiguration(json) },
            onProgress = { progress -> update { copy(save = progress) } },
        ) { shown, failure ->
            update { copy(config = shown, refused = refusedAfter(refused, failure)) }
        }

    init {
        load()
    }

    /** A change still waiting out its delay is sent now, where this page's scope can no longer cancel it. */
    override fun onCleared() = saves.cleared()

    fun load() {
        _state.value = LiveTvRecordingUiState.Loading
        viewModelScope.launch {
            connection.awaitSaves()
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.liveTvConfiguration() }
                    .fold(
                        onSuccess = { config ->
                            saves.loaded(config)
                            LiveTvRecordingUiState.Ready(config)
                        },
                        onFailure = { LiveTvRecordingUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /** Changes one setting and saves it. */
    fun set(
        key: String,
        value: JsonElement,
    ) = saves.set(key, value)

    /** Sends the change a failed write left unsent. */
    fun retrySave() = saves.retry()

    /** Opens [sheet], or closes whichever is open for null. */
    fun sheet(sheet: RecordingSheet?) = update { copy(sheet = sheet) }

    /** Saves a folder and closes its sheet. A blank path clears the folder. */
    fun saveFolder(
        folder: RecordingFolder,
        path: String,
    ) {
        saves.set(folder.key, JsonPrimitive(path.trim()))
        update { copy(sheet = null) }
    }

    /** Saves the number [text] in [field] and closes its sheet; text out of range keeps it open and saves nothing. */
    fun saveNumber(
        field: RecordingNumber,
        text: String,
    ) {
        val value = field.parse(text) ?: return
        saves.set(field.key, value)
        update { copy(sheet = null) }
    }

    private fun update(change: LiveTvRecordingUiState.Ready.() -> LiveTvRecordingUiState.Ready) =
        _state.update { (it as? LiveTvRecordingUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinLiveTvRecording"
    }
}
