package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
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

    /** The settings as shown, what the pickers offer, and the last save that failed and was put back. */
    data class Ready(
        val settings: ServerSettings,
        val branding: Branding,
        val cultures: List<Culture> = emptyList(),
        val countries: List<Country> = emptyList(),
        val editing: ServerText? = null,
        val saveFailed: ConsoleFailure? = null,
    ) : ServerSettingsUiState
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
        ConfigurationSaves(viewModelScope, connection, TAG, { _, client, json -> client.saveServerConfiguration(json) }) { shown, failure ->
            update { copy(settings = shown.serverSettings(), saveFailed = failure) }
        }

    private val brand =
        ConfigurationSaves(viewModelScope, connection, TAG, { _, client, json -> client.saveBranding(json) }) { shown, failure ->
            update { copy(branding = shown.branding(), saveFailed = failure) }
        }

    init {
        load()
    }

    fun load() {
        _state.value = ServerSettingsUiState.Loading
        viewModelScope.launch {
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

    /** Opens the dialog for [text], or closes it for null. */
    fun edit(text: ServerText?) = update { copy(editing = text) }

    /** Saves the text the dialog for [text] holds, and closes it. A blank server name isn't saved. */
    fun saveText(
        text: ServerText,
        value: String,
    ) {
        update { copy(editing = null) }
        when (text) {
            ServerText.Name -> value.trim().takeIf(String::isNotEmpty)?.let { server.set(SERVER_NAME, JsonPrimitive(it)) }
            ServerText.LoginDisclaimer -> brand.set(LOGIN_DISCLAIMER, JsonPrimitive(value.trim()))
            ServerText.CustomCss -> brand.set(CUSTOM_CSS, JsonPrimitive(value))
        }
    }

    fun setMetadataLanguage(code: String) = server.set(METADATA_LANGUAGE, JsonPrimitive(code))

    fun setMetadataCountry(code: String) = server.set(METADATA_COUNTRY, JsonPrimitive(code))

    fun setQuickConnect(on: Boolean) = server.set(QUICK_CONNECT_AVAILABLE, JsonPrimitive(on))

    fun setSplashscreen(on: Boolean) = brand.set(SPLASHSCREEN_ENABLED, JsonPrimitive(on))

    private fun update(change: ServerSettingsUiState.Ready.() -> ServerSettingsUiState.Ready) =
        _state.update { (it as? ServerSettingsUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinServerSettings"
    }
}
