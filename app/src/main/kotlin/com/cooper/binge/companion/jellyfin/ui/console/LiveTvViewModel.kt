package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LiveTvSummary
import com.cooper.binge.companion.jellyfin.server.TunerHostSummary
import com.cooper.binge.companion.jellyfin.server.deleteTunerHost
import com.cooper.binge.companion.jellyfin.server.discoverTuners
import com.cooper.binge.companion.jellyfin.server.liveTvRead
import com.cooper.binge.companion.jellyfin.server.saveTunerHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

sealed interface LiveTvUiState {
    data object Loading : LiveTvUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : LiveTvUiState

    /**
     * The page's [summary], the [sheet] open over it, whether a change is in flight ([saving]), the tuner the user asked to
     * remove and has not confirmed ([removing]), and the last change that failed ([problem]).
     */
    data class Ready(
        val summary: LiveTvSummary,
        val sheet: TunerSheet? = null,
        val saving: Boolean = false,
        val removing: TunerHostSummary? = null,
        val problem: TunerProblem? = null,
    ) : LiveTvUiState
}

/**
 * The server's Live TV setup: its tuner hosts and guide data providers, and when the guide ends, with the tuners added, edited and
 * removed here. [withGuide] is whether the user has Live TV access, which the server's guide lookup needs on top of the management
 * flag that opens this page. The hosts are kept as the server sent them, so an edit sends back a key this app does not show.
 */
class LiveTvViewModel(
    private val connection: Connection,
    private val withGuide: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow<LiveTvUiState>(LiveTvUiState.Loading)
    val state: StateFlow<LiveTvUiState> = _state.asStateFlow()

    private var hosts = emptyMap<String, JsonObject>()
    private var found = emptyMap<String, JsonObject>()

    init {
        load()
    }

    fun load() {
        _state.value = LiveTvUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.liveTvRead(withGuide) }
                    .fold(
                        onSuccess = { read ->
                            hosts = read.hosts
                            LiveTvUiState.Ready(read.summary)
                        },
                        onFailure = { LiveTvUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /** Reads the setup again without the page's skeleton, as a change lands. */
    private suspend fun reread() {
        connection.consoleCall(TAG) { _, client -> client.liveTvRead(withGuide) }.onSuccess { read ->
            hosts = read.hosts
            update { copy(summary = read.summary) }
        }
    }

    fun sheet(sheet: TunerSheet?) = update { copy(sheet = sheet, problem = null) }

    /** Opens the form for [draft]: a new tuner of a kind, or one the server found. */
    fun form(draft: TunerDraft) = update { copy(sheet = TunerSheet.Form(draft), problem = null) }

    /** Opens the form for the tuner [id]. */
    fun edit(id: String) {
        val host = hosts[id] ?: return
        update { copy(sheet = TunerSheet.Form(TunerDraft.of(host))) }
    }

    /** Asks the server for the tuners it finds on the network that it does not have yet. */
    fun discover() {
        update { copy(sheet = TunerSheet.Discovering, problem = null) }
        viewModelScope.launch {
            connection.consoleCall(TAG) { _, client -> client.discoverTuners() }.fold(
                onSuccess = { devices ->
                    found =
                        devices
                            .mapNotNull { host ->
                                TunerDraft
                                    .of(host)
                                    .url
                                    .takeIf { it.isNotBlank() }
                                    ?.let { it to host }
                            }.toMap()
                    update { copy(sheet = TunerSheet.Found(found.values.map { TunerDraft.of(it).copy(id = null) })) }
                },
                onFailure = { update { copy(sheet = TunerSheet.Choose, problem = TunerProblem.DiscoverFailed) } },
            )
        }
    }

    /** Saves [draft]: a new tuner when it has no id, else the one it names, with the keys the form does not show as they were. */
    fun save(draft: TunerDraft) {
        if (!draft.valid || (_state.value as? LiveTvUiState.Ready)?.saving != false) return
        val base = draft.id?.let { hosts[it] } ?: found[draft.url.trim()]
        update { copy(saving = true, problem = null) }
        viewModelScope.launch {
            connection.consoleCall(TAG) { _, client -> connection.tracked { client.saveTunerHost(draft.applyTo(base)) } }.fold(
                onSuccess = {
                    reread()
                    update { copy(saving = false, sheet = null) }
                },
                onFailure = {
                    update { copy(saving = false, problem = if (draft.id == null) TunerProblem.NotAdded else TunerProblem.NotChanged) }
                },
            )
        }
    }

    fun askRemove(id: String) {
        val tuner = (_state.value as? LiveTvUiState.Ready)?.summary?.tuners?.firstOrNull { it.id == id } ?: return
        update { copy(removing = tuner) }
    }

    fun cancelRemove() = update { copy(removing = null) }

    /** Removes the tuner the user confirmed removing. */
    fun remove() {
        val tuner = (_state.value as? LiveTvUiState.Ready)?.removing ?: return
        update { copy(removing = null, saving = true, problem = null) }
        viewModelScope.launch {
            connection.consoleCall(TAG) { _, client -> connection.tracked { client.deleteTunerHost(tuner.id) } }.fold(
                onSuccess = {
                    reread()
                    update { copy(saving = false, sheet = null) }
                },
                onFailure = { update { copy(saving = false, problem = TunerProblem.NotChanged) } },
            )
        }
    }

    fun dismissProblem() = update { copy(problem = null) }

    private fun update(change: LiveTvUiState.Ready.() -> LiveTvUiState.Ready) =
        _state.update { (it as? LiveTvUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinLiveTv"
    }
}
