package com.cooper.binge.companion.jellyfin.ui.console

import com.binge.designsystem.SaveStatus
import com.binge.designsystem.save.SaveAsMade
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.saveUserConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Where a page's saves stand, for [FormSaveStatus]: the last write's [status], and whether a change is [failed], unsent. */
data class SaveProgress(
    val status: SaveStatus = SaveStatus.Idle,
    val failed: Boolean = false,
)

/**
 * Saves a configuration as a page's changes are made, through the design system's [SaveAsMade]: the user's own by
 * default, or another that [save] sends (the server's, its branding). Jellyfin has no partial update, so each write
 * sends the whole configuration as the page shows it, with every key it was given kept, and writes take turns, each
 * built on the last. A burst of changes is one write.
 *
 * A change shows at once ([shown] goes to [onShown]). A write that fails because the server could not be reached keeps
 * the change on screen, unsent, and [onProgress] says so until a write lands: [retry] sends it again. A write the server
 * refuses would only be refused again, so the configuration goes back to what the server last accepted, with the
 * failure, in [onShown].
 *
 * A write runs on the connection's own scope, not the page's, so closing the page does not cancel it.
 */
internal class ConfigurationSaves(
    private val connection: Connection,
    scope: CoroutineScope,
    private val tag: String,
    private val save: suspend (JellyfinSession, JellyfinClient, JsonObject) -> Unit = { session, client, configuration ->
        client.saveUserConfiguration(session.userId, configuration)
    },
    private val onProgress: (SaveProgress) -> Unit = {},
    private val onShown: (shown: JsonObject, failure: ConsoleFailure?) -> Unit,
) {
    /** The configuration as the server last accepted it. */
    private var saved = JsonObject(emptyMap())

    /** The configuration as the page shows it: [saved] with the changes not yet accepted. */
    var shown = JsonObject(emptyMap())
        private set

    private var progress = SaveProgress()

    private val engine =
        SaveAsMade(
            scope = scope,
            appScope = connection.saveScope,
            dispatcher = connection.io,
            draft = { shown },
            canSave = { true },
            write = { configuration ->
                connection.tracked {
                    connection.consoleCall(tag) { session, client -> save(session, client, configuration) }.getOrThrow()
                }
                configuration
            },
            adopt = { sent, _ ->
                saved = sent
                shown != sent
            },
            failed = { failed ->
                report(progress.copy(failed = failed))
                if (failed) onShown(shown, ConsoleFailure.Unreachable)
            },
            isTransient = { error -> error.consoleFailure() == ConsoleFailure.Unreachable },
            status = { status -> report(progress.copy(status = status)) },
            refused = { error ->
                shown = saved
                onShown(shown, error.consoleFailure())
            },
        )

    private fun report(next: SaveProgress) {
        progress = next
        onProgress(next)
    }

    /** The configuration the page read: what the next write builds on. */
    fun loaded(configuration: JsonObject) {
        saved = configuration
        shown = configuration
        engine.discard()
    }

    fun set(
        key: String,
        value: JsonElement,
    ) {
        shown = JsonObject(shown + (key to value))
        onShown(shown, null)
        engine.changed()
    }

    /** Sends the change a failed write left unsent. */
    fun retry() = engine.now()

    /**
     * The page is going: a change still waiting, or one whose write failed, is sent now, and counted with the saves
     * [Connection.awaitSaves] waits for, so a page opened next reads it.
     */
    fun cleared() {
        val owed = engine.settle()
        connection.launchSave { owed.await() }
    }
}
