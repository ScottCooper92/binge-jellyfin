package com.cooper.binge.companion.jellyfin.ui.console

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.removeUserImage
import com.cooper.binge.companion.jellyfin.server.setUserImage
import com.cooper.binge.companion.jellyfin.server.userImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ProfilePictureUiState {
    data object Loading : ProfilePictureUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : ProfilePictureUiState

    /**
     * [picture] is null when the user has none. [busy] is an upload or a removal on its way. [refused] is why the last change
     * didn't take, and the picture is then still the old one; [unreadable] is a file that is not an image this device can read.
     */
    data class Ready(
        val picture: Bitmap? = null,
        val busy: Boolean = false,
        val refused: ConsoleFailure? = null,
        val unreadable: Boolean = false,
        val confirmingRemove: Boolean = false,
    ) : ProfilePictureUiState
}

/**
 * The signed-in user's own picture: read, replaced with a photo they pick, and removed. A picked file is cropped to a square
 * and scaled by [prepare] before it is sent. A change that fails leaves the old picture, and says so.
 */
class ProfilePictureViewModel(
    private val connection: Connection,
    private val newRoute: Boolean,
    private val prepare: (ByteArray) -> ByteArray? = ::squareAvatar,
    private val work: CoroutineDispatcher = Dispatchers.Default,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow<ProfilePictureUiState>(ProfilePictureUiState.Loading)
    val state: StateFlow<ProfilePictureUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ProfilePictureUiState.Loading
        viewModelScope.launch {
            _state.value =
                read().fold(
                    onSuccess = { ProfilePictureUiState.Ready(picture = it) },
                    onFailure = { ProfilePictureUiState.Failed(it.consoleFailure()) },
                )
        }
    }

    /** The user's picture on the server, decoded: null for none, or for a file this device cannot decode. */
    private suspend fun read(): Result<Bitmap?> =
        connection
            .consoleCall(TAG) { session, client -> client.userImage(session.userId, newRoute) }
            .map { bytes -> bytes?.let { withContext(work) { decode(it) } } }

    /**
     * [pick] gives the file the user picked, or null when it can't be read; it runs off the main thread. The file is prepared,
     * sent, and read back.
     */
    fun choose(pick: () -> ByteArray?) {
        val ready = _state.value as? ProfilePictureUiState.Ready ?: return
        if (ready.busy) return
        _state.value = ready.copy(busy = true, unreadable = false, refused = null)
        // On the connection's scope: an upload already sent must finish if the page closes.
        connection.launchSave {
            val jpeg = withContext(io) { pick() }?.let { file -> withContext(work) { prepare(file) } }
            if (jpeg == null) {
                _state.update { (it as? ProfilePictureUiState.Ready)?.copy(busy = false, unreadable = true) ?: it }
                return@launchSave
            }
            val sent = connection.consoleCall(TAG) { session, client -> client.setUserImage(session.userId, jpeg, newRoute) }
            // What the server holds is the truth, so read it back; if that fails the upload still took, and the file sent shows.
            val shown = if (sent.isSuccess) read().getOrNull() ?: decode(jpeg) else null
            _state.update { current ->
                val now = current as? ProfilePictureUiState.Ready ?: return@update current
                sent.fold(
                    onSuccess = { now.copy(picture = shown, busy = false) },
                    onFailure = { now.copy(busy = false, refused = it.consoleFailure()) },
                )
            }
        }
    }

    fun askRemove(asking: Boolean) = _state.update { (it as? ProfilePictureUiState.Ready)?.copy(confirmingRemove = asking) ?: it }

    fun remove() {
        val ready = _state.value as? ProfilePictureUiState.Ready ?: return
        if (ready.busy) return
        _state.value = ready.copy(busy = true, confirmingRemove = false, unreadable = false, refused = null)
        connection.launchSave {
            val removed = connection.consoleCall(TAG) { session, client -> client.removeUserImage(session.userId, newRoute) }
            _state.update { current ->
                val now = current as? ProfilePictureUiState.Ready ?: return@update current
                removed.fold(
                    onSuccess = { now.copy(picture = null, busy = false) },
                    onFailure = { now.copy(busy = false, refused = it.consoleFailure()) },
                )
            }
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? = attempt { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    private companion object {
        const val TAG = "JellyfinProfilePicture"
    }
}
