package com.cooper.binge.companion.jellyfin.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.designsystem.SaveStatus
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
        /** The copy is being read again: it may be older than the server's, so no change is built on it until the read is in. */
        val reading: Boolean = false,
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

    /** The changes the server has not accepted yet, by key, so a read can put them back on top of the copy it reads. */
    private val unsent = mutableMapOf<String, JsonPrimitive>()

    private val saves =
        ConfigurationSaves(
            connection,
            viewModelScope,
            TAG,
            onProgress = { progress ->
                // What the server has accepted is no longer owed to it.
                if (progress.status == SaveStatus.Saved && !progress.failed) unsent.clear()
                _state.update { (it as? SearchSwitchesState.Ready)?.copy(save = progress) ?: it }
            },
        ) { shown, failure ->
            // A refusal puts the whole copy back to what the server accepted, so nothing is owed: a read must not send it again.
            if (failure == ConsoleFailure.Refused) unsent.clear()
            _state.update {
                (it as? SearchSwitchesState.Ready)?.copy(configuration = shown, refused = refusedAfter(it.refused, failure)) ?: it
            }
        }

    /** A read is running, so another call to [refresh] has nothing to add: one read serves every switch that asked. */
    private var reading = false

    /**
     * Reads the configuration and shows it. A change still waiting out its delay, or still being written, is sent first, so the
     * read sees it. While it runs the copy shown may be older than the server's, so no change can be made on it
     * ([SearchSwitchesState.Ready.reading]: the switches show the spinner), which is the only way a change is never built on a copy
     * that lacks what a page saved. A change the server could not be reached for is not sent first (that would write the old copy
     * over what a page saved); it is put back on top of what is read, which sends it again a moment after the read lands.
     */
    fun refresh() {
        val current = _state.value
        if (reading) return
        reading = true
        val kept = current is SearchSwitchesState.Ready && current.save.failed
        _state.value = (current as? SearchSwitchesState.Ready)?.copy(reading = true) ?: SearchSwitchesState.Loading
        viewModelScope.launch {
            try {
                // Sent now and answered when the server has it.
                if (!kept) saves.settle().await()
                connection.awaitSaves()
                connection
                    .consoleCall(TAG) { _, client -> client.userConfiguration() }
                    .fold(
                        onSuccess = { configuration ->
                            // Before the state says it is done, so whoever sees it done may ask again.
                            reading = false
                            saves.loaded(configuration)
                            _state.update {
                                (it as? SearchSwitchesState.Ready)?.copy(configuration = configuration, reading = false)
                                    ?: SearchSwitchesState.Ready(configuration)
                            }
                            unsent.toMap().forEach { (key, value) -> saves.set(key, value) }
                        },
                        onFailure = { error ->
                            reading = false
                            _state.update {
                                (it as? SearchSwitchesState.Ready)?.copy(reading = false)
                                    ?: SearchSwitchesState.Failed(error.consoleFailure())
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

    /**
     * A page is about to open over the search, and will read the configuration. A change still waiting out its delay is sent now and
     * counted with the saves [Connection.awaitSaves] waits for, so the page reads it: otherwise the page would read the server
     * without it, and its next save would send that copy back over the change. The view model stays usable, for when the search is
     * shown again.
     */
    fun leaving() {
        val owed = saves.settle()
        connection.launchSave { owed.await() }
    }

    /** Not while the copy is being read again: Retry would send the old copy over what a page saved, and the read puts the change back anyway. */
    fun retrySave() {
        if ((_state.value as? SearchSwitchesState.Ready)?.reading == true) return
        saves.retry()
    }

    override fun onCleared() = saves.cleared()

    private fun save(
        key: String,
        on: Boolean,
    ) {
        val ready = _state.value as? SearchSwitchesState.Ready ?: return
        if (ready.reading) return
        val value = JsonPrimitive(on)
        unsent[key] = value
        saves.set(key, value)
    }

    private companion object {
        const val TAG = "JellyfinSearchSwitches"
    }
}
