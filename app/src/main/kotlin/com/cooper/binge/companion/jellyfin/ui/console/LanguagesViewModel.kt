package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.AUDIO_LANGUAGE
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.PlaybackLanguages
import com.cooper.binge.companion.jellyfin.server.SUBTITLE_LANGUAGE
import com.cooper.binge.companion.jellyfin.server.SUBTITLE_MODE
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import com.cooper.binge.companion.jellyfin.server.cultures
import com.cooper.binge.companion.jellyfin.server.playbackLanguages
import com.cooper.binge.companion.jellyfin.server.saveUserConfiguration
import com.cooper.binge.companion.jellyfin.server.userConfiguration
import com.cooper.binge.companion.jellyfin.server.with
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

sealed interface LanguagesUiState {
    data object Loading : LanguagesUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : LanguagesUiState

    /** The choices as shown, and the last save that failed and was put back, if any. */
    data class Ready(
        val languages: PlaybackLanguages,
        val cultures: List<Culture>,
        val saveFailed: ConsoleFailure? = null,
    ) : LanguagesUiState
}

/**
 * The user's playback languages, read from and saved to their own configuration. Each choice saves
 * as it's made: the last configuration the server accepted, with that one change. A save that fails
 * puts the choice back and says so.
 */
class LanguagesViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<LanguagesUiState>(LanguagesUiState.Loading)
    val state: StateFlow<LanguagesUiState> = _state.asStateFlow()

    /** The configuration as the server last accepted it. Saves take turns, each building on the last. */
    private var saved = JsonObject(emptyMap())
    private val saving = Mutex()

    init {
        load()
    }

    fun load() {
        _state.value = LanguagesUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { _, client ->
                        coroutineScope {
                            val cultures = async { client.cultures() }
                            val configuration = client.userConfiguration()
                            configuration to cultures.await()
                        }
                    }.fold(
                        onSuccess = { (configuration, cultures) ->
                            saved = configuration
                            LanguagesUiState.Ready(configuration.playbackLanguages(), cultures.sortedBy { it.displayName })
                        },
                        onFailure = { LanguagesUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    fun setAudio(code: String) = save(AUDIO_LANGUAGE, code) { copy(audio = code) }

    fun setSubtitles(code: String) = save(SUBTITLE_LANGUAGE, code) { copy(subtitles = code) }

    fun setSubtitleMode(mode: SubtitleMode) = save(SUBTITLE_MODE, mode.wire) { copy(subtitleMode = mode) }

    private fun save(
        key: String,
        value: String,
        shown: PlaybackLanguages.() -> PlaybackLanguages,
    ) {
        if (_state.value !is LanguagesUiState.Ready) return
        updateReady { copy(languages = languages.shown(), saveFailed = null) }
        viewModelScope.launch {
            saving.withLock {
                val next = saved.with(key, value)
                connection
                    .consoleCall(TAG) { session, client -> client.saveUserConfiguration(session.userId, next) }
                    .onSuccess { saved = next }
                    .onFailure { error ->
                        // Only this choice goes back; another saved meanwhile stays.
                        val before = saved.playbackLanguages()
                        updateReady { copy(languages = languages.restoring(key, before), saveFailed = error.consoleFailure()) }
                    }
            }
        }
    }

    private fun updateReady(change: LanguagesUiState.Ready.() -> LanguagesUiState.Ready) =
        _state.update { (it as? LanguagesUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinLanguages"
    }
}

/** These choices with the one [key] names taken from [from]. */
private fun PlaybackLanguages.restoring(
    key: String,
    from: PlaybackLanguages,
): PlaybackLanguages =
    when (key) {
        AUDIO_LANGUAGE -> copy(audio = from.audio)
        SUBTITLE_LANGUAGE -> copy(subtitles = from.subtitles)
        else -> copy(subtitleMode = from.subtitleMode)
    }
