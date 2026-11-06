package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.server.Branding
import com.cooper.binge.companion.jellyfin.server.CUSTOM_CSS
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.Country
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.LOGIN_DISCLAIMER
import com.cooper.binge.companion.jellyfin.server.METADATA_COUNTRY
import com.cooper.binge.companion.jellyfin.server.METADATA_LANGUAGE
import com.cooper.binge.companion.jellyfin.server.QUICK_CONNECT_AVAILABLE
import com.cooper.binge.companion.jellyfin.server.SERVER_NAME
import com.cooper.binge.companion.jellyfin.server.SPLASHSCREEN_ENABLED
import com.cooper.binge.companion.jellyfin.server.SPLASHSCREEN_LOCATION
import com.cooper.binge.companion.jellyfin.server.ServerSettings
import com.cooper.binge.companion.jellyfin.server.branding
import com.cooper.binge.companion.jellyfin.server.brandingConfiguration
import com.cooper.binge.companion.jellyfin.server.countries
import com.cooper.binge.companion.jellyfin.server.cultures
import com.cooper.binge.companion.jellyfin.server.saveBranding
import com.cooper.binge.companion.jellyfin.server.saveServerConfiguration
import com.cooper.binge.companion.jellyfin.server.serverConfiguration
import com.cooper.binge.companion.jellyfin.server.serverSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/** A text setting edited in a dialog, rather than saved a keystroke at a time. */
enum class ServerText { Name, LoginDisclaimer, CustomCss }

/** The picker open over the page. */
enum class ServerPicker { MetadataLanguage, MetadataCountry }

sealed interface ServerSettingsUiState {
    data object Loading : ServerSettingsUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : ServerSettingsUiState

    /**
     * The settings as shown, what the pickers offer, and the last save that failed ([saveFailed]). [refused] keeps a page
     * refusal until the next edit, even if the other writer cannot reach the server. A text sheet that is
     * [submitting] stays open while its save runs; if the server refuses it, [sheetFailed] says why in the sheet.
     */
    data class Ready(
        val settings: ServerSettings,
        val branding: Branding,
        val cultures: List<Culture> = emptyList(),
        val countries: List<Country> = emptyList(),
        val editing: ServerText? = null,
        val saveFailed: ConsoleFailure? = null,
        val refused: ConsoleFailure? = null,
        val submitting: ServerText? = null,
        val sheetFailed: ConsoleFailure? = null,
        val serverSave: SaveProgress = SaveProgress(),
        val brandSave: SaveProgress = SaveProgress(),
    ) : ServerSettingsUiState {
        /** Where the page's saves stand, the two writers' together: failed if either is, busy if either is. */
        val save: SaveProgress get() = combined(serverSave, brandSave)
    }
}

/**
 * The server's general settings and its login branding, each saved as it's made (see
 * [ConfigurationSaves]); a text setting saves when its dialog does. The language and country pickers
 * are niceties: if their lists can't be read, the rest of the page still works.
 */
class ServerSettingsViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<ServerSettingsUiState>(ServerSettingsUiState.Loading)
    val state: StateFlow<ServerSettingsUiState> = _state.asStateFlow()

    private val server =
        ConfigurationSaves(
            connection,
            viewModelScope,
            TAG,
            { _, client, json -> client.saveServerConfiguration(json) },
            onProgress = { progress ->
                update { copy(serverSave = progress) }
                sheetSaved(progress, SERVER_TEXTS)
            },
        ) { shown, failure ->
            update { copy(settings = shown.serverSettings()).routed(failure, SERVER_TEXTS) }
        }

    private val brand =
        ConfigurationSaves(
            connection,
            viewModelScope,
            TAG,
            { _, client, json -> client.saveBranding(json) },
            onProgress = { progress ->
                update { copy(brandSave = progress) }
                sheetSaved(progress, BRANDING_TEXTS)
            },
        ) { shown, failure ->
            update { copy(branding = shown.branding()).routed(failure, BRANDING_TEXTS) }
        }

    /** The login splash image's sheet, which reads the branding configuration again after it changes the image. */
    internal val splashImage =
        SplashImageController(
            connection,
            viewModelScope,
            adoptBranding = { json ->
                // Only the image's location: the rest of the page's copy, and what it has waiting, stays.
                brand.adoptKey(SPLASHSCREEN_LOCATION, json[SPLASHSCREEN_LOCATION])
                update { copy(branding = brand.shown.branding()) }
            },
        )

    init {
        load()
    }

    fun load() {
        _state.value = ServerSettingsUiState.Loading
        viewModelScope.launch {
            connection.awaitSaves()
            _state.value =
                connection
                    .consoleCall(TAG) { _, client ->
                        coroutineScope {
                            val cultures = async { attempt { client.cultures() }.getOrDefault(emptyList()) }
                            val countries = async { attempt { client.countries() }.getOrDefault(emptyList()) }
                            val branding = async { client.brandingConfiguration() }
                            val configuration = client.serverConfiguration()
                            server.loaded(configuration)
                            brand.loaded(branding.await())
                            ServerSettingsUiState.Ready(
                                configuration.serverSettings(),
                                branding.await().branding(),
                                cultures.await().sortedBy { it.displayName },
                                countries.await().sortedBy { it.displayName },
                            )
                        }
                    }.getOrElse { ServerSettingsUiState.Failed(it.consoleFailure()) }
        }
    }

    /** Opens the sheet for [text], or closes it for null. A save the closed sheet was waiting on reports on the page. */
    fun edit(text: ServerText?) = update { copy(editing = text, submitting = null, sheetFailed = null) }

    /**
     * Saves the text the sheet for [text] holds. The sheet stays open while the save runs: it closes when the server
     * takes it, stays open with what was typed and says why if the server refuses it, and closes if the server can't be
     * reached, the change then waiting unsent on the page. A text that is unchanged, or a blank server name, closes it.
     */
    fun saveText(
        text: ServerText,
        value: String,
    ) {
        val (saves, key, json) =
            when (text) {
                ServerText.Name -> Triple(server, SERVER_NAME, JsonPrimitive(value.trim()))
                ServerText.LoginDisclaimer -> Triple(brand, LOGIN_DISCLAIMER, JsonPrimitive(value.trim()))
                ServerText.CustomCss -> Triple(brand, CUSTOM_CSS, JsonPrimitive(value))
            }
        val unsendable = text == ServerText.Name && json.content.isEmpty()
        if (unsendable || saves.shown[key] == json) {
            update { copy(editing = null) }
            return
        }
        update { copy(submitting = text, sheetFailed = null, saveFailed = null, refused = null) }
        saves.set(key, json)
    }

    /** A save of one of [texts] landed: the sheet it came from, if still open and waiting, closes. */
    private fun sheetSaved(
        progress: SaveProgress,
        texts: Set<ServerText>,
    ) {
        if (progress.status == SaveStatus.Saved) update { if (submitting in texts) copy(submitting = null, editing = null) else this }
    }

    /**
     * Where [failure] shows. A refusal of what an open sheet submitted shows in the sheet, which keeps what was typed;
     * an unreachable server closes it, the change unsent. Anything else, or a sheet already dismissed, shows on the page.
     */
    private fun ServerSettingsUiState.Ready.routed(
        failure: ConsoleFailure?,
        texts: Set<ServerText>,
    ): ServerSettingsUiState.Ready {
        val waiting = submitting != null && submitting in texts
        return when {
            waiting && editing != null && failure == ConsoleFailure.Refused ->
                copy(submitting = null, sheetFailed = failure, saveFailed = null)
            waiting && failure == ConsoleFailure.Unreachable ->
                copy(submitting = null, editing = null, saveFailed = failure, refused = refusedAfter(refused, failure))
            else -> copy(saveFailed = failure, refused = refusedAfter(refused, failure))
        }
    }

    fun setMetadataLanguage(code: String) = server.set(METADATA_LANGUAGE, JsonPrimitive(code))

    fun setMetadataCountry(code: String) = server.set(METADATA_COUNTRY, JsonPrimitive(code))

    fun setQuickConnect(on: Boolean) = server.set(QUICK_CONNECT_AVAILABLE, JsonPrimitive(on))

    fun setSplashscreen(on: Boolean) = brand.set(SPLASHSCREEN_ENABLED, JsonPrimitive(on))

    /** Sends the changes a failed write left unsent. */
    fun retrySave() {
        server.retry()
        brand.retry()
    }

    /** A change still waiting out its delay is sent now, where this page's scope can no longer cancel it. */
    override fun onCleared() {
        server.cleared()
        brand.cleared()
    }

    private fun update(change: ServerSettingsUiState.Ready.() -> ServerSettingsUiState.Ready) =
        _state.update { (it as? ServerSettingsUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinServerSettings"
        val SERVER_TEXTS = setOf(ServerText.Name)
        val BRANDING_TEXTS = setOf(ServerText.LoginDisclaimer, ServerText.CustomCss)
    }
}
