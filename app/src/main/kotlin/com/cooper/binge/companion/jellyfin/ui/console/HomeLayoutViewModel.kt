package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.DISPLAY_COLLECTIONS_VIEW
import com.cooper.binge.companion.jellyfin.server.DISPLAY_MISSING_EPISODES
import com.cooper.binge.companion.jellyfin.server.GROUPED_FOLDERS
import com.cooper.binge.companion.jellyfin.server.HIDE_PLAYED_IN_LATEST
import com.cooper.binge.companion.jellyfin.server.HomeLayout
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.LATEST_EXCLUDES
import com.cooper.binge.companion.jellyfin.server.MY_MEDIA_EXCLUDES
import com.cooper.binge.companion.jellyfin.server.ORDERED_VIEWS
import com.cooper.binge.companion.jellyfin.server.UserView
import com.cooper.binge.companion.jellyfin.server.groupingOptions
import com.cooper.binge.companion.jellyfin.server.homeLayout
import com.cooper.binge.companion.jellyfin.server.idList
import com.cooper.binge.companion.jellyfin.server.userConfiguration
import com.cooper.binge.companion.jellyfin.server.userViews
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

sealed interface HomeLayoutUiState {
    data object Loading : HomeLayoutUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : HomeLayoutUiState

    /** The user's libraries and home layout as shown, and the last save that failed and was put back, if any. */
    data class Ready(
        private val views: List<UserView>,
        val layout: HomeLayout,
        /** The libraries that can be grouped into Movies, Shows and the like, by id. */
        val groupable: Set<String> = emptySet(),
        val saveFailed: ConsoleFailure? = null,
        /** The library whose own page is open, by id. */
        val openId: String? = null,
    ) : HomeLayoutUiState {
        /** The libraries in the home's order: the saved order first, then the rest as the server listed them. */
        val libraries: List<UserView> =
            views.sortedBy { view -> layout.order.indexOf(view.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }

        val openLibrary: UserView? get() = libraries.firstOrNull { it.id == openId }
    }
}

/**
 * How Jellyfin's clients lay out the user's home: the order of their libraries, where each one shows,
 * and the display switches. Read from and saved to the user's own configuration, each change as it's
 * made (see [ConfigurationSaves]).
 */
class HomeLayoutViewModel(
    private val connection: Connection,
    private val profile: JellyfinServerProfile,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeLayoutUiState>(HomeLayoutUiState.Loading)
    val state: StateFlow<HomeLayoutUiState> = _state.asStateFlow()

    private val saves =
        ConfigurationSaves(viewModelScope, connection, TAG) { shown, failure ->
            _state.update { (it as? HomeLayoutUiState.Ready)?.copy(layout = shown.homeLayout(), saveFailed = failure) ?: it }
        }

    init {
        load()
    }

    fun load() {
        _state.value = HomeLayoutUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { session, client ->
                        coroutineScope {
                            val views = async { client.userViews(session.userId, profile) }
                            // Grouping is a nicety: a server that can't list it still shows the rest.
                            val groupable = async { attempt { client.groupingOptions(session.userId, profile) }.getOrDefault(emptyList()) }
                            val configuration = client.userConfiguration()
                            Triple(configuration, views.await(), groupable.await())
                        }
                    }.fold(
                        onSuccess = { (configuration, views, groupable) ->
                            saves.loaded(configuration)
                            HomeLayoutUiState.Ready(views, configuration.homeLayout(), groupable.map { it.id }.toSet())
                        },
                        onFailure = { HomeLayoutUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    fun setInMyMedia(
        id: String,
        shown: Boolean,
    ) = ready()?.let { save(MY_MEDIA_EXCLUDES, idList(it.layout.myMediaExcludes.toggled(id, !shown))) }

    fun setInLatest(
        id: String,
        shown: Boolean,
    ) = ready()?.let { save(LATEST_EXCLUDES, idList(it.layout.latestExcludes.toggled(id, !shown))) }

    fun setGrouped(
        id: String,
        grouped: Boolean,
    ) = ready()?.let { save(GROUPED_FOLDERS, idList(it.layout.grouped.toggled(id, grouped))) }

    /** Moves library [id] [by] places in the home's order, saving the whole order. */
    fun move(
        id: String,
        by: Int,
    ) {
        val ids = ready()?.libraries?.map { it.id }?.toMutableList() ?: return
        val from = ids.indexOf(id)
        val to = from + by
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        save(ORDERED_VIEWS, idList(ids))
    }

    fun setHidePlayedInLatest(on: Boolean) = save(HIDE_PLAYED_IN_LATEST, JsonPrimitive(on))

    fun setDisplayMissingEpisodes(on: Boolean) = save(DISPLAY_MISSING_EPISODES, JsonPrimitive(on))

    fun setDisplayCollectionsView(on: Boolean) = save(DISPLAY_COLLECTIONS_VIEW, JsonPrimitive(on))

    /** Opens library [id]'s own page, or closes it for null. */
    fun open(id: String?) = _state.update { (it as? HomeLayoutUiState.Ready)?.copy(openId = id) ?: it }

    private fun ready() = _state.value as? HomeLayoutUiState.Ready

    private fun save(
        key: String,
        value: JsonElement,
    ) {
        if (ready() != null) saves.set(key, value)
    }

    private companion object {
        const val TAG = "JellyfinHomeLayout"
    }
}

/** This set with [id] in it when [present], else without it. */
private fun Set<String>.toggled(
    id: String,
    present: Boolean,
): Set<String> = if (present) this + id else this - id
