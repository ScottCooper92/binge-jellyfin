package com.cooper.binge.companion.jellyfin.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.AUTOPLAY_NEXT_EPISODE
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.DISPLAY_COLLECTIONS_VIEW
import com.cooper.binge.companion.jellyfin.server.DISPLAY_MISSING_EPISODES
import com.cooper.binge.companion.jellyfin.server.HIDE_PLAYED_IN_LATEST
import com.cooper.binge.companion.jellyfin.server.HomeLayout
import com.cooper.binge.companion.jellyfin.server.PLAY_DEFAULT_AUDIO_TRACK
import com.cooper.binge.companion.jellyfin.server.PlaybackPreferences
import com.cooper.binge.companion.jellyfin.server.REMEMBER_AUDIO
import com.cooper.binge.companion.jellyfin.server.REMEMBER_SUBTITLES
import com.cooper.binge.companion.jellyfin.server.homeLayout
import com.cooper.binge.companion.jellyfin.server.playbackPreferences
import com.cooper.binge.companion.jellyfin.server.userConfiguration
import com.cooper.binge.companion.jellyfin.ui.console.ConfigurationSaves
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleFailure
import com.cooper.binge.companion.jellyfin.ui.console.SaveProgress
import com.cooper.binge.companion.jellyfin.ui.console.consoleCall
import com.cooper.binge.companion.jellyfin.ui.console.consoleFailure
import com.cooper.binge.companion.jellyfin.ui.console.refusedAfter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed interface SearchSwitchesState {
    /** Nothing has asked for a switch yet, so nothing has been read. */
    data object Idle : SearchSwitchesState

    data object Loading : SearchSwitchesState

    data class Failed(
        val failure: ConsoleFailure,
    ) : SearchSwitchesState

    /** The user's configuration as shown; where its saves stand ([save]); and the last save the server refused, which was put back. */
    data class Ready(
        val configuration: JsonObject,
        val save: SaveProgress = SaveProgress(),
        val refused: ConsoleFailure? = null,
    ) : SearchSwitchesState {
        val playback: PlaybackPreferences get() = configuration.playbackPreferences()
        val home: HomeLayout get() = configuration.homeLayout()
    }
}

/**
 * The search's switches on the user's own pages (Playback and the Home screen), all on one copy of the user's configuration.
 * Jellyfin has no partial update: a save sends the whole configuration, so two copies of it, one per page, would each write back
 * what the other had saved before, silently. So the search keeps one, saved through the same [ConfigurationSaves] the pages use
 * (the same instant change, put-back on a refusal, and Retry for a change the server could not be reached for), and reads it
 * afresh each time the search comes to the front (once, however many switches show), so a change made on a page opened over it
 * is not written back.
 */
class SearchSwitchesViewModel(
    private val connection: Connection,
) : ViewModel() {
    private val _state = MutableStateFlow<SearchSwitchesState>(SearchSwitchesState.Idle)
    val state: StateFlow<SearchSwitchesState> = _state.asStateFlow()

    private val saves =
        ConfigurationSaves(
            connection,
            viewModelScope,
            TAG,
            onProgress = { progress -> _state.update { (it as? SearchSwitchesState.Ready)?.copy(save = progress) ?: it } },
        ) { shown, failure ->
            _state.update {
                (it as? SearchSwitchesState.Ready)?.copy(configuration = shown, refused = refusedAfter(it.refused, failure)) ?: it
            }
        }

    /** A read is running, so another call to [refresh] has nothing to add: one read serves every switch that asked. */
    private var reading = false

    /** How many changes have been made, so a read can tell that one was made while it ran and must not be read over. */
    private var changes = 0

    /**
     * Reads the configuration and shows it. A change still waiting out its delay, or still being written, is sent first, so the
     * read sees it. The first read shows [SearchSwitchesState.Loading]; a later one keeps what is shown until the new copy arrives.
     * What is read never replaces a change made while it ran, nor one the server could not be reached for (that is kept, unsent,
     * for Retry): reading over either would drop it without a word.
     */
    fun refresh() {
        val current = _state.value
        if (reading || (current is SearchSwitchesState.Ready && current.save.failed)) return
        reading = true
        if (current !is SearchSwitchesState.Ready) _state.value = SearchSwitchesState.Loading
        val seen = changes
        viewModelScope.launch {
            try {
                // Sent now and answered when the server has it; what comes back is a change that could not be sent.
                if (saves.settle().await() != null) return@launch
                connection.awaitSaves()
                connection
                    .consoleCall(TAG) { _, client -> client.userConfiguration() }
                    .fold(
                        onSuccess = { configuration ->
                            if (changes == seen) {
                                saves.loaded(configuration)
                                _state.value = SearchSwitchesState.Ready(configuration)
                            }
                        },
                        onFailure = { error ->
                            if (_state.value !is SearchSwitchesState.Ready) {
                                _state.value =
                                    SearchSwitchesState.Failed(error.consoleFailure())
                            }
                        },
                    )
            } finally {
                reading = false
            }
        }
    }

    fun setPlayDefaultAudioTrack(on: Boolean) = save(PLAY_DEFAULT_AUDIO_TRACK, on)

    fun setRememberAudio(on: Boolean) = save(REMEMBER_AUDIO, on)

    fun setRememberSubtitles(on: Boolean) = save(REMEMBER_SUBTITLES, on)

    fun setAutoplayNextEpisode(on: Boolean) = save(AUTOPLAY_NEXT_EPISODE, on)

    fun setHidePlayedInLatest(on: Boolean) = save(HIDE_PLAYED_IN_LATEST, on)

    fun setDisplayMissingEpisodes(on: Boolean) = save(DISPLAY_MISSING_EPISODES, on)

    fun setDisplayCollectionsView(on: Boolean) = save(DISPLAY_COLLECTIONS_VIEW, on)

    fun retrySave() = saves.retry()

    override fun onCleared() = saves.cleared()

    private fun save(
        key: String,
        on: Boolean,
    ) {
        if (_state.value !is SearchSwitchesState.Ready) return
        changes++
        saves.set(key, JsonPrimitive(on))
    }

    private companion object {
        const val TAG = "JellyfinSearchSwitches"
    }
}
