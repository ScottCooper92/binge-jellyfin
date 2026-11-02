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
     * edited, whose entries are [draft] (each add and remove saves as it's made, so Back loses nothing).
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
 * same save as Server settings: the whole configuration goes back with one key changed). A list's editor saves
 * each add and remove the same way. The language list is a nicety: the page works without it.
 */
class LibraryDisplayViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<LibraryDisplayUiState>(LibraryDisplayUiState.Loading)
    val state: StateFlow<LibraryDisplayUiState> = _state.asStateFlow()

    private val saves =
        ConfigurationSaves(connection, viewModelScope, TAG, { _, client, json -> client.saveServerConfiguration(json) }) { shown, failure ->
            update { copy(configuration = shown, saveFailed = failure, draft = editing?.let { shown.entries(it) } ?: draft) }
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

    /** Opens the editor for [list] with its entries, or closes the editor for null. */
    fun edit(list: DisplayList?) = update { copy(editing = list, draft = list?.let { configuration.entries(it) }.orEmpty(), sheet = null) }

    /** Adds [entry] to the list and saves it, unless it is blank or already there. */
    fun add(entry: ListEntry) {
        val clean = entry.copy(first = entry.first.trim(), second = entry.second?.trim())
        if (clean.first.isEmpty() || clean.second?.isEmpty() == true) return
        update { copy(sheet = null) }
        changeList { if (clean in it) it else it + clean }
    }

    fun remove(index: Int) = changeList { entries -> entries.filterIndexed { i, _ -> i != index } }

    private fun changeList(change: (List<ListEntry>) -> List<ListEntry>) {
        val ready = _state.value as? LibraryDisplayUiState.Ready ?: return
        val list = ready.editing ?: return
        val entries = change(ready.draft)
        if (entries != ready.draft) saves.set(list.key, jsonEntries(list, entries))
    }

    /** A change still waiting out its delay is sent now, where this page's scope can no longer cancel it. */
    override fun onCleared() {
        saves.cleared()
    }

    private fun update(change: LibraryDisplayUiState.Ready.() -> LibraryDisplayUiState.Ready) =
        _state.update { (it as? LibraryDisplayUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinLibraryDisplay"
    }
}
