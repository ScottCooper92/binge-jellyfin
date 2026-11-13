package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The licences page's state: loading, the list, or a list that could not be read. */
sealed interface LicencesUiState {
    data object Loading : LicencesUiState

    data class Ready(
        val entries: List<LicenceEntry>,
    ) : LicencesUiState

    data object Unreadable : LicencesUiState
}

/** Reads the shipped libraries' licences once for the page. */
class LicencesViewModel(
    source: LicenceSource,
) : ViewModel() {
    private val _state = MutableStateFlow<LicencesUiState>(LicencesUiState.Loading)
    val state: StateFlow<LicencesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value =
                attempt { source.load() }.fold(
                    onSuccess = { LicencesUiState.Ready(it) },
                    onFailure = { LicencesUiState.Unreadable },
                )
        }
    }
}
