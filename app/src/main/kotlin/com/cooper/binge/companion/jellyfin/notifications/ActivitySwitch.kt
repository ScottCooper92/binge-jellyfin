package com.cooper.binge.companion.jellyfin.notifications

import com.cooper.binge.companion.jellyfin.auth.ServerIdentity
import com.cooper.homelab.core.notifications.FeedCursorStore

/**
 * The administrator's notifications switch. Turning it on drops the cursor, so what the log gathered while it was off is not
 * announced: the next poll seeds and announces nothing.
 */
class ActivitySwitch(
    private val prefs: NotificationPrefs,
    private val store: FeedCursorStore,
    private val serverIdentity: ServerIdentity,
) {
    suspend fun set(on: Boolean) {
        // The cursor goes first, so a poll the switch starts cannot find the old one and announce the backlog.
        if (on) store.clear(serverIdentity.currentServerId())
        prefs.setEnabled(on)
    }
}
