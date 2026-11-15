package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.NFO_RELEASE_DATE_FORMAT
import com.cooper.binge.companion.jellyfin.server.NFO_USER_ID
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.server.nfoConfiguration
import com.cooper.binge.companion.jellyfin.server.saveNfoConfiguration
import com.cooper.binge.companion.jellyfin.server.users
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The sheet open over the page. */
enum class NfoSheet { User, DateFormat }

sealed interface NfoUiState {
    data object Loading : NfoUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : NfoUiState

    /**
     * The NFO configuration as shown, the server's users to pick from (null when they could not be read: the page still works, and
     * the picker says so), the sheet open, where the saves stand, and the last save the server refused ([refused]), which was put back.
     */
    data class Ready(
        val config: JsonObject,
        val users: List<ServerUser>? = null,
        val sheet: NfoSheet? = null,
        val save: SaveProgress = SaveProgress(),
        val refused: ConsoleFailure? = null,
    ) : NfoUiState
}

/** The id of the user whose watch state goes into NFO files, or null for none. */
fun JsonObject.nfoUserId(): String? = (this[NFO_USER_ID] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.takeIf { it.isNotBlank() }

/**
 * The server's NFO options, each saved as it's made (see [ConfigurationSaves]) with the whole configuration sent back. The users the
 * watch state can come from are a nicety, read beside the configuration: the page works without them.
 */
class NfoViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<NfoUiState>(NfoUiState.Loading)
    val state: StateFlow<NfoUiState> = _state.asStateFlow()

    private val saves =
        ConfigurationSaves(
            connection,
            viewModelScope,
            TAG,
            { _, client, json -> client.saveNfoConfiguration(json) },
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
        _state.value = NfoUiState.Loading
        viewModelScope.launch {
            connection.awaitSaves()
            _state.value =
                connection
                    .consoleCall(TAG) { _, client ->
                        coroutineScope {
                            val users = async { attempt { client.users() }.getOrNull() }
                            client.nfoConfiguration() to users.await()
                        }
                    }.fold(
                        onSuccess = { (config, users) ->
                            saves.loaded(config)
                            NfoUiState.Ready(config, users)
                        },
                        onFailure = { NfoUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /** Changes one setting and saves it. */
    fun set(
        key: String,
        value: JsonElement,
    ) = saves.set(key, value)

    /** Saves [id] as the user whose watch state is written, and closes the picker; null is none. */
    fun setUser(id: String?) {
        saves.set(NFO_USER_ID, id?.let(::JsonPrimitive) ?: JsonNull)
        sheet(null)
    }

    /** Saves the date format and closes its sheet. A blank format is not sent: the server would write dates with no format. */
    fun setDateFormat(format: String) {
        val trimmed = format.trim()
        if (trimmed.isEmpty()) return
        saves.set(NFO_RELEASE_DATE_FORMAT, JsonPrimitive(trimmed))
        sheet(null)
    }

    /** Sends the change a failed write left unsent. */
    fun retrySave() = saves.retry()

    /** Opens [sheet], or closes whichever is open for null. */
    fun sheet(sheet: NfoSheet?) = update { copy(sheet = sheet) }

    private fun update(change: NfoUiState.Ready.() -> NfoUiState.Ready) = _state.update { (it as? NfoUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinNfo"
    }
}
