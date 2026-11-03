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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the Schedules Direct setup stands. The password is never in it: it is hashed as the account is sent and held nowhere. */
sealed interface SdStep {
    /** The account: a username and a password, typed into the form's own fields. */
    data object Account : SdStep

    /**
     * The country and postal code the lineups are read for. [countries] is null while they are read, and empty when the server gave
     * none. [choosing] is the country list open.
     */
    data class Location(
        val providerId: String,
        val countries: List<SdCountry>?,
        val country: SdCountry? = null,
        val choosing: Boolean = false,
    ) : SdStep

    /** The lineups for [postalCode] in [country]: [lineups] is null while they are read. An empty list may be a login that failed. */
    data class Lineups(
        val providerId: String,
        val countries: List<SdCountry>,
        val country: SdCountry,
        val postalCode: String,
        val lineups: List<SdLineup>?,
    ) : SdStep
}

/** What went wrong with a Schedules Direct change, which decides how the page says it. */
enum class SdProblem {
    /** The server would not keep the account. A dialog says why. */
    AccountNotSaved,

    /** The server would not add the lineup to the account, which is almost always the login. A dialog says why. */
    LineupNotAdded,
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

    fun close() = _state.update { SdUiState() }

    fun dismissProblem() = _state.update { it.copy(problem = null, failed = null) }

    /** Saves the account from [username] and [password], hashing the password here, and goes on to the location. */
    fun signIn(
        username: String,
        password: String,
    ) {
        if (username.isBlank() || password.isEmpty() || _state.value.saving) return
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
                            locate(id)
                        }
                    },
                    onFailure = { error -> _state.update { it.copy(saving = false).failedWith(error, SdProblem.AccountNotSaved) } },
                )
        }
    }

    /** Opens the location step for the saved account [providerId], reading the countries: for a new account, or one left unfinished. */
    fun locate(providerId: String) {
        _state.update { it.copy(step = SdStep.Location(providerId, countries = null), problem = null, failed = null) }
        viewModelScope.launch {
            connection.consoleCall(TAG) { _, client -> client.schedulesDirectCountries() }.fold(
                onSuccess = { countries -> updateStep<SdStep.Location> { copy(countries = countries) } },
                onFailure = { error -> _state.update { it.copy(step = null, failed = error.consoleFailure()) } },
            )
        }
    }

    fun chooseCountry(open: Boolean) = updateStep<SdStep.Location> { copy(choosing = open) }

    fun pickCountry(country: SdCountry) = updateStep<SdStep.Location> { copy(country = country, choosing = false) }

    /** Reads the lineups for [postalCode] in the chosen country. */
    fun findLineups(postalCode: String) {
        val location = _state.value.step as? SdStep.Location ?: return
        val country = location.country ?: return
        val code = postalCode.trim()
        if (code.isEmpty()) return
        val countries = location.countries.orEmpty()
        _state.update {
            it.copy(step = SdStep.Lineups(location.providerId, countries, country, code, lineups = null), problem = null, failed = null)
        }
        viewModelScope.launch {
            connection.consoleCall(TAG) { _, client -> client.schedulesDirectLineups(location.providerId, country.code, code) }.fold(
                onSuccess = { lineups -> updateStep<SdStep.Lineups> { copy(lineups = lineups) } },
                onFailure = { error -> _state.update { it.copy(step = location, failed = error.consoleFailure()) } },
            )
        }
    }

    /** Back from the lineups to the location, with the country as it was. */
    fun backToLocation() {
        val lineups = _state.value.step as? SdStep.Lineups ?: return
        _state.update { it.copy(step = SdStep.Location(lineups.providerId, lineups.countries, lineups.country)) }
    }

    /** Adds [lineup] to the account: the provider as the server holds it with the lineup, country and postal code chosen. */
    fun addLineup(lineup: SdLineup) {
        val step = _state.value.step as? SdStep.Lineups ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, problem = null, failed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client ->
                    val held = client.listingProviders()[step.providerId] ?: error("The account is gone")
                    connection.tracked {
                        client.saveListingProvider(held.withLineup(lineup.id, step.country.code, step.postalCode), validateListings = true)
                    }
                }.fold(
                    onSuccess = {
                        _state.update { SdUiState() }
                        onChanged()
                    },
                    onFailure = { error -> _state.update { it.copy(saving = false).failedWith(error, SdProblem.LineupNotAdded) } },
                )
        }
    }

    /** The server's own refusal of what it was sent is [problem], which a dialog explains; any other failure says why it never got there. */
    private fun SdUiState.failedWith(
        error: Throwable,
        problem: SdProblem,
    ) = if (error.isProviderRefused()) copy(problem = problem) else copy(failed = error.consoleFailure())

    private inline fun <reified S : SdStep> updateStep(change: S.() -> S) =
        _state.update { current -> (current.step as? S)?.let { current.copy(step = it.change()) } ?: current }

    private companion object {
        const val TAG = "JellyfinLiveTvSchedulesDirect"
    }
}
