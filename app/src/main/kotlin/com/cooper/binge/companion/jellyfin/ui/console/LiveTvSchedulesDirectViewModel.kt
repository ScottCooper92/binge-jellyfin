package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.SdCountry
import com.cooper.binge.companion.jellyfin.server.SdLineup
import com.cooper.binge.companion.jellyfin.server.listingProviders
import com.cooper.binge.companion.jellyfin.server.newSchedulesDirectProvider
import com.cooper.binge.companion.jellyfin.server.saveListingProvider
import com.cooper.binge.companion.jellyfin.server.schedulesDirectCountries
import com.cooper.binge.companion.jellyfin.server.schedulesDirectLineups
import com.cooper.binge.companion.jellyfin.server.string
import com.cooper.binge.companion.jellyfin.server.withLineup
import com.cooper.binge.companion.jellyfin.server.withLogin
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The account the sheet was for is no longer on the server: read just before a save, which it would have sent over nothing. */
private class AccountGone : Exception("The account is gone")

/** Where the Schedules Direct setup stands. The password is never in it: it is hashed as the account is sent and held nowhere. */
sealed interface SdStep {
    /** The account: a username and a password, typed into the form's own fields. */
    data object Account : SdStep

    /** The login of a saved account [providerId], changed in place: [username] as it is, and a password typed anew. */
    data class Credentials(
        val providerId: String,
        val username: String,
    ) : SdStep

    /**
     * The country and postal code the lineups are read for. [countries] is null while they are read, and empty when the server gave
     * none. [choosing] is the country list open.
     */
    data class Location(
        val providerId: String,
        val countries: List<SdCountry>?,
        val country: SdCountry? = null,
        val choosing: Boolean = false,
        /** What the user has typed, kept here so choosing a country, or coming back from the lineups, does not empty the field. */
        val postalCode: String = "",
        /** The account already has a lineup, which stays until another is added: the step says so rather than that the setup is unfinished. */
        val changing: Boolean = false,
    ) : SdStep

    /** The lineups for [postalCode] in [country]: [lineups] is null while they are read. An empty list may be a login that failed. */
    data class Lineups(
        val providerId: String,
        val countries: List<SdCountry>,
        val country: SdCountry,
        val postalCode: String,
        val lineups: List<SdLineup>?,
        val changing: Boolean = false,
    ) : SdStep
}

/** What went wrong with a Schedules Direct change, which decides how the page says it. */
enum class SdProblem {
    /** The server would not keep the account. A dialog says why. */
    AccountNotSaved,

    /** The server would not add the lineup to the account, which is almost always the login. A dialog says why. */
    LineupNotAdded,

    /** The account was removed (from another client, or the dashboard) while the sheet was open, so nothing was saved. A dialog says so. */
    AccountGone,
}

/**
 * The setup sheet's [step] (null when closed), whether a call is in flight, the last [problem] (the server's own refusal of what it was
 * sent) and the last [failed] call that never got that far (unreachable, or not allowed), which a snackbar says. Always interactive, so a flat class.
 */
data class SdUiState(
    val step: SdStep? = null,
    val saving: Boolean = false,
    val problem: SdProblem? = null,
    val failed: ConsoleFailure? = null,
)

/**
 * Sets up a Schedules Direct account in steps: the account is saved first, because the server reads lineups with the account it
 * holds, then a country and postal code, then a lineup, which is added to the account. The account stays saved if the setup is
 * left, and the page lists it as not finished so it can be finished or removed. [onChanged] is the page reading its list again.
 */
class LiveTvSchedulesDirectViewModel(
    private val connection: Connection,
    private val onChanged: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(SdUiState())
    val state: StateFlow<SdUiState> = _state.asStateFlow()

    fun start() = _state.update { SdUiState(step = SdStep.Account) }

    /** Opens the login of the saved account [providerId] to change, with its [username] filled in. */
    fun editLogin(
        providerId: String,
        username: String,
    ) = _state.update { SdUiState(step = SdStep.Credentials(providerId, username)) }

    fun close() {
        lineupsLookup?.cancel()
        _state.update { SdUiState() }
    }

    fun dismissProblem() = _state.update { it.copy(problem = null, failed = null) }

    /** Saves the account from [username] and [password], hashing the password here, and goes on to the location. */
    fun signIn(
        username: String,
        password: String,
    ) {
        if (username.isBlank() || password.isEmpty() || _state.value.saving) return
        val editing = _state.value.step as? SdStep.Credentials
        if (editing != null) {
            changeLogin(editing.providerId, username.trim(), password)
            return
        }
        val account = newSchedulesDirectProvider(username.trim(), password)
        _state.update { it.copy(saving = true, problem = null, failed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(
                    TAG,
                ) { _, client -> connection.tracked { client.saveListingProvider(account, validateLogin = true) } }
                .fold(
                    onSuccess = { saved ->
                        onChanged()
                        val id = saved.string("Id")
                        if (id == null) {
                            _state.update { it.copy(saving = false, problem = SdProblem.AccountNotSaved) }
                        } else {
                            _state.update { it.copy(saving = false) }
                            // The account stays saved if the sheet was left meanwhile; the list shows it as not finished.
                            if (_state.value.step is SdStep.Account) locate(id)
                        }
                    },
                    onFailure = { error -> fail(error, SdProblem.AccountNotSaved) },
                )
        }
    }

    /** Saves the new login over the account as the server holds it, which keeps its lineup, and closes the sheet once the page is told. */
    private fun changeLogin(
        providerId: String,
        username: String,
        password: String,
    ) {
        _state.update { it.copy(saving = true, problem = null, failed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client ->
                    val held = client.listingProviders()[providerId] ?: throw AccountGone()
                    connection.tracked { client.saveListingProvider(held.withLogin(username, password), validateLogin = true) }
                }.fold(
                    onSuccess = {
                        onChanged()
                        _state.update { SdUiState() }
                    },
                    onFailure = { error -> fail(error, SdProblem.AccountNotSaved) },
                )
        }
    }

    /** Opens the location step for the saved account [providerId], reading the countries: for a new account, one left unfinished, or [changing] the lineup of one that has it. */
    fun locate(
        providerId: String,
        changing: Boolean = false,
    ) {
        _state.update { it.copy(step = SdStep.Location(providerId, countries = null, changing = changing), problem = null, failed = null) }
        viewModelScope.launch {
            connection.consoleCall(TAG) { _, client -> client.schedulesDirectCountries() }.fold(
                onSuccess = { countries -> updateStep<SdStep.Location> { copy(countries = countries) } },
                onFailure = { error -> _state.update { it.copy(step = null, failed = error.consoleFailure()) } },
            )
        }
    }

    fun chooseCountry(open: Boolean) = updateStep<SdStep.Location> { copy(choosing = open) }

    fun typePostalCode(postalCode: String) = updateStep<SdStep.Location> { copy(postalCode = postalCode) }

    fun pickCountry(country: SdCountry) = updateStep<SdStep.Location> { copy(country = country, choosing = false) }

    /** Reads the lineups for [postalCode] in the chosen country. */
    fun findLineups(postalCode: String) {
        val location = _state.value.step as? SdStep.Location ?: return
        val country = location.country ?: return
        val code = postalCode.trim()
        if (code.isEmpty()) return
        val countries = location.countries.orEmpty()
        _state.update {
            it.copy(
                step = SdStep.Lineups(location.providerId, countries, country, code, lineups = null, changing = location.changing),
                problem = null,
                failed = null,
            )
        }
        lineupsLookup?.cancel()
        lineupsLookup =
            viewModelScope.launch {
                connection.consoleCall(TAG) { _, client -> client.schedulesDirectLineups(location.providerId, country.code, code) }.fold(
                    onSuccess = { lineups -> updateStep<SdStep.Lineups> { copy(lineups = lineups) } },
                    onFailure = { error ->
                        _state.update {
                            if (it.step is SdStep.Lineups) {
                                it.copy(step = location.copy(postalCode = postalCode), failed = error.consoleFailure())
                            } else {
                                it
                            }
                        }
                    },
                )
            }
    }

    /** Back from the lineups to the location, with the country and postal code as they were. */
    fun backToLocation() {
        val lineups = _state.value.step as? SdStep.Lineups ?: return
        lineupsLookup?.cancel()
        _state.update {
            it.copy(
                step =
                    SdStep.Location(
                        lineups.providerId,
                        lineups.countries,
                        lineups.country,
                        postalCode = lineups.postalCode,
                        changing = lineups.changing,
                    ),
            )
        }
    }

    /** Adds [lineup] to the account: the provider as the server holds it with the lineup, country and postal code chosen. */
    fun addLineup(lineup: SdLineup) {
        val step = _state.value.step as? SdStep.Lineups ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, problem = null, failed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client ->
                    val held = client.listingProviders()[step.providerId] ?: throw AccountGone()
                    connection.tracked {
                        client.saveListingProvider(held.withLineup(lineup.id, step.country.code, step.postalCode), validateListings = true)
                    }
                }.fold(
                    onSuccess = {
                        // The page is told first, so seeing the setup closed means it has been.
                        onChanged()
                        _state.update { SdUiState() }
                    },
                    onFailure = { error -> fail(error, SdProblem.LineupNotAdded) },
                )
        }
    }

    /**
     * A save that did not go through. An account removed meanwhile closes the sheet, tells the page to read its list again, and says so
     * in a dialog; anything else stays on the step, as [failedWith] says.
     */
    private fun fail(
        error: Throwable,
        problem: SdProblem,
    ) {
        if (error is AccountGone) {
            onChanged()
            _state.update { SdUiState(problem = SdProblem.AccountGone) }
        } else {
            _state.update { it.copy(saving = false).failedWith(error, problem) }
        }
    }

    /** The server's own refusal of what it was sent is [problem], which a dialog explains; any other failure says why it never got there. */
    private fun SdUiState.failedWith(
        error: Throwable,
        problem: SdProblem,
    ): SdUiState =
        when {
            error.isProviderRefused() -> copy(problem = problem)
            else -> copy(failed = error.consoleFailure())
        }

    private var lineupsLookup: Job? = null

    private inline fun <reified S : SdStep> updateStep(change: S.() -> S) =
        _state.update { current -> (current.step as? S)?.let { current.copy(step = it.change()) } ?: current }

    private companion object {
        const val TAG = "JellyfinLiveTvSchedulesDirect"
    }
}
