package com.cooper.binge.companion.jellyfin.notifications

import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.notifications.FeedCursorKey
import com.cooper.homelab.core.notifications.FeedCursorStore

/**
 * Carries the activity cursor the app kept before the shared poll (in [NotificationPrefs], under `cursor_owner`, `cursor_id` and
 * `cursor_date_millis`) to the per-server cursor store, so the first poll after the update does not seed again and drop what
 * arrived meanwhile. It runs on every check and does something only while the new cursor is missing, and only for the user the
 * old cursor belonged to. The old keys stay in place, only read, so a rollback finds them.
 */
class LegacyActivityCursor(
    private val prefs: NotificationPrefs,
) {
    suspend fun importInto(
        store: FeedCursorStore,
        server: ServerId,
        session: JellyfinSession,
    ) {
        val key = FeedCursorKey(server, session.userId, ACTIVITY_SIGNAL)
        if (store.get(key) != null) return
        val old = prefs.cursor("${session.serverId}/${session.userId}") ?: return
        store.set(key, old.id.toString())
    }
}
