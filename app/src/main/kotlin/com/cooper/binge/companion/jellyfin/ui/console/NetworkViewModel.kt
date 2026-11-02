package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.NETWORK_BASE_URL
import com.cooper.binge.companion.jellyfin.server.NETWORK_BIND_ADDRESSES
import com.cooper.binge.companion.jellyfin.server.NETWORK_CERTIFICATE_PASSWORD
import com.cooper.binge.companion.jellyfin.server.NETWORK_CERTIFICATE_PATH
import com.cooper.binge.companion.jellyfin.server.NETWORK_KNOWN_PROXIES
import com.cooper.binge.companion.jellyfin.server.NETWORK_LOCAL_SUBNETS
import com.cooper.binge.companion.jellyfin.server.NETWORK_PUBLISHED_BY_SUBNET
import com.cooper.binge.companion.jellyfin.server.NETWORK_REMOTE_IP_FILTER
import com.cooper.binge.companion.jellyfin.server.NETWORK_VIRTUAL_NAMES
import com.cooper.binge.companion.jellyfin.server.NetworkImpact
import com.cooper.binge.companion.jellyfin.server.NetworkKeys
import com.cooper.binge.companion.jellyfin.server.NetworkSettings
import com.cooper.binge.companion.jellyfin.server.networkConfiguration
import com.cooper.binge.companion.jellyfin.server.networkImpacts
import com.cooper.binge.companion.jellyfin.server.networkKeys
import com.cooper.binge.companion.jellyfin.server.networkSettings
import com.cooper.binge.companion.jellyfin.server.normalBaseUrl
import com.cooper.binge.companion.jellyfin.server.saveNetworkConfiguration
import com.cooper.binge.companion.jellyfin.server.saveServerConfiguration
import com.cooper.binge.companion.jellyfin.server.serverConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A port on the page, whose number may be any a computer has. */
enum class NetworkNumber {
    HttpPort,
    HttpsPort,
    PublicHttpPort,
    PublicHttpsPort,
    ;

    fun key(keys: NetworkKeys): String =
        when (this) {
            HttpPort -> keys.httpPort
            HttpsPort -> keys.httpsPort
            PublicHttpPort -> keys.publicHttpPort
            PublicHttpsPort -> keys.publicHttpsPort
        }

    /** [text] as a port, or null if it isn't one. */
    fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT }
}

/** A text that is not a number or a list. */
enum class NetworkText(
    val key: String,
) {
    BaseUrl(NETWORK_BASE_URL),
    CertificatePath(NETWORK_CERTIFICATE_PATH),

    /** A new certificate password, typed. Never shown back, and kept exactly as typed. */
    CertificatePassword(NETWORK_CERTIFICATE_PASSWORD),
}

/**
 * A list on the page, one entry per line. A list of [form] options is part of the page's form, saved with its Save button,
 * because a wrong value can lock every client out; the others save as they're made.
 */
enum class NetworkList(
    val key: String,
    val form: Boolean,
) {
    BindAddresses(NETWORK_BIND_ADDRESSES, true),
    RemoteIpFilter(NETWORK_REMOTE_IP_FILTER, true),
    KnownProxies(NETWORK_KNOWN_PROXIES, false),
    LocalSubnets(NETWORK_LOCAL_SUBNETS, false),
    PublishedBySubnet(NETWORK_PUBLISHED_BY_SUBNET, false),
    VirtualNames(NETWORK_VIRTUAL_NAMES, false),
}

/** The sheet open over the page. */
sealed interface NetworkSheet {
    data class Number(
        val field: NetworkNumber,
    ) : NetworkSheet

    data class Text(
        val field: NetworkText,
    ) : NetworkSheet

    data class Items(
        val field: NetworkList,
    ) : NetworkSheet
}

sealed interface NetworkUiState {
    data object Loading : NetworkUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : NetworkUiState

    /**
     * The options as shown: what the server has, with the page's own unsaved changes ([draft], the keys). Those are the ones
     * that can lock a client out, and are saved together by Save changes, behind a confirm when they would reach the address
     * this app uses ([confirming]). The rest save as they're made ([save], [refused]). [restartNeeded]: a change was saved,
     * and the server applies it when it restarts.
     */
    data class Ready(
        val settings: NetworkSettings,
        val keys: NetworkKeys,
        val draft: Set<String> = emptySet(),
        val sheet: NetworkSheet? = null,
        val confirming: Set<NetworkImpact>? = null,
        val committing: Boolean = false,
        val formFailed: ConsoleFailure? = null,
        val restartNeeded: Boolean = false,
        val reconnect: Boolean = false,
        val save: SaveProgress = SaveProgress(),
        val refused: ConsoleFailure? = null,
    ) : NetworkUiState
}

/**
 * The page's unsaved changes, over the configuration the server last accepted. The changes are by their key in the network
 * configuration, [NETWORK_BASE_URL] included, which a release from 10.9 keeps in the server's own configuration ([server]).
 */
internal class NetworkDraft(
    private val names: NetworkKeys,
) {
    /** The server's own configuration, read for the base URL it keeps from 10.9. */
    var server = JsonObject(emptyMap())

    var changes = emptyMap<String, JsonElement>()
        private set

    /** The settings as shown: [saved] (what the server has), with the changes over it. */
    fun shown(saved: JsonObject): NetworkSettings = network(saved).networkSettings(names, baseUrl(changes))

    /** The settings as the server has them: [saved] alone. */
    fun settings(saved: JsonObject): NetworkSettings = saved.networkSettings(names, baseUrl(emptyMap()))

    private fun baseUrl(over: Map<String, JsonElement>): String? =
        if (names.baseUrlInServer) ((over[NETWORK_BASE_URL] ?: server[NETWORK_BASE_URL]) as? JsonPrimitive)?.content.orEmpty() else null

    /** Keeps [value] for [key] until Save changes, or drops it if [saved] already has it. */
    fun put(
        key: String,
        value: JsonElement,
        saved: JsonObject,
    ) {
        val has = if (key == NETWORK_BASE_URL && names.baseUrlInServer) server[key] else saved[key]
        changes = if (has == value) changes - key else changes + (key to value)
    }

    /** The network configuration to write: [saved] with the changes over it, the base URL aside when the server keeps it elsewhere. */
    fun network(saved: JsonObject): JsonObject = JsonObject(saved + networkChanges())

    /** A new certificate password with HTTPS on is a change that can leave the server unable to serve it. */
    fun passwordImpact(saved: JsonObject): Set<NetworkImpact> =
        if (NETWORK_CERTIFICATE_PASSWORD in changes && shown(saved).enableHttps) setOf(NetworkImpact.Certificate) else emptySet()

    fun hasNetworkChanges(): Boolean = networkChanges().isNotEmpty()

    /** The changes that go in the network configuration: all of them, but the base URL when the server keeps that elsewhere. */
    private fun networkChanges(): Map<String, JsonElement> = if (names.baseUrlInServer) changes - NETWORK_BASE_URL else changes

    /** The server's configuration with the new base URL, or null if there is none to write. */
    fun serverWithBaseUrl(): JsonObject? =
        changes[NETWORK_BASE_URL]?.takeIf { names.baseUrlInServer }?.let {
            JsonObject(
                server + (NETWORK_BASE_URL to it),
            )
        }

    fun clear() {
        changes = emptyMap()
    }

    /** The network changes were saved: they are the server's now. */
    fun networkSaved() {
        changes = changes - networkChanges().keys
    }

    /** The base URL was saved into [configuration]. */
    fun serverSaved(configuration: JsonObject) {
        server = configuration
        changes = changes - NETWORK_BASE_URL
    }
}

/**
 * The server's network options. Most are risky: a wrong port, address or HTTPS setting leaves every client, this app too,
 * unable to reach the server until someone changes it on the server itself. So those are edited on the page, and written
 * only when the person presses Save changes, behind a confirm that names how the change reaches the address this app is
 * signed in to. The lists that can't lock anyone out (proxies, subnets, discovery) save as they're made.
 *
 * The configuration is kept whole: the certificate password, which the server sends back with the rest, goes back as it came
 * and is never shown. The page never restarts the server; it says a restart is needed and opens the Server page.
 */
class NetworkViewModel(
    private val connection: Connection,
    profile: JellyfinServerProfile,
) : ViewModel() {
    private val names = networkKeys(profile)
    private val unsaved = NetworkDraft(names)

    /** Whether the changes being saved can cut this app off, so the page says to reconnect after the restart. */
    private var cutOff = false

    private val _state = MutableStateFlow<NetworkUiState>(NetworkUiState.Loading)
    val state: StateFlow<NetworkUiState> = _state.asStateFlow()

    private val saves: ConfigurationSaves =
        ConfigurationSaves(
            connection,
            viewModelScope,
            TAG,
            { _, client, json -> client.saveNetworkConfiguration(json) },
            onProgress = { progress -> update { copy(save = progress) } },
        ) { _, failure ->
            update { copy(settings = unsaved.shown(saves.shown), refused = failure?.takeIf { it == ConsoleFailure.Refused }) }
        }

    init {
        load()
    }

    fun load() {
        _state.value = NetworkUiState.Loading
        viewModelScope.launch {
            connection.awaitSaves()
            unsaved.clear()
            _state.value =
                connection
                    .consoleCall(TAG) { _, client ->
                        val network = client.networkConfiguration()
                        network to if (names.baseUrlInServer) client.serverConfiguration() else JsonObject(emptyMap())
                    }.fold(
                        onSuccess = { (network, configuration) ->
                            saves.loaded(network)
                            unsaved.server = configuration
                            NetworkUiState.Ready(unsaved.shown(network), names)
                        },
                        onFailure = { NetworkUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    fun sheet(sheet: NetworkSheet?) = update { copy(sheet = sheet) }

    fun saveNumber(
        field: NetworkNumber,
        text: String,
    ) {
        val port = field.parse(text) ?: return
        update { copy(sheet = null) }
        drafted(field.key(names), JsonPrimitive(port))
    }

    fun saveText(
        field: NetworkText,
        text: String,
    ) {
        update { copy(sheet = null) }
        val value =
            when (field) {
                NetworkText.BaseUrl -> normalBaseUrl(text)
                NetworkText.CertificatePath -> text.trim()
                NetworkText.CertificatePassword -> text
            }
        drafted(field.key, JsonPrimitive(value))
    }

    fun saveItems(
        field: NetworkList,
        text: String,
    ) {
        val value = JsonArray(parseHosts(text).map(::JsonPrimitive))
        update { copy(sheet = null) }
        if (field.form) drafted(field.key, value) else saves.set(field.key, value)
    }

    /** A switch. One of the page's form, whose wrong value can lock a client out, waits for Save changes; the others save as made. */
    fun setSwitch(
        key: String,
        on: Boolean,
        form: Boolean,
    ) = if (form) drafted(key, JsonPrimitive(on)) else saves.set(key, JsonPrimitive(on))

    private fun drafted(
        key: String,
        value: JsonElement,
    ) {
        unsaved.put(key, value, saves.shown)
        update { copy(settings = unsaved.shown(saves.shown), draft = unsaved.changes.keys, formFailed = null) }
    }

    /** Save changes: straight through if it doesn't reach this app's address, else after the person has confirmed how it does. */
    fun save() {
        cutOff = false
        viewModelScope.launch {
            val appUrl = connection.signedIn()?.first?.baseUrl
            val impacts =
                appUrl?.let { networkImpacts(it, unsaved.settings(saves.shown), unsaved.shown(saves.shown)) }.orEmpty() +
                    unsaved.passwordImpact(saves.shown)
            if (impacts.isEmpty()) commit() else update { copy(confirming = impacts) }
        }
    }

    fun dismissConfirm() = update { copy(confirming = null) }

    /** Saves after the person confirmed. A change that can cut this app off says to reconnect once the server restarts. */
    fun confirm() {
        cutOff = (_state.value as? NetworkUiState.Ready)?.confirming?.any { it.severe } == true
        update { copy(confirming = null) }
        commit()
    }

    /**
     * Writes the changes. Whole configurations go back, with only these keys changed, so the certificate password and any
     * option this app doesn't know go back as they came. It runs on the connection's scope, so leaving the page doesn't cancel it.
     */
    private fun commit() {
        val network = unsaved.network(saves.shown)
        val configuration = unsaved.serverWithBaseUrl()
        val writeNetwork = unsaved.hasNetworkChanges()
        update { copy(committing = true, formFailed = null) }
        connection.launchSave {
            val result =
                attempt {
                    if (writeNetwork) connection.consoleCall(TAG) { _, client -> client.saveNetworkConfiguration(network) }.getOrThrow()
                    if (writeNetwork) viewModelScope.launch { adopt(network, null) }
                    if (configuration != null) {
                        connection.consoleCall(TAG) { _, client -> client.saveServerConfiguration(configuration) }.getOrThrow()
                    }
                }
            viewModelScope.launch {
                result
                    .onSuccess { adopt(null, configuration) }
                    .onFailure { error -> update { copy(committing = false, formFailed = error.consoleFailure()) } }
            }
        }
    }

    /** What was saved is the server's now: its changes are no longer changes, and the server needs a restart to apply them. */
    private fun adopt(
        network: JsonObject?,
        configuration: JsonObject?,
    ) {
        network?.let {
            saves.loaded(it)
            unsaved.networkSaved()
        }
        configuration?.let(unsaved::serverSaved)
        update {
            copy(
                settings = unsaved.shown(saves.shown),
                draft = unsaved.changes.keys,
                committing = network != null && committing,
                restartNeeded = true,
                reconnect = cutOff,
            )
        }
    }

    fun retrySave() = saves.retry()

    /** A change still waiting out its delay is sent now, where this page's scope can no longer cancel it. */
    override fun onCleared() {
        saves.cleared()
    }

    private fun update(change: NetworkUiState.Ready.() -> NetworkUiState.Ready) =
        _state.update { (it as? NetworkUiState.Ready)?.change() ?: it }

    internal companion object {
        const val TAG = "JellyfinNetwork"
    }
}

private const val MIN_PORT = 1
private const val MAX_PORT = 65535
