package com.cooper.homelab.ephyra.openitem

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The one request the exported entry has made and the app has not yet acted on. [OpenItemActivity] puts a checked request here and starts
 * the app; the screens collect [requests] and act when the user is signed in and the client is on. A newer request replaces one nobody has
 * taken, so only the last of several quick opens is acted on. The app's own activity reads nothing from the intent that started it.
 */
class OpenItemRequests {
    private val channel = Channel<OpenItemRequest>(Channel.CONFLATED)

    /** The requests, each delivered to one collector. */
    val requests: Flow<OpenItemRequest> = channel.receiveAsFlow()

    /** Makes [request] the pending one. */
    fun offer(request: OpenItemRequest) {
        channel.trySend(request)
    }
}
