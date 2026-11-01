package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.DisplayList
import com.cooper.binge.companion.jellyfin.server.ListEntry
import com.cooper.binge.companion.jellyfin.server.UiLanguage
import com.cooper.binge.companion.jellyfin.server.entries
import com.cooper.binge.companion.jellyfin.server.jsonEntries
import com.cooper.binge.companion.jellyfin.server.saveServerConfiguration
import com.cooper.binge.companion.jellyfin.server.serverConfiguration
import com.cooper.binge.companion.jellyfin.server.uiLanguages
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A sheet open over the page. */
enum class DisplaySheet { ChapterDuration, MetadataPath, NewEntry }

/** The longest dummy chapter duration this app sends, in seconds (a day); the server has no limit, Jellyfin's own page none either. */
const val MAX_DUMMY_CHAPTER_SECONDS = 86_400

/** [text] as a whole number of seconds a dummy chapter lasts, or null if it isn't one in range. */
fun parseChapterSeconds(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 0..MAX_DUMMY_CHAPTER_SECONDS }

sealed interface LibraryDisplayUiState {
    data object Loading : LibraryDisplayUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : LibraryDisplayUiState

    /**
     * The configuration as shown, the languages the server can be shown in, the sheet open, and the list being
     * edited with its [draft] (a list saves with its Save, and Back drops it).
     */
    data class Ready(
        val configuration: JsonObject,
        val languages: List<UiLanguage> = emptyList(),
        val sheet: DisplaySheet? = null,
        val editing: DisplayList? = null,
        val draft: List<ListEntry> = emptyList(),
        val saveFailed: ConsoleFailure? = null,
    ) : LibraryDisplayUiState
}

/**
 * The server's library display and metadata defaults, each saved as it's made (see [ConfigurationSaves], the
 * same save as Server settings: the whole configuration goes back with one key changed). A list is edited as a
 * draft and saves with its Save. The language list is a nicety: the page works without it.
 */
class LibraryDisplayViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<LibraryDisplayUiState>(LibraryDisplayUiState.Loading)
    val state: StateFlow<LibraryDisplayUiState> = _state.asStateFlow()

    private val saves =
        ConfigurationSaves(connection, TAG, { _, client, json -> client.saveServerConfiguration(json) }) { shown, failure ->
            update { copy(configuration = shown, saveFailed = failure) }
        }

    init {
        load()
    }

    fun load() {
        _state.value = LibraryDisplayUiState.Loading
        viewModelScope.launch {
            connection.awaitSaves()
            _state.value =
                connection
                    .consoleCall(TAG) { _, client ->
                        coroutineScope {
                            val languages = async { attempt { client.uiLanguages() }.getOrDefault(emptyList()) }
                            client.serverConfiguration() to languages.await()
                        }
                    }.fold(
                        onSuccess = { (configuration, languages) ->
                            saves.loaded(configuration)
                            LibraryDisplayUiState.Ready(configuration, languages)
                        },
                        onFailure = { LibraryDisplayUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /** Changes one setting, and saves it. */
    fun set(
        key: String,
        value: JsonElement,
    ) = saves.set(key, value)

    /** Opens [sheet], or closes whichever is open for null. */
    fun sheet(sheet: DisplaySheet?) = update { copy(sheet = sheet) }

    /** Opens the editor for [list] with its entries as a draft, or closes the editor for null, dropping the draft. */
    fun edit(list: DisplayList?) = update { copy(editing = list, draft = list?.let { configuration.entries(it) }.orEmpty(), sheet = null) }

    /** Adds [entry] to the draft, unless it is blank or already there. */
    fun add(entry: ListEntry) {
        val clean = entry.copy(first = entry.first.trim(), second = entry.second?.trim())
        if (clean.first.isEmpty() || clean.second?.isEmpty() == true) return
        update { copy(draft = if (clean in draft) draft else draft + clean, sheet = null) }
    }

    fun remove(index: Int) = update { copy(draft = draft.filterIndexed { i, _ -> i != index }) }

    /** Saves the draft as the list's setting, and closes the editor. */
    fun saveList() {
        val ready = _state.value as? LibraryDisplayUiState.Ready ?: return
        val list = ready.editing ?: return
        saves.set(list.key, jsonEntries(list, ready.draft))
        update { copy(editing = null, draft = emptyList()) }
    }

    private fun update(change: LibraryDisplayUiState.Ready.() -> LibraryDisplayUiState.Ready) =
        _state.update { (it as? LibraryDisplayUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinLibraryDisplay"
    }
}
