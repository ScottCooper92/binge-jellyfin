package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.FetcherKind
import com.cooper.binge.companion.jellyfin.server.METADATA_OPTIONS
import com.cooper.binge.companion.jellyfin.server.TypeFetchers
import com.cooper.binge.companion.jellyfin.server.metadataTypes
import com.cooper.binge.companion.jellyfin.server.saveServerConfiguration
import com.cooper.binge.companion.jellyfin.server.serverConfiguration
import com.cooper.binge.companion.jellyfin.server.typeFetchers
import com.cooper.binge.companion.jellyfin.server.withFetcher
import com.cooper.binge.companion.jellyfin.server.withFetcherMoved
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

sealed interface MetadataDefaultsUiState {
    data object Loading : MetadataDefaultsUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : MetadataDefaultsUiState

    /**
     * The configuration as shown and the fetchers the server offers each item type. A type's page, and a provider's, are
     * screens of their own over the page. [save] is where the saves stand, and [refused] the last the server refused,
     * which was put back.
     */
    data class Ready(
        val configuration: JsonObject,
        val fetchers: List<TypeFetchers> = emptyList(),
        val save: SaveProgress = SaveProgress(),
        val refused: ConsoleFailure? = null,
    ) : MetadataDefaultsUiState
}

/** The fetcher whose page is open: its [kind] and its [name]. */
data class OpenFetcher(
    val kind: FetcherKind,
    val name: String,
)

/** The item types the page lists: those the server's defaults hold an entry for, then any other type a library can hold. */
fun MetadataDefaultsUiState.Ready.types(): List<String> {
    val held = configuration.metadataTypes()
    return held + fetchers.map { it.type }.filter { type -> held.none { it.equals(type, ignoreCase = true) } }
}

/** The names the server offers [type] for [kind], in its order; none where it did not list the type. */
fun MetadataDefaultsUiState.Ready.offered(
    type: String,
    kind: FetcherKind,
): List<String> = fetchers.firstOrNull { it.type.equals(type, ignoreCase = true) }?.of(kind).orEmpty()

/**
 * The server's per-item-type metadata and image fetcher defaults, each switch saved as it's made (see [ConfigurationSaves],
 * the same save as Server settings: the whole configuration goes back with the one list changed). The names the server offers
 * are a nicety: the page works without them, from the names the defaults themselves hold.
 */
class MetadataDefaultsViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<MetadataDefaultsUiState>(MetadataDefaultsUiState.Loading)
    val state: StateFlow<MetadataDefaultsUiState> = _state.asStateFlow()

    private val saves =
        ConfigurationSaves(
            connection,
            viewModelScope,
            TAG,
            { _, client, json -> client.saveServerConfiguration(json) },
            onProgress = { progress -> update { copy(save = progress) } },
        ) { shown, failure ->
            update { copy(configuration = shown, refused = refusedAfter(refused, failure)) }
        }

    init {
        load()
    }

    fun load() {
        _state.value = MetadataDefaultsUiState.Loading
        viewModelScope.launch {
            connection.awaitSaves()
            _state.value =
                connection
                    .consoleCall(TAG) { _, client ->
                        coroutineScope {
                            val fetchers = async { attempt { client.typeFetchers() }.getOrDefault(emptyList()) }
                            client.serverConfiguration() to fetchers.await()
                        }
                    }.fold(
                        onSuccess = { (configuration, fetchers) ->
                            saves.loaded(configuration)
                            MetadataDefaultsUiState.Ready(configuration, fetchers)
                        },
                        onFailure = { MetadataDefaultsUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /** A type's or provider's page opened: what the last screen reported was said there, and isn't replayed here. */
    fun itemOpened() = update { copy(refused = null) }

    /** A type's or provider's page closed: what it reported was said there, and isn't replayed by the screen beneath it. */
    fun itemClosed() = itemOpened()

    /** Switches [name] on or off in [kind]'s list for [type], and saves it. */
    fun set(
        type: String,
        kind: FetcherKind,
        name: String,
        enabled: Boolean,
    ) {
        val changed = saves.shown.withFetcher(type, kind, name, enabled)
        saves.set(METADATA_OPTIONS, changed.getValue(METADATA_OPTIONS))
    }

    /** Moves [name] [by] places in [kind]'s order for [type]: -1 earlier, 1 later. */
    fun move(
        type: String,
        kind: FetcherKind,
        name: String,
        by: Int,
    ) {
        val ready = _state.value as? MetadataDefaultsUiState.Ready ?: return
        val changed = saves.shown.withFetcherMoved(type, kind, ready.offered(type, kind), name, by)
        if (changed != saves.shown) saves.set(METADATA_OPTIONS, changed.getValue(METADATA_OPTIONS))
    }

    fun retrySave() = saves.retry()

    /** A change still waiting out its delay is sent now, where this page's scope can no longer cancel it. */
    override fun onCleared() {
        saves.cleared()
    }

    private fun update(change: MetadataDefaultsUiState.Ready.() -> MetadataDefaultsUiState.Ready) =
        _state.update { (it as? MetadataDefaultsUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinMetadataDefaults"
    }
}
