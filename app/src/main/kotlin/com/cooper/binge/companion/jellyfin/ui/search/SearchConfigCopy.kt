package com.cooper.binge.companion.jellyfin.ui.search

import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.saveUserConfiguration
import com.cooper.binge.companion.jellyfin.ui.console.ConfigurationSaves
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleFailure
import com.cooper.binge.companion.jellyfin.ui.console.SaveProgress
import com.cooper.binge.companion.jellyfin.ui.console.consoleCall
import com.cooper.binge.companion.jellyfin.ui.console.consoleFailure
import com.cooper.binge.companion.jellyfin.ui.console.refusedAfter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed interface SearchConfigState {
    /** Nothing has asked for a switch yet, so nothing has been read. */
    data object Idle : SearchConfigState

    data object Loading : SearchConfigState

    data class Failed(
        val failure: ConsoleFailure,
    ) : SearchConfigState

    /** The configuration as shown; where its saves stand ([save]); and the last save the server refused, which was put back. */
    data class Ready(
        val configuration: JsonObject,
        val save: SaveProgress = SaveProgress(),
        val refused: ConsoleFailure? = null,
        /** The copy is being read again: it may be older than the server's, so no change is built on it until the read is in. */
        val reading: Boolean = false,
    ) : SearchConfigState
}

/**
 * One change to a configuration, built from the copy as it is when the change is made and again when it is put back on a newer
 * copy: a change inside a nested object (the trickplay options) is the new object, made from the copy's own. [id] names the thing
 * changed, so a second flip of the same switch replaces the first.
 */
class ConfigChange(
    val id: String,
    val apply: (JsonObject) -> Pair<String, JsonElement>,
)

/**
 * The search's one copy of one configuration the server keeps whole. Jellyfin has no partial update: a save sends the whole
 * configuration, so two copies of it would each write back what the other had saved before, silently. The copy is saved through
 * the same [ConfigurationSaves] the pages use (the same instant change, put-back on a refusal, and Retry for a change the server
 * could not be reached for) and read afresh each time the search comes to the front, so a change made on a page opened over it is
 * not written back:
 *
 * - While it is read again no change can be made ([SearchConfigState.Ready.reading]; the switches show a spinner), since the copy
 *   may be older than the server's. A change still waiting out its delay, or being written, is sent first.
 * - A change the server could not be reached for is not sent before a read (that would write the old copy over what a page
 *   saved); it is put back on top of what is read. A change a refusal put back, or one that was sent, is no longer owed.
 * - [leaving] sends what is still owed before a page opened over the search reads the configuration.
 *
 * Nothing is read until [start]: a search that shows none of its switches reads nothing.
 */
class SearchConfigCopy(
    private val connection: Connection,
    private val scope: CoroutineScope,
    private val tag: String,
    private val read: suspend (JellyfinClient) -> JsonObject,
    private val save: suspend (JellyfinSession, JellyfinClient, JsonObject) -> Unit = { session, client, json ->
        client.saveUserConfiguration(session.userId, json)
    },
) {
    private val _state = MutableStateFlow<SearchConfigState>(SearchConfigState.Idle)
    val state: StateFlow<SearchConfigState> = _state.asStateFlow()

    /** The changes the server has not accepted yet, by what they change, so a read can put them back on top of the copy it reads. */
    private val unsent = mutableMapOf<String, ConfigChange>()

    private val saves =
        ConfigurationSaves(
            connection,
            scope,
            tag,
            onProgress = { progress ->
                // What the server has accepted is no longer owed to it.
                if (progress.status == SaveStatus.Saved && !progress.failed) unsent.clear()
                _state.update { (it as? SearchConfigState.Ready)?.copy(save = progress) ?: it }
            },
            onShown = { shown, failure ->
                // A refusal puts the whole copy back to what the server accepted, so nothing is owed: a read must not send it again.
                if (failure == ConsoleFailure.Refused) unsent.clear()
                _state.update {
                    (it as? SearchConfigState.Ready)?.copy(configuration = shown, refused = refusedAfter(it.refused, failure)) ?: it
                }
            },
            save = save,
        )

    /** A read is running, so another call to [refresh] has nothing to add: one read serves every switch that asked. */
    private var reading = false

    /** Reads the configuration the first time a switch of it is shown, and not again: the route reads it afresh when it is shown. */
    fun start() {
        if (_state.value is SearchConfigState.Idle) refresh()
    }

    /**
     * Reads the configuration and shows it. A change still waiting out its delay, or still being written, is sent first, so the
     * read sees it. While it runs the copy shown may be older than the server's, so no change can be made on it, which is the only
     * way a change is never built on a copy that lacks what a page saved. A change the server could not be reached for is not sent
     * first; it is put back on top of what is read, which sends it again a moment after the read lands.
     */
    fun refresh() {
        val current = _state.value
        if (reading) return
        reading = true
        val kept = current is SearchConfigState.Ready && current.save.failed
        _state.value = (current as? SearchConfigState.Ready)?.copy(reading = true) ?: SearchConfigState.Loading
        scope.launch {
            try {
                // Sent now and answered when the server has it.
                if (!kept) {
                    val sent = unsent.toMap()
                    acceptedBy(saves.settle().await(), sent)
                }
                connection.awaitSaves()
                connection
                    .consoleCall(tag) { _, client -> read(client) }
                    .fold(
                        onSuccess = { configuration ->
                            // Before the state says it is done, so whoever sees it done may ask again.
                            reading = false
                            saves.loaded(configuration)
                            _state.update {
                                (it as? SearchConfigState.Ready)?.copy(configuration = configuration, reading = false)
                                    ?: SearchConfigState.Ready(configuration)
                            }
                            unsent.values.toList().forEach { put(it) }
                        },
                        onFailure = { error ->
                            reading = false
                            _state.update {
                                (it as? SearchConfigState.Ready)?.copy(reading = false) ?: SearchConfigState.Failed(error.consoleFailure())
                            }
                        },
                    )
            } finally {
                reading = false
            }
        }
    }

    /** Makes [change] on the copy, unless the copy is not ready or is being read again. */
    fun change(change: ConfigChange) {
        val ready = _state.value as? SearchConfigState.Ready ?: return
        if (ready.reading) return
        unsent[change.id] = change
        put(change)
    }

    private fun put(change: ConfigChange) {
        val (key, value) = change.apply(saves.shown)
        saves.set(key, value)
    }

    /**
     * A page is about to open over the search, and will read the configuration. A change still waiting out its delay is sent now and
     * counted with the saves [Connection.awaitSaves] waits for, so the page reads it: otherwise the page would read the server
     * without it, and its next save would send that copy back over the change. Nothing happens for a copy nothing has read.
     */
    fun leaving() {
        if (_state.value !is SearchConfigState.Ready) return
        val sent = unsent.toMap()
        val owed = saves.settle()
        connection.launchSave { owed.await() }
        scope.launch { acceptedBy(owed.await(), sent) }
    }

    /**
     * What a `settle()` answered: nothing kept means the server has accepted what it sent (a refusal is told through the callback,
     * which clears [unsent] too), so those changes are no longer owed. Only the ones sent: a change made since is still owed.
     * Without this a read would put the old change back on top of what a page saved after it.
     */
    private fun acceptedBy(
        kept: JsonObject?,
        sent: Map<String, ConfigChange>,
    ) {
        if (kept != null) return
        sent.forEach { (id, change) -> if (unsent[id] === change) unsent.remove(id) }
    }

    /** Not while the copy is being read again: Retry would send the old copy over what a page saved, and the read puts the change back anyway. */
    fun retry() {
        if ((_state.value as? SearchConfigState.Ready)?.reading == true) return
        saves.retry()
    }

    /** The search is gone: what is still owed is sent now, past the search's own scope. */
    fun cleared() = saves.cleared()
}
