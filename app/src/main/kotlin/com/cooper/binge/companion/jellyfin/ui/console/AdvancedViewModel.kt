package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.ACTIVITY_LOG_RETENTION_DAYS
import com.cooper.binge.companion.jellyfin.server.ALLOW_CLIENT_LOG_UPLOAD
import com.cooper.binge.companion.jellyfin.server.AdvancedSettings
import com.cooper.binge.companion.jellyfin.server.CACHE_SIZE
import com.cooper.binge.companion.jellyfin.server.CAST_RECEIVER_APPLICATIONS
import com.cooper.binge.companion.jellyfin.server.CORS_HOSTS
import com.cooper.binge.companion.jellyfin.server.CastReceiver
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ENABLE_LEGACY_AUTHORIZATION
import com.cooper.binge.companion.jellyfin.server.ENABLE_METRICS
import com.cooper.binge.companion.jellyfin.server.ENABLE_SLOW_RESPONSE_WARNING
import com.cooper.binge.companion.jellyfin.server.IMAGE_EXTRACTION_TIMEOUT_MS
import com.cooper.binge.companion.jellyfin.server.INACTIVE_SESSION_THRESHOLD
import com.cooper.binge.companion.jellyfin.server.LIBRARY_MONITOR_DELAY
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SCAN_CONCURRENCY
import com.cooper.binge.companion.jellyfin.server.LIBRARY_UPDATE_DURATION
import com.cooper.binge.companion.jellyfin.server.PARALLEL_IMAGE_ENCODING_LIMIT
import com.cooper.binge.companion.jellyfin.server.SLOW_RESPONSE_THRESHOLD_MS
import com.cooper.binge.companion.jellyfin.server.advancedSettings
import com.cooper.binge.companion.jellyfin.server.castReceiversJson
import com.cooper.binge.companion.jellyfin.server.corsHostsJson
import com.cooper.binge.companion.jellyfin.server.saveServerConfiguration
import com.cooper.binge.companion.jellyfin.server.serverConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * A number on the Advanced page, with the range this app allows: a value outside it is never sent. The server states no
 * ranges of its own, so these are this app's choice.
 */
enum class AdvancedNumber(
    val key: String,
    val min: Int,
    val max: Int,
) {
    RetentionDays(ACTIVITY_LOG_RETENTION_DAYS, 1, MAX_RETENTION_DAYS),
    MonitorDelay(LIBRARY_MONITOR_DELAY, 1, MAX_SECONDS),
    UpdateDuration(LIBRARY_UPDATE_DURATION, 1, MAX_SECONDS),
    ScanConcurrency(LIBRARY_SCAN_CONCURRENCY, 0, MAX_CONCURRENCY),
    ImageEncodingLimit(PARALLEL_IMAGE_ENCODING_LIMIT, 0, MAX_CONCURRENCY),
    ExtractionTimeout(IMAGE_EXTRACTION_TIMEOUT_MS, 0, MAX_TIMEOUT_MS),
    SlowThreshold(SLOW_RESPONSE_THRESHOLD_MS, 1, MAX_TIMEOUT_MS),
    InactiveThreshold(INACTIVE_SESSION_THRESHOLD, 0, MAX_MINUTES),
    CacheSize(CACHE_SIZE, 1, MAX_CACHE_SIZE),
    ;

    /** [text] as this number, or null if it isn't a whole number in range. */
    fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in min..max }
}

/** The two lists the page edits in a sheet. */
enum class AdvancedList { CorsHosts, CastReceivers }

/** [text] as hosts, one per line or separated by commas, without repeats; null for none, which the server reads as no host allowed. */
fun parseHosts(text: String): List<String> =
    text
        .split('\n', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/** [text] as cast receivers, one per line as `id, name`, or null if a line has no id or no name (the server needs both). */
fun parseCastReceivers(text: String): List<CastReceiver>? {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val receivers =
        lines.map { line ->
            val id = line.substringBefore(',').trim()
            val name = line.substringAfter(',', "").trim()
            if (id.isEmpty() || name.isEmpty()) return null
            CastReceiver(id, name)
        }
    return receivers.distinctBy { it.id }
}

sealed interface AdvancedUiState {
    data object Loading : AdvancedUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : AdvancedUiState

    /**
     * The options as shown, the number or list being edited in a sheet, where the saves stand ([save]), and the last
     * save the server refused, which was put back ([refused]).
     */
    data class Ready(
        val settings: AdvancedSettings,
        val editing: AdvancedNumber? = null,
        val editingList: AdvancedList? = null,
        val save: SaveProgress = SaveProgress(),
        val refused: ConsoleFailure? = null,
    ) : AdvancedUiState
}

/**
 * The server's advanced options, each saved as it's made (see [ConfigurationSaves]); a number or a list saves when its
 * sheet does. They are keys of the server's configuration, so each change sends the whole configuration with that one
 * key changed.
 */
class AdvancedViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<AdvancedUiState>(AdvancedUiState.Loading)
    val state: StateFlow<AdvancedUiState> = _state.asStateFlow()

    private val saves =
        ConfigurationSaves(
            connection,
            viewModelScope,
            TAG,
            { _, client, json -> client.saveServerConfiguration(json) },
            onProgress = { progress -> update { copy(save = progress) } },
        ) { shown, failure ->
            update { copy(settings = shown.advancedSettings(), refused = refusedAfter(refused, failure)) }
        }

    init {
        load()
    }

    fun load() {
        _state.value = AdvancedUiState.Loading
        viewModelScope.launch {
            connection.awaitSaves()
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.serverConfiguration() }
                    .fold(
                        onSuccess = {
                            saves.loaded(it)
                            AdvancedUiState.Ready(it.advancedSettings())
                        },
                        onFailure = { AdvancedUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    fun edit(number: AdvancedNumber?) = update { copy(editing = number) }

    fun editList(list: AdvancedList?) = update { copy(editingList = list) }

    /** Saves what the sheet for [number] holds and closes it; text that isn't in range keeps it open and saves nothing. */
    fun save(
        number: AdvancedNumber,
        text: String,
    ) {
        val value = number.parse(text) ?: return
        update { copy(editing = null) }
        change(number.key, JsonPrimitive(value))
    }

    /** Saves the list the sheet holds and closes it; a cast receiver list with a line missing its id or name stays open. */
    fun saveList(
        list: AdvancedList,
        text: String,
    ) {
        val value: JsonElement =
            when (list) {
                AdvancedList.CorsHosts -> corsHostsJson(parseHosts(text))
                AdvancedList.CastReceivers -> castReceiversJson(parseCastReceivers(text) ?: return)
            }
        update { copy(editingList = null) }
        change(if (list == AdvancedList.CorsHosts) CORS_HOSTS else CAST_RECEIVER_APPLICATIONS, value)
    }

    fun setSlowResponseWarning(on: Boolean) = change(ENABLE_SLOW_RESPONSE_WARNING, JsonPrimitive(on))

    fun setMetrics(on: Boolean) = change(ENABLE_METRICS, JsonPrimitive(on))

    fun setAllowClientLogUpload(on: Boolean) = change(ALLOW_CLIENT_LOG_UPLOAD, JsonPrimitive(on))

    fun setLegacyAuthorization(on: Boolean) = change(ENABLE_LEGACY_AUTHORIZATION, JsonPrimitive(on))

    private fun change(
        key: String,
        value: JsonElement,
    ) = saves.set(key, value)

    fun retrySave() = saves.retry()

    /** A change still waiting out its delay is sent now, where this page's scope can no longer cancel it. */
    override fun onCleared() {
        saves.cleared()
    }

    private fun update(change: AdvancedUiState.Ready.() -> AdvancedUiState.Ready) =
        _state.update { (it as? AdvancedUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinAdvanced"
    }
}

// Every range here is this app's own: the server states none. Delays and times are bounded at an hour, a day for the
// session threshold, ten minutes for a timeout; retention at ten years, which is more than any log is wanted for.
private const val MAX_RETENTION_DAYS = 3650
private const val MAX_SECONDS = 3600
private const val MAX_CONCURRENCY = 64
private const val MAX_TIMEOUT_MS = 600_000
private const val MAX_MINUTES = 1440
private const val MAX_CACHE_SIZE = 1_000_000
