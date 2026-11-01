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
import com.cooper.binge.companion.jellyfin.server.userConfiguration
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
 * as it's made (see [ConfigurationSaves]); a save that fails puts the choice back and says so.
 */
class PlaybackViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Loading)
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val saves =
        ConfigurationSaves(viewModelScope, connection, TAG) { shown, failure ->
            _state.update { (it as? PlaybackUiState.Ready)?.copy(preferences = shown.playbackPreferences(), saveFailed = failure) ?: it }
        }

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
                            saves.loaded(configuration)
                            PlaybackUiState.Ready(configuration.playbackPreferences(), cultures.sortedBy { it.displayName })
                        },
                        onFailure = { PlaybackUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    fun setAudio(code: String) = save(AUDIO_LANGUAGE, JsonPrimitive(code))

    fun setSubtitles(code: String) = save(SUBTITLE_LANGUAGE, JsonPrimitive(code))

    fun setSubtitleMode(mode: SubtitleMode) = save(SUBTITLE_MODE, JsonPrimitive(mode.wire))

    fun setPlayDefaultAudioTrack(on: Boolean) = save(PLAY_DEFAULT_AUDIO_TRACK, JsonPrimitive(on))

    fun setRememberAudio(on: Boolean) = save(REMEMBER_AUDIO, JsonPrimitive(on))

    fun setRememberSubtitles(on: Boolean) = save(REMEMBER_SUBTITLES, JsonPrimitive(on))

    fun setAutoplayNextEpisode(on: Boolean) = save(AUTOPLAY_NEXT_EPISODE, JsonPrimitive(on))

    private fun save(
        key: String,
        value: JsonPrimitive,
    ) {
        if (_state.value is PlaybackUiState.Ready) saves.set(key, value)
    }

    private companion object {
        const val TAG = "JellyfinPlayback"
    }
}
