package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LiveChannel
import com.cooper.binge.companion.jellyfin.server.ParentalRating
import com.cooper.binge.companion.jellyfin.server.ServerDevice
import com.cooper.binge.companion.jellyfin.server.devices
import com.cooper.binge.companion.jellyfin.server.liveTvChannels
import com.cooper.binge.companion.jellyfin.server.parentalRatings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A list a section picks from, read when its page opens. */
sealed interface Loaded<out T> {
    data object Idle : Loaded<Nothing>

    data object Loading : Loaded<Nothing>

    data class Failed(
        val failure: ConsoleFailure,
    ) : Loaded<Nothing>

    data class Ready<T>(
        val value: T,
    ) : Loaded<T>
}

/** What the pages of a user's settings pick from: the server's ratings, its live TV channels and its devices. */
data class UserChoices(
    val ratings: Loaded<List<ParentalRating>> = Loaded.Idle,
    val channels: Loaded<List<LiveChannel>> = Loaded.Idle,
    val devices: Loaded<List<ServerDevice>> = Loaded.Idle,
)

/**
 * The lists a user's settings pick from, each read the first time its page opens (and again on Try again),
 * so the users page doesn't ask the server for live TV channels it may not have.
 */
class UserChoicesViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow(UserChoices())
    val state: StateFlow<UserChoices> = _state.asStateFlow()

    /** Reads what [section] picks from, unless it has it or is reading it. */
    fun ensure(section: UserSection?) {
        val now = _state.value
        when (section) {
            UserSection.Parental -> if (now.ratings is Loaded.Idle) read(UserSection.Parental)
            UserSection.LiveTv -> if (now.channels is Loaded.Idle) read(UserSection.LiveTv)
            UserSection.Devices -> if (now.devices is Loaded.Idle) read(UserSection.Devices)
            else -> Unit
        }
    }

    /** Reads what [section] picks from again, after a failure. */
    fun retry(section: UserSection) = read(section)

    private fun read(section: UserSection) {
        _state.update {
            when (section) {
                UserSection.Parental -> it.copy(ratings = Loaded.Loading)
                UserSection.LiveTv -> it.copy(channels = Loaded.Loading)
                else -> it.copy(devices = Loaded.Loading)
            }
        }
        viewModelScope.launch {
            when (section) {
                UserSection.Parental ->
                    connection.consoleCall(TAG) { _, c -> c.parentalRatings() }.let { r ->
                        set { copy(ratings = r.loaded()) }
                    }
                UserSection.LiveTv ->
                    connection.consoleCall(TAG) { _, c -> c.liveTvChannels() }.let { r ->
                        set { copy(channels = r.loaded()) }
                    }
                else -> connection.consoleCall(TAG) { _, c -> c.devices() }.let { r -> set { copy(devices = r.loaded()) } }
            }
        }
    }

    private fun set(change: UserChoices.() -> UserChoices) = _state.update { it.change() }

    private companion object {
        const val TAG = "JellyfinUserChoices"
    }
}

private fun <T> Result<T>.loaded(): Loaded<T> = fold(onSuccess = { Loaded.Ready(it) }, onFailure = { Loaded.Failed(it.consoleFailure()) })
