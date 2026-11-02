package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.GUIDE_PATH
import com.cooper.binge.companion.jellyfin.server.GUIDE_TYPE_XMLTV
import com.cooper.binge.companion.jellyfin.server.GuideProviderSummary
import com.cooper.binge.companion.jellyfin.server.deleteListingProvider
import com.cooper.binge.companion.jellyfin.server.listingProviders
import com.cooper.binge.companion.jellyfin.server.newXmlTvProvider
import com.cooper.binge.companion.jellyfin.server.saveListingProvider
import com.cooper.binge.companion.jellyfin.server.string
import com.cooper.binge.companion.jellyfin.server.withPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The sheet open over the Live TV page for guide data: an XMLTV form, a new one when it has no [id]. A provider of another [type]
 * (Schedules Direct) opens with no field and the way to remove it, until it can be edited here.
 */
data class GuideForm(
    val id: String? = null,
    val path: String = "",
    val type: String = GUIDE_TYPE_XMLTV,
    /** What the page calls this provider, for its remove confirm. */
    val name: String = "",
) {
    val isXmlTv: Boolean get() = type.equals(GUIDE_TYPE_XMLTV, ignoreCase = true)

    /** The address or path may carry an account, so it is left out of [toString] and out of any state dump. */
    override fun toString(): String = "GuideForm(id=$id)"
}

/** What went wrong with a guide change, which decides how the page says it. */
enum class GuideProblem {
    /** The server would not keep the provider, almost always because it could not read the address or the file. A dialog says why. */
    NotSaved,

    /** The change did not go through, and there is nothing more to say than that. */
    NotChanged,
}

/**
 * What a guide data change is doing, beside the Live TV page's own state: the [form] open, whether a change is in flight, the provider
 * to remove that the user has not confirmed ([removing]), the last [problem], and [queued], set when the server accepted a change and so
 * queued a guide refresh. It is data that is always interactive, so it is a flat class and the page's loading and failure stay with it.
 */
data class GuideUiState(
    val form: GuideForm? = null,
    val saving: Boolean = false,
    val removing: GuideRemoval? = null,
    val problem: GuideProblem? = null,
    val queued: Boolean = false,
)

/**
 * Adds, edits and removes the server's XMLTV guide data. A change is sent as a whole provider read just before, so a key this app
 * does not know, and a Schedules Direct `Password`, go back as they came; neither is shown. [onChanged] is the page re-reading its list.
 */
class LiveTvGuideViewModel(
    private val connection: Connection,
    private val onChanged: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(GuideUiState())
    val state: StateFlow<GuideUiState> = _state.asStateFlow()

    /** Opens the form for a new XMLTV guide. */
    fun add() = _state.update { it.copy(form = GuideForm(), problem = null, queued = false) }

    /** Opens the form for [provider], with an XMLTV one's address or path as the server holds it. */
    fun edit(provider: GuideProviderSummary) {
        if (!provider.type.equals(GUIDE_TYPE_XMLTV, ignoreCase = true)) {
            _state.update {
                it.copy(
                    form = GuideForm(provider.id, type = provider.type, name = provider.label),
                    problem = null,
                    queued = false,
                )
            }
            return
        }
        viewModelScope.launch {
            connection.consoleCall(TAG) { _, client -> client.listingProviders() }.fold(
                onSuccess = { providers ->
                    val path = providers[provider.id]?.string(GUIDE_PATH)
                    if (path !=
                        null
                    ) {
                        _state.update {
                            it.copy(
                                form = GuideForm(provider.id, path, name = provider.label),
                                problem = null,
                                queued = false,
                            )
                        }
                    }
                },
                onFailure = { _state.update { it.copy(problem = GuideProblem.NotChanged) } },
            )
        }
    }

    fun dismissForm() = _state.update { it.copy(form = null, problem = null) }

    /** Saves [form]: a new XMLTV guide, or the provider it names with its path changed. */
    fun save(form: GuideForm) {
        if (!form.isXmlTv || form.path.isBlank() || _state.value.saving) return
        _state.update { it.copy(saving = true, problem = null, queued = false) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client ->
                    val held = form.id?.let { client.listingProviders()[it] }
                    connection.tracked {
                        client.saveListingProvider(
                            held?.withPath(form.path.trim()) ?: newXmlTvProvider(form.path.trim()),
                        )
                    }
                }.fold(
                    onSuccess = {
                        _state.update { it.copy(saving = false, form = null, queued = true) }
                        onChanged()
                    },
                    onFailure = { _state.update { it.copy(saving = false, problem = GuideProblem.NotSaved) } },
                )
        }
    }

    /** Asks to remove the provider [form] is open for; nothing is sent until the user confirms. */
    fun askRemove(form: GuideForm) {
        val id = form.id ?: return
        _state.update { it.copy(removing = GuideRemoval(id, form.name)) }
    }

    fun cancelRemove() = _state.update { it.copy(removing = null) }

    /** Removes the provider the user confirmed removing. */
    fun remove() {
        val provider = _state.value.removing ?: return
        _state.update { it.copy(removing = null, saving = true, problem = null, queued = false) }
        viewModelScope.launch {
            connection.consoleCall(TAG) { _, client -> connection.tracked { client.deleteListingProvider(provider.id) } }.fold(
                onSuccess = {
                    _state.update { it.copy(saving = false, form = null, queued = true) }
                    onChanged()
                },
                onFailure = { _state.update { it.copy(saving = false, problem = GuideProblem.NotChanged) } },
            )
        }
    }

    fun dismissProblem() = _state.update { it.copy(problem = null) }

    fun dismissQueued() = _state.update { it.copy(queued = false) }

    private companion object {
        const val TAG = "JellyfinLiveTvGuide"
    }
}

/** The provider the user asked to remove, by the name the page shows for it. */
data class GuideRemoval(
    val id: String,
    val name: String,
)

/** What the guide data on the Live TV page can ask of its view model. */
data class LiveTvGuideActions(
    val onAdd: () -> Unit = {},
    val onEdit: (GuideProviderSummary) -> Unit = {},
    val onSave: (GuideForm) -> Unit = {},
    val onDismissForm: () -> Unit = {},
    val onAskRemove: (GuideForm) -> Unit = {},
    val onCancelRemove: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onDismissProblem: () -> Unit = {},
)
