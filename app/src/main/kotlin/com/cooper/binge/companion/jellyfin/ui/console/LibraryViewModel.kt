package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.AvailableProviders
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.Country
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.ProviderGroup
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.server.addLibraryPath
import com.cooper.binge.companion.jellyfin.server.availableProviders
import com.cooper.binge.companion.jellyfin.server.countries
import com.cooper.binge.companion.jellyfin.server.cultures
import com.cooper.binge.companion.jellyfin.server.folderExists
import com.cooper.binge.companion.jellyfin.server.libraries
import com.cooper.binge.companion.jellyfin.server.librariesWithId
import com.cooper.binge.companion.jellyfin.server.providerOrder
import com.cooper.binge.companion.jellyfin.server.providersDisabled
import com.cooper.binge.companion.jellyfin.server.removeLibrary
import com.cooper.binge.companion.jellyfin.server.removeLibraryPath
import com.cooper.binge.companion.jellyfin.server.renameLibrary
import com.cooper.binge.companion.jellyfin.server.saveLibraryOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Where in a library's pages the user is: the library itself, a list of its providers, or one provider. */
sealed interface LibraryPage {
    data object Main : LibraryPage

    data class Providers(
        val group: ProviderGroup,
    ) : LibraryPage

    data class One(
        val group: ProviderGroup,
        val name: String,
    ) : LibraryPage
}

/** What a library's page has open over it: a sheet to type in or pick from, or a confirm. */
sealed interface LibraryDialog {
    data object Rename : LibraryDialog

    data object Remove : LibraryDialog

    data object AddFolder : LibraryDialog

    data object SeasonZero : LibraryDialog

    data class RemoveFolder(
        val path: String,
    ) : LibraryDialog
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : LibraryUiState

    /**
     * The library and its options as shown (a change shows at once, and is put back if the server refuses it),
     * what the server offers to choose from, and the page and dialog open. [busy] is a folder, rename or remove
     * in flight; [failed] the last of those that the server refused; [folderMissing] a typed folder it hasn't got; [nameTaken] a new name another library has;
     * and [gone] a removed library, which closes the page.
     */
    data class Ready(
        val library: ServerLibrary,
        val options: JsonObject,
        val available: AvailableProviders = AvailableProviders(),
        val cultures: List<Culture> = emptyList(),
        val countries: List<Country> = emptyList(),
        val page: LibraryPage = LibraryPage.Main,
        val dialog: LibraryDialog? = null,
        val busy: Boolean = false,
        val saveFailed: ConsoleFailure? = null,
        val failed: ConsoleFailure? = null,
        val folderMissing: Boolean = false,
        val nameTaken: Boolean = false,
        val gone: Boolean = false,
    ) : LibraryUiState
}

/**
 * One library's page: its folders, its name, and its options, which save as they're made (see
 * [ConfigurationSaves]). The options are one object the server replaces whole, so each change sends the
 * object as last read with that key changed, and a key this app doesn't know goes back untouched.
 * Folders, rename and remove are single calls that re-read the library after.
 */
class LibraryViewModel(
    private val connection: Connection,
    private val start: ServerLibrary,
) : ViewModel() {
    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** The library's id now: a rename gives it a new one. */
    private var id = start.id
    private var name = start.name

    private val saves =
        ConfigurationSaves(connection, TAG, { _, client, json -> client.saveLibraryOptions(id, json) }) { shown, failure ->
            update { copy(options = shown, saveFailed = failure) }
        }

    init {
        load()
    }

    fun load() {
        _state.value = LibraryUiState.Loading
        viewModelScope.launch {
            connection.awaitSaves()
            _state.value =
                connection
                    .consoleCall(TAG) { _, client ->
                        coroutineScope {
                            val fresh = async { client.libraries().firstOrNull(::isThis) ?: start }
                            // What the server offers to choose from is a nicety: the page works without it.
                            val available = async { attempt { client.availableProviders(start.type) }.getOrDefault(AvailableProviders()) }
                            val cultures = async { attempt { client.cultures() }.getOrDefault(emptyList()) }
                            val countries = async { attempt { client.countries() }.getOrDefault(emptyList()) }
                            LibraryUiState.Ready(
                                library = fresh.await(),
                                options = fresh.await().options,
                                available = available.await(),
                                cultures = cultures.await().sortedBy { it.displayName },
                                countries = countries.await().sortedBy { it.displayName },
                            )
                        }
                    }.fold(
                        onSuccess = {
                            // The server may have given the library an id since the list was read.
                            id = it.library.id
                            name = it.library.name
                            saves.loaded(it.options)
                            it
                        },
                        onFailure = { LibraryUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    fun show(page: LibraryPage) = update { copy(page = page) }

    /** Opens [dialog], or closes whichever is open for null. */
    fun ask(dialog: LibraryDialog?) = update { copy(dialog = dialog, folderMissing = false, nameTaken = false) }

    /** Changes one option, and saves it. */
    fun set(
        key: String,
        value: JsonElement,
    ) = saves.set(key, value)

    fun setProvider(
        group: ProviderGroup,
        name: String,
        enabled: Boolean,
    ) = saves.set(group.disabledKey, saves.shown.providersDisabled(group, name, enabled))

    /** Moves [name] [by] places in [group]'s order: -1 earlier, 1 later. */
    fun moveProvider(
        group: ProviderGroup,
        name: String,
        by: Int,
    ) {
        val ready = _state.value as? LibraryUiState.Ready ?: return
        val names = group.available(ready.available)
        saves.set(group.orderKey, saves.shown.providerOrder(group, names, name, by))
    }

    fun addFolder(path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return
        manage(keepDialog = true) { client, library ->
            if (!client.folderExists(trimmed)) return@manage Outcome.FolderMissing
            client.addLibraryPath(library.name, trimmed)
            Outcome.Done
        }
    }

    fun removeFolder(path: String) =
        manage { client, library ->
            client.removeLibraryPath(library.name, path)
            Outcome.Done
        }

    /** Renames the library to [name], unless it's blank, is the name it has, or is another library's. */
    fun rename(name: String) {
        val newName = name.trim()
        val ready = _state.value as? LibraryUiState.Ready ?: return
        if (newName.isEmpty() || newName == ready.library.name) return
        manage(keepDialog = true) { client, library ->
            if (client.libraries().any { it.name.equals(newName, ignoreCase = true) && it.id != library.id }) return@manage Outcome.Taken
            client.renameLibrary(library.name, newName)
            Outcome.Renamed(newName)
        }
    }

    fun remove() =
        manage { client, library ->
            client.removeLibrary(library.name)
            Outcome.Removed
        }

    private sealed interface Outcome {
        data object Done : Outcome

        data object FolderMissing : Outcome

        data object Taken : Outcome

        data object Removed : Outcome

        data class Renamed(
            val name: String,
        ) : Outcome
    }

    /**
     * Runs a call that changes the library itself, after any option still saving has landed, and shows the
     * library as the server now has it. Only a refused call leaves [LibraryUiState.Ready.failed] set.
     */
    private fun manage(
        keepDialog: Boolean = false,
        call: suspend (JellyfinClient, ServerLibrary) -> Outcome,
    ) {
        val ready = _state.value as? LibraryUiState.Ready ?: return
        update { copy(busy = true, failed = null, folderMissing = false, nameTaken = false, dialog = dialog.takeIf { keepDialog }) }
        viewModelScope.launch {
            connection.awaitSaves()
            connection
                .consoleCall(TAG) { _, client ->
                    val outcome = call(client, ready.library)
                    val name = (outcome as? Outcome.Renamed)?.name ?: ready.library.name
                    val fresh = if (outcome == Outcome.Removed) null else client.librariesWithId(name).firstOrNull { it.name == name }
                    outcome to fresh
                }.onSuccess { (outcome, fresh) -> finished(outcome, fresh) }
                .onFailure { error -> update { copy(busy = false, failed = error.consoleFailure()) } }
        }
    }

    private fun finished(
        outcome: Outcome,
        fresh: ServerLibrary?,
    ) {
        when {
            outcome == Outcome.Removed -> update { copy(busy = false, gone = true, dialog = null) }
            outcome == Outcome.FolderMissing -> update { copy(busy = false, folderMissing = true) }
            outcome == Outcome.Taken -> update { copy(busy = false, nameTaken = true) }
            fresh == null -> update { copy(busy = false, failed = ConsoleFailure.Refused, dialog = null) }
            else -> {
                id = fresh.id
                name = fresh.name
                saves.loaded(fresh.options)
                update { copy(library = fresh, options = fresh.options, busy = false, dialog = null) }
            }
        }
    }

    /** Whether [row] is this library: by id, or by name while the server hasn't given it one. */
    private fun isThis(row: ServerLibrary) = if (id.isEmpty()) row.name == name else row.id == id

    private fun update(change: LibraryUiState.Ready.() -> LibraryUiState.Ready) =
        _state.update { (it as? LibraryUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinLibrary"
    }
}

/** The names the server offers for this group of providers. */
internal fun ProviderGroup.available(providers: AvailableProviders): List<String> =
    when (this) {
        ProviderGroup.Readers -> providers.metadataReaders
        ProviderGroup.Subtitles -> providers.subtitleFetchers
        ProviderGroup.Lyrics -> providers.lyricFetchers
        ProviderGroup.Segments -> providers.segmentProviders
    }
