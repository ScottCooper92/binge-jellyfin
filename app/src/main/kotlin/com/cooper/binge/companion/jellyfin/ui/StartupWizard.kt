package com.cooper.binge.companion.jellyfin.ui

import android.util.Log
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.Country
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.LocalNetworkPermission
import com.cooper.binge.companion.jellyfin.server.StartupConfiguration
import com.cooper.binge.companion.jellyfin.server.UiLanguage
import com.cooper.binge.companion.jellyfin.server.countries
import com.cooper.binge.companion.jellyfin.server.cultures
import com.cooper.binge.companion.jellyfin.server.saveStartupConfiguration
import com.cooper.binge.companion.jellyfin.server.startupConfiguration
import com.cooper.binge.companion.jellyfin.server.uiLanguages
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleFailure
import com.cooper.binge.companion.jellyfin.ui.console.consoleFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    ) : StartupPhase {
        /** The two passwords differ, so the account cannot be sent yet. */
        val mismatch: Boolean get() = confirmation.isNotEmpty() && password != confirmation

        val canSave: Boolean get() = name.isNotBlank() && password == confirmation && !busy

        override fun toString() = "Account(name=$name, busy=$busy, error=$error)"
    }

    /** The account was made. The server still reports setup as unfinished until it is completed. */
    data object Saved : StartupPhase
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
    val onBack: () -> Unit = {},
)

/**
 * Drives the setup wizard on the app's one state flow. Every call is anonymous: a server that is not set up has no account, and
 * these routes answer without one until setup is complete. Nothing is sent until the user taps Save.
 */
class StartupWizard internal constructor(
    private val connection: Connection,
    private val state: MutableStateFlow<UiState>,
    private val scope: CoroutineScope,
    private val localNetwork: LocalNetworkPermission,
) {
    /** The first account: its fields and its save. */
    val account = StartupAccountStep(connection, state, scope, localNetwork)

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
            read.exceptionOrNull()?.let { Log.i(TAG, "Setup read failed", it) }
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
            saved.exceptionOrNull()?.let { Log.i(TAG, "Setup save failed", it) }
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
        val wizard = state.value as? UiState.Startup ?: return
        state.value = UiState.SignIn(baseUrl = wizard.baseUrl, server = wizard.server)
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
