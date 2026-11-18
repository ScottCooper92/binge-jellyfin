package com.cooper.homelab.ephyra.ui.console

import com.binge.designsystem.SaveStatus
import com.cooper.homelab.core.presentation.editor.SaveAsMade
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.saveUserConfiguration
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.cooper.homelab.core.presentation.editor.SaveStatus as EngineStatus

/** Where a page's saves stand, for [FormSaveStatus]: the last write's [status], and whether a change is [failed], unsent. */
data class SaveProgress(
    val status: SaveStatus = SaveStatus.Idle,
    val failed: Boolean = false,
)

/**
 * Saves a configuration as a page's changes are made, through the shared library's [SaveAsMade]: the user's own by
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
    private val scope: CoroutineScope,
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
            // The shared rules: a 5xx is the server failing, so the change is kept unsent and offered again, not put back.
            isTransient = { error -> error.serverFailure().isTransient },
            status = { status -> report(progress.copy(status = status.shown())) },
            refused = ::refuse,
        )

    private fun refuse(error: Throwable) {
        shown = saved
        onShown(shown, error.consoleFailure())
    }

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

    /**
     * Takes one key's value from a read of the server, on both copies and without [SaveAsMade.discard]: what the page has
     * waiting stays waiting. A null [value] drops the key.
     */
    fun adoptKey(
        key: String,
        value: JsonElement?,
    ) {
        saved = saved.with(key, value)
        shown = shown.with(key, value)
    }

    private fun JsonObject.with(
        key: String,
        value: JsonElement?,
    ) = JsonObject(if (value == null) this - key else this + (key to value))

    fun set(
        key: String,
        value: JsonElement,
    ) {
        shown = JsonObject(shown + (key to value))
        onShown(shown, null)
        engine.changed()
    }

    /**
     * Sends a change still waiting, after any write running; the answer completes when the server has it. A write the server
     * refuses meanwhile is told to the page, and answers nothing.
     */
    fun settle(): Deferred<JsonObject?> {
        val settled = engine.settle()
        return connection.saveScope.async(connection.io) {
            val answer = settled.await()
            answer.refusal?.let { scope.launch { refuse(it) } }
            answer.kept
        }
    }

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
        val owed = engine.cleared()
        connection.launchSave { owed.join() }
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

/** The shared engine's status as the design system's, which the pages' status line reads. */
private fun EngineStatus.shown(): SaveStatus =
    when (this) {
        EngineStatus.Idle -> SaveStatus.Idle
        EngineStatus.Saving -> SaveStatus.Saving
        EngineStatus.Saved -> SaveStatus.Saved
    }
