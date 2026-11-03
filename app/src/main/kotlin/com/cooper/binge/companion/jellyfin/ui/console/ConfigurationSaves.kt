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

    /** Sends a change still waiting, after any write running; the answer completes when the server has it. */
    fun settle() = engine.settle()

    /**
     * Sends the change a failed write left unsent. Nothing happens if the last write did not fail: Jellyfin has no
     * partial update, so a write of a configuration that never failed would send the snapshot this page read, and put
     * back what someone changed on the server since.
     */
    fun retry() {
        if (progress.failed) engine.now()
    }

    /**
     * The page is going: a change still waiting, or one whose write failed, is sent now, and counted with the saves
     * [Connection.awaitSaves] waits for, so a page opened next reads it.
     */
    fun cleared() {
        val owed = engine.settle()
        connection.launchSave { owed.await() }
    }
}

/**
 * What a page's `refused` becomes when [ConfigurationSaves] reports [failure], given the refusal it already says ([current]). A
 * refusal is said until the next change: a change (no failure) clears it, a refusal replaces it, and a write that could not reach
 * the server leaves it as it was, because that says nothing about a refusal, and on a page with two saves the other one's must
 * stay. Every save-as-made page reads its `refused` through this, so they cannot drift apart.
 */
internal fun refusedAfter(
    current: ConsoleFailure?,
    failure: ConsoleFailure?,
): ConsoleFailure? =
    when (failure) {
        null -> null
        ConsoleFailure.Refused -> failure
        else -> current
    }

/**
 * The saves of a page with two writers, as one status: failed if either is, busy if either is, else saved if either is.
 * Every page with two writers reads its save status through this, so they cannot drift apart.
 */
internal fun combined(
    first: SaveProgress,
    second: SaveProgress,
): SaveProgress {
    val statuses = listOf(first.status, second.status)
    val status =
        statuses.firstOrNull { it == SaveStatus.Saving }
            ?: statuses.firstOrNull { it == SaveStatus.Saved }
            ?: statuses.firstOrNull { it != SaveStatus.Idle }
            ?: SaveStatus.Idle
    return SaveProgress(status, first.failed || second.failed)
}
