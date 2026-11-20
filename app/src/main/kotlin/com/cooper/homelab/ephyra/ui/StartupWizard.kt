package com.cooper.homelab.ephyra.ui

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.Country
import com.cooper.homelab.ephyra.server.Culture
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.server.SignedIn
import com.cooper.homelab.ephyra.server.StartupConfiguration
import com.cooper.homelab.ephyra.server.UiLanguage
import com.cooper.homelab.ephyra.server.countries
import com.cooper.homelab.ephyra.server.cultures
import com.cooper.homelab.ephyra.server.noted
import com.cooper.homelab.ephyra.server.saveStartupConfiguration
import com.cooper.homelab.ephyra.server.startupConfiguration
import com.cooper.homelab.ephyra.server.uiLanguages
import com.cooper.homelab.ephyra.ui.console.ConsoleFailure
import com.cooper.homelab.ephyra.ui.console.consoleFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** What the setup wizard is showing. */
sealed interface StartupPhase {
    /** Reading what the server holds and the lists a choice is made from. */
    data object Loading : StartupPhase

    /** The read failed: the server's refusal, or no answer. It stays an error screen with Try again. */
    data class Failed(
        val failure: ConsoleFailure,
    ) : StartupPhase

    /** The first step: [configuration] is what the server held, with the user's changes on it, and not yet saved. */
    data class Configuring(
        val configuration: StartupConfiguration,
        val uiLanguages: List<UiLanguage>,
        val cultures: List<Culture>,
        val countries: List<Country>,
        val busy: Boolean = false,
        val error: SetupError? = null,
    ) : StartupPhase

    /**
     * The second step: the first account, the server's administrator. The password lives here, in memory, from the moment it is typed
     * until it is sent or the wizard is left. It is never saved (no saved state), never logged, and [toString] leaves it out.
     */
    data class Account(
        val name: String = "",
        val password: String = "",
        val confirmation: String = "",
        val busy: Boolean = false,
        val error: SetupError? = null,
        /** The server's own words for refusing the account, when it gave any: a plain message, shown in place of [error]'s. */
        val reason: String? = null,
    ) : StartupPhase {
        /** The two passwords differ, so the account cannot be sent yet. */
        val mismatch: Boolean get() = confirmation.isNotEmpty() && password != confirmation

        /** The server will not make an account without a password, so one is needed, typed twice. */
        val canSave: Boolean get() = name.isNotBlank() && password.isNotEmpty() && password == confirmation && !busy

        override fun toString() = "Account(name=$name, busy=$busy, error=$error)"
    }

    /**
     * The last step: remote access, then Finish setup, which completes the server and signs in as the account just made. [name] and
     * [password] are that account, held in memory only for the sign-in: never in saved state, never logged, left out of [toString], and
     * gone when the wizard is left or they are used.
     */
    data class Finish(
        val name: String,
        val password: String,
        val remoteAccess: Boolean = true,
        val portMapping: Boolean = false,
        val busy: Boolean = false,
        val error: SetupError? = null,
    ) : StartupPhase {
        override fun toString() = "Finish(name=$name, remoteAccess=$remoteAccess, portMapping=$portMapping, busy=$busy, error=$error)"
    }
}

/** What the setup wizard's screens can ask of it. */
data class StartupActions(
    val onOpen: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onUiLanguage: (String) -> Unit = {},
    val onMetadataLanguage: (String) -> Unit = {},
    val onMetadataCountry: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onAccountName: (String) -> Unit = {},
    val onPassword: (String) -> Unit = {},
    val onConfirmation: (String) -> Unit = {},
    val onSaveAccount: () -> Unit = {},
    val onRemoteAccess: (Boolean) -> Unit = {},
    val onPortMapping: (Boolean) -> Unit = {},
    val onFinish: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * Drives the setup wizard on the app's one state flow. Every call is anonymous: a server that is not set up has no account, and
 * these routes answer without one until setup is complete. Nothing is sent until the user taps Save.
 */
class StartupWizard internal constructor(
    private val connection: Connection,
    private val state: MutableStateFlow<UiState.Setup>,
    private val scope: CoroutineScope,
    private val localNetwork: LocalNetworkPermission,
    /** The wizard was left for the notice it was opened from: the sign-in step shows again as it was. */
    private val onBack: () -> Unit,
    signInByHand: (name: String, baseUrl: String) -> Unit,
    signIn: suspend (HttpUrl, PublicServerInfo, SignedIn) -> Unit,
) {
    /** The first account: its fields and its save. */
    val account = StartupAccountStep(connection, state, scope, localNetwork)

    /** Remote access, then completing the server and signing in as the account. */
    val finish = StartupFinishStep(connection, state, scope, localNetwork, signIn, signInByHand)

    /** Opens the wizard from the notice, and reads what it needs. */
    fun open() {
        val current = state.value as? UiState.SignIn ?: return
        if (!current.server.setupIncomplete) return
        state.value = UiState.Startup(current.baseUrl, current.server)
        load()
    }

    /** Reads the setup configuration and the lists again. */
    fun load() {
        val current = state.value as? UiState.Startup ?: return
        val url = JellyfinClient.parseBaseUrl(current.baseUrl) ?: return
        update { copy(phase = StartupPhase.Loading) }
        scope.launch {
            val read = attempt { connection.anonymous(url).readStartup() }
            read.exceptionOrNull()?.let { connection.logger.noted(TAG, "setup read failed", it) }
            update {
                copy(phase = read.fold(onSuccess = { it }, onFailure = { StartupPhase.Failed(it.consoleFailure()) }))
            }
        }
    }

    fun onName(name: String) = edit { copy(configuration = configuration.withServerName(name), error = null) }

    fun onUiLanguage(value: String) = edit { copy(configuration = configuration.withUiCulture(value), error = null) }

    fun onMetadataLanguage(value: String) = edit { copy(configuration = configuration.withMetadataLanguage(value), error = null) }

    fun onMetadataCountry(value: String) = edit { copy(configuration = configuration.withMetadataCountry(value), error = null) }

    /** Saves the step. A refusal or no answer keeps what the user chose, with the reason beside Save. */
    fun save() {
        val wizard = state.value as? UiState.Startup ?: return
        val step = wizard.phase as? StartupPhase.Configuring ?: return
        if (step.busy) return
        val url = JellyfinClient.parseBaseUrl(wizard.baseUrl) ?: return
        edit { copy(busy = true, error = null) }
        scope.launch {
            val saved = attempt { connection.anonymous(url).saveStartupConfiguration(step.configuration) }
            saved.exceptionOrNull()?.let { connection.logger.noted(TAG, "setup save failed", it) }
            if (saved.isSuccess) account.suggestName(url)
            update {
                if (saved.isSuccess) {
                    copy(phase = StartupPhase.Account())
                } else {
                    val error = if (saved.exceptionOrNull().isRefusal()) SetupError.Generic else unreachableSetup(baseUrl, localNetwork)
                    copy(phase = (phase as? StartupPhase.Configuring)?.copy(busy = false, error = error) ?: phase)
                }
            }
        }
    }

    /** Back to the notice. */
    fun back() {
        if (state.value !is UiState.Startup) return
        onBack()
    }

    /** The wizard's calls, as the screens take them. */
    fun actions() =
        StartupActions(
            onOpen = ::open,
            onRetry = ::load,
            onName = ::onName,
            onUiLanguage = ::onUiLanguage,
            onMetadataLanguage = ::onMetadataLanguage,
            onMetadataCountry = ::onMetadataCountry,
            onSave = ::save,
            onAccountName = account::onName,
            onPassword = account::onPassword,
            onConfirmation = account::onConfirmation,
            onSaveAccount = account::save,
            onRemoteAccess = finish::onRemoteAccess,
            onPortMapping = finish::onPortMapping,
            onFinish = finish::finish,
            onBack = ::back,
        )

    private fun edit(change: StartupPhase.Configuring.() -> StartupPhase.Configuring) =
        update {
            copy(phase = (phase as? StartupPhase.Configuring)?.change() ?: phase)
        }

    private fun update(change: UiState.Startup.() -> UiState.Startup) = state.update { (it as? UiState.Startup)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinStartup"
    }
}

private fun Throwable?.isRefusal(): Boolean = this is JellyfinHttpException

private suspend fun JellyfinClient.readStartup(): StartupPhase.Configuring =
    coroutineScope {
        val configuration = async { startupConfiguration() }
        val languages = async { uiLanguages() }
        val cultures = async { cultures() }
        val countries = async { countries() }
        StartupPhase.Configuring(
            configuration = configuration.await(),
            uiLanguages = languages.await(),
            cultures = cultures.await().sortedBy { it.displayName },
            countries = countries.await().sortedBy { it.displayName },
        )
    }
