package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.AUDIO_LANGUAGE
import com.cooper.binge.companion.jellyfin.server.AUTOPLAY_NEXT_EPISODE
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.PLAY_DEFAULT_AUDIO_TRACK
import com.cooper.binge.companion.jellyfin.server.PlaybackPreferences
import com.cooper.binge.companion.jellyfin.server.REMEMBER_AUDIO
import com.cooper.binge.companion.jellyfin.server.REMEMBER_SUBTITLES
import com.cooper.binge.companion.jellyfin.server.SUBTITLE_LANGUAGE
import com.cooper.binge.companion.jellyfin.server.SUBTITLE_MODE
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import com.cooper.binge.companion.jellyfin.server.cultures
import com.cooper.binge.companion.jellyfin.server.playbackPreferences
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
import kotlinx.serialization.json.JsonPrimitive

sealed interface PlaybackUiState {
    data object Loading : PlaybackUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : PlaybackUiState

    /** The choices as shown, and the last save that failed and was put back, if any. */
    data class Ready(
        val preferences: PlaybackPreferences,
        val cultures: List<Culture>,
        val saveFailed: ConsoleFailure? = null,
    ) : PlaybackUiState
}

/**
 * The user's playback settings, read from and saved to their own configuration. Each choice saves
 * as it's made: the last configuration the server accepted, with that one change. A save that fails
 * puts the choice back and says so.
 */
class PlaybackViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Loading)
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    /** The configuration as the server last accepted it. Saves take turns, each building on the last. */
    private var saved = JsonObject(emptyMap())
    private val saving = Mutex()

    /** The latest pick made for each key, so a failed save only puts back a choice nothing newer replaced. */
    private val latestPick = mutableMapOf<String, Int>()

    init {
        load()
    }

    fun load() {
        _state.value = PlaybackUiState.Loading
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
                            PlaybackUiState.Ready(configuration.playbackPreferences(), cultures.sortedBy { it.displayName })
                        },
                        onFailure = { PlaybackUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    fun setAudio(code: String) = save(AUDIO_LANGUAGE, code) { copy(audio = code) }

    fun setSubtitles(code: String) = save(SUBTITLE_LANGUAGE, code) { copy(subtitles = code) }

    fun setSubtitleMode(mode: SubtitleMode) = save(SUBTITLE_MODE, mode.wire) { copy(subtitleMode = mode) }

    fun setPlayDefaultAudioTrack(on: Boolean) = save(PLAY_DEFAULT_AUDIO_TRACK, on) { copy(playDefaultAudioTrack = on) }

    fun setRememberAudio(on: Boolean) = save(REMEMBER_AUDIO, on) { copy(rememberAudio = on) }

    fun setRememberSubtitles(on: Boolean) = save(REMEMBER_SUBTITLES, on) { copy(rememberSubtitles = on) }

    fun setAutoplayNextEpisode(on: Boolean) = save(AUTOPLAY_NEXT_EPISODE, on) { copy(autoplayNextEpisode = on) }

    private fun save(
        key: String,
        value: String,
        shown: PlaybackPreferences.() -> PlaybackPreferences,
    ) = save(key, JsonPrimitive(value), shown)

    private fun save(
        key: String,
        value: Boolean,
        shown: PlaybackPreferences.() -> PlaybackPreferences,
    ) = save(key, JsonPrimitive(value), shown)

    private fun save(
        key: String,
        value: JsonPrimitive,
        shown: PlaybackPreferences.() -> PlaybackPreferences,
    ) {
        if (_state.value !is PlaybackUiState.Ready) return
        val pick =
            synchronized(latestPick) {
                val next = (latestPick[key] ?: 0) + 1
                latestPick[key] = next
                updateReady { copy(preferences = preferences.shown(), saveFailed = null) }
                next
            }
        viewModelScope.launch {
            saving.withLock {
                val next = saved.with(key, value)
                connection
                    .consoleCall(TAG) { session, client -> client.saveUserConfiguration(session.userId, next) }
                    .onSuccess { saved = next }
                    .onFailure { error ->
                        // Only this choice goes back, and only if it is still the one shown: a newer pick
                        // for the same row is waiting to save, and says how it went itself.
                        val before = saved.playbackPreferences()
                        synchronized(latestPick) {
                            if (latestPick[key] != pick) return@onFailure
                            updateReady { copy(preferences = preferences.restoring(key, before), saveFailed = error.consoleFailure()) }
                        }
                    }
            }
        }
    }

    private fun updateReady(change: PlaybackUiState.Ready.() -> PlaybackUiState.Ready) =
        _state.update { (it as? PlaybackUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinPlayback"
    }
}

/** These choices with the one [key] names taken from [from]. */
private fun PlaybackPreferences.restoring(
    key: String,
    from: PlaybackPreferences,
): PlaybackPreferences =
    when (key) {
        AUDIO_LANGUAGE -> copy(audio = from.audio)
        SUBTITLE_LANGUAGE -> copy(subtitles = from.subtitles)
        SUBTITLE_MODE -> copy(subtitleMode = from.subtitleMode)
        PLAY_DEFAULT_AUDIO_TRACK -> copy(playDefaultAudioTrack = from.playDefaultAudioTrack)
        REMEMBER_AUDIO -> copy(rememberAudio = from.rememberAudio)
        REMEMBER_SUBTITLES -> copy(rememberSubtitles = from.rememberSubtitles)
        else -> copy(autoplayNextEpisode = from.autoplayNextEpisode)
    }
