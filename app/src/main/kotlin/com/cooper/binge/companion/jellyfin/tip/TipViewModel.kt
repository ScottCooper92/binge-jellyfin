package com.cooper.binge.companion.jellyfin.tip

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The tip jar's state: no row until Play has priced every step. */
sealed interface TipUiState {
    data object Loading : TipUiState

    data object Unavailable : TipUiState

    data class Ready(
        val prices: Map<Int, String>,
    ) : TipUiState
}

/** What a finished tip says to the user. A cancelled tip is not an event. */
sealed interface TipEvent {
    data object Thanks : TipEvent

    data object Pending : TipEvent

    data object Failed : TipEvent
}

/** Reads the tip prices once and runs one purchase at a time. */
internal class TipViewModel(
    private val billing: TipBilling,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TipUiState>(TipUiState.Loading)
    val uiState: StateFlow<TipUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TipEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TipEvent> = _events.asSharedFlow()

    private var giving = false

    init {
        viewModelScope.launch {
            _uiState.value = billing.prices()?.let(TipUiState::Ready) ?: TipUiState.Unavailable
        }
    }

    fun give(
        activity: Activity,
        step: Int,
    ) {
        if (giving || _uiState.value !is TipUiState.Ready) return
        giving = true
        viewModelScope.launch {
            try {
                when (billing.give(activity, step)) {
                    TipOutcome.Thanks -> _events.emit(TipEvent.Thanks)
                    TipOutcome.Pending -> _events.emit(TipEvent.Pending)
                    TipOutcome.Failed -> _events.emit(TipEvent.Failed)
                    TipOutcome.Cancelled -> Unit
                }
            } finally {
                giving = false
            }
        }
    }
}
