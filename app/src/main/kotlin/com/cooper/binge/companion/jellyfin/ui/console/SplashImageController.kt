package com.cooper.binge.companion.jellyfin.ui.console

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.brandingConfiguration
import com.cooper.binge.companion.jellyfin.server.removeSplashscreen
import com.cooper.binge.companion.jellyfin.server.splashscreenImage
import com.cooper.binge.companion.jellyfin.server.uploadSplashscreen
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * The login splash image's sheet. [picture] is what the server serves, or null when it serves none (the splash screen is off,
 * or there is no image at all): a missing preview is not an error. [loading] is the preview on its way, [busy] an upload or a
 * removal. A change the server refuses leaves the old image and says so in [refused]; [unreadable] is a file this device
 * cannot read as an image.
 */
data class SplashUiState(
    val open: Boolean = false,
    val loading: Boolean = false,
    val picture: Bitmap? = null,
    val busy: Boolean = false,
    val refused: ConsoleFailure? = null,
    val unreadable: Boolean = false,
    val confirmingRemove: Boolean = false,
)

/**
 * Setting and removing the login splash image, for the server settings page. An upload or a removal changes the branding
 * configuration on the server (`SplashscreenLocation`), so each one afterwards reads the configuration again and hands it to
 * [adoptBranding]: on Jellyfin 10.11 that is how the page learns whether the image is the user's own. A branding save does
 * not need to wait for it, since the server keeps the location it has whatever the save carries (checked on 10.11.6).
 */
internal class SplashImageController(
    private val connection: Connection,
    private val scope: CoroutineScope,
    private val adoptBranding: suspend (JsonObject) -> Unit,
    private val prepare: (ByteArray) -> ByteArray? = ::fittedImage,
    private val work: CoroutineDispatcher = Dispatchers.Default,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow(SplashUiState())
    val state: StateFlow<SplashUiState> = _state.asStateFlow()

    /** Opens the sheet, reading the preview, or closes it. */
    fun open(on: Boolean) {
        if (!on) {
            // A change still running keeps its outcome for the next time the sheet opens.
            _state.update {
                if (it.busy) {
                    it.copy(
                        open = false,
                        confirmingRemove = false,
                    )
                } else {
                    it.copy(open = false, confirmingRemove = false, refused = null, unreadable = false)
                }
            }
            return
        }
        _state.update { it.copy(open = true, loading = true, picture = null) }
        scope.launch {
            val picture = readPicture().getOrNull()
            _state.update { it.copy(loading = false, picture = picture) }
        }
    }

    /** What the server serves, decoded; a failure to read it, like a missing image, is no preview. */
    private suspend fun readPicture(): Result<Bitmap?> =
        connection
            .consoleCall(TAG) { _, client -> client.splashscreenImage() }
            .map { bytes -> bytes?.let { withContext(work) { decode(it) } } }

    /** [pick] gives the file the user picked, or null when it can't be read; it runs off the main thread. */
    fun choose(pick: () -> ByteArray?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, unreadable = false, refused = null) }
        change {
            val jpeg = withContext(io) { pick() }?.let { file -> withContext(work) { prepare(file) } }
            if (jpeg == null) {
                _state.update { it.copy(busy = false, unreadable = true) }
                return@change
            }
            val sent = connection.consoleCall(TAG) { _, client -> client.uploadSplashscreen(jpeg) }
            adoptCurrentBranding()
            val shown = if (sent.isSuccess) readPicture().getOrNull() ?: decode(jpeg) else null
            _state.update { now ->
                sent.fold(
                    onSuccess = { now.copy(picture = shown, busy = false) },
                    onFailure = { now.copy(busy = false, refused = it.consoleFailure()) },
                )
            }
        }
    }

    fun askRemove(asking: Boolean) = _state.update { it.copy(confirmingRemove = asking) }

    fun remove() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, confirmingRemove = false, unreadable = false, refused = null) }
        change {
            val removed = connection.consoleCall(TAG) { _, client -> client.removeSplashscreen() }
            adoptCurrentBranding()
            // What is served after a removal is Jellyfin's own, if it serves one, so read it rather than assume none.
            val shown = if (removed.isSuccess) readPicture().getOrNull() else null
            _state.update { now ->
                removed.fold(
                    onSuccess = { now.copy(picture = shown, busy = false) },
                    onFailure = { now.copy(busy = false, refused = it.consoleFailure()) },
                )
            }
        }
    }

    /** On the connection's scope: a request already sent must finish, and its configuration be adopted, if the page closes. */
    private fun change(block: suspend () -> Unit) = connection.launchSave(block)

    private suspend fun adoptCurrentBranding() {
        connection.consoleCall(TAG) { _, client -> client.brandingConfiguration() }.getOrNull()?.let { json ->
            // On the scope's own thread, as the page's saves are, and not at all once the page has gone.
            scope.launch { adoptBranding(json) }.join()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? = attempt { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    private companion object {
        const val TAG = "JellyfinSplashImage"
    }
}
