package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.saveUserConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Saves a configuration a change at a time, as the console's settings pages make them: the user's own
 * by default, or another that [save] sends (the server's, its branding). Jellyfin has no partial
 * update, so each save sends the last configuration the server accepted with that one change, and
 * saves take turns, each building on the last.
 *
 * A change shows at once ([shown] goes to [onShown]). A save that fails puts that one key back to what
 * the server last accepted, with the failure, unless a newer change to the same key is on its way and
 * will say how it went itself.
 */
internal class ConfigurationSaves(
    private val scope: CoroutineScope,
    private val connection: Connection,
    private val tag: String,
    private val save: suspend (JellyfinSession, JellyfinClient, JsonObject) -> Unit = { session, client, configuration ->
        client.saveUserConfiguration(session.userId, configuration)
    },
    private val onShown: (shown: JsonObject, failure: ConsoleFailure?) -> Unit,
) {
    /** The configuration as the server last accepted it. */
    private var saved = JsonObject(emptyMap())

    /** The configuration as the page shows it: [saved] with the changes still saving. */
    var shown = JsonObject(emptyMap())
        private set

    private val saving = Mutex()
    private val lock = Any()

    /** The latest change made to each key, so a failed save only puts back a change nothing newer replaced. */
    private val latest = mutableMapOf<String, Int>()

    /** The configuration the page read: what the next save builds on. */
    fun loaded(configuration: JsonObject) {
        synchronized(lock) {
            saved = configuration
            shown = configuration
        }
    }

    fun set(
        key: String,
        value: JsonElement,
    ) {
        val change =
            synchronized(lock) {
                val next = (latest[key] ?: 0) + 1
                latest[key] = next
                shown = JsonObject(shown + (key to value))
                onShown(shown, null)
                next
            }
        scope.launch {
            saving.withLock {
                val next = JsonObject(saved + (key to value))
                connection
                    .consoleCall(tag) { session, client -> save(session, client, next) }
                    .onSuccess { synchronized(lock) { saved = next } }
                    .onFailure { error ->
                        synchronized(lock) {
                            if (latest[key] != change) return@onFailure
                            shown = JsonObject(shown - key + (saved[key]?.let { mapOf(key to it) } ?: emptyMap()))
                            onShown(shown, error.consoleFailure())
                        }
                    }
            }
        }
    }
}
