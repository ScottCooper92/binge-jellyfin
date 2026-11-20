package com.cooper.homelab.ephyra.playback

import com.binge.companion.sdk.attempt
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.player.api.MediaPlayer
import com.cooper.homelab.player.api.PlayTitle
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import com.cooper.homelab.player.api.PlaybackOutcome
import com.cooper.homelab.player.api.PlaybackRefusal
import com.cooper.homelab.player.api.PlaybackSittingControl
import com.cooper.homelab.player.api.PlayerEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404

/**
 * Runs each play from the Play button to the last report, on [scope], which outlives the screen: the last stop is still delivered after the
 * screen is gone. A sitting mints a source for the player's own profile, hands it over, reports what the player says, mints again once when
 * a source is rejected, and moves to the next episode when asked.
 */
class PlaybackSittings(
    private val scope: CoroutineScope,
    private val player: MediaPlayer,
    private val minter: SourceMinter,
    private val reporter: PlayReporter,
    private val nextEpisodes: NextEpisodes,
    private val newSessionId: () -> String,
) : PlaybackSittingControl {
    private class Sitting(
        val item: PlayItem,
        val outcome: MutableStateFlow<PlaybackOutcome>,
    ) {
        var job: Job? = null
    }

    private val sittings = MutableStateFlow<Map<String, Sitting>>(emptyMap())

    /** Begins a sitting for [item] and returns its session id at once; the source is minted behind it. */
    fun start(item: PlayItem): String {
        val id = newSessionId()
        val sitting = Sitting(item, MutableStateFlow(PlaybackOutcome.Preparing(item.title)))
        sittings.update { it + (id to sitting) }
        launch(id, sitting)
        return id
    }

    override fun outcome(sessionId: String): Flow<PlaybackOutcome?> = sittings.flatMapLatest { it[sessionId]?.outcome ?: flowOf(null) }

    /** Runs a refused sitting again from its first item, with the server's own resume point. */
    override fun retry(sessionId: String) {
        val sitting = sittings.value[sessionId] ?: return
        if (sitting.outcome.value is PlaybackOutcome.Playing || sitting.job?.isActive == true) return
        sitting.outcome.value = PlaybackOutcome.Preparing(sitting.item.title)
        launch(sessionId, sitting)
    }

    override fun cancel(sessionId: String) {
        sittings.value[sessionId]?.job?.cancel()
        sittings.update { it - sessionId }
    }

    private fun launch(
        id: String,
        sitting: Sitting,
    ) {
        sitting.job = scope.launch { run(id, sitting) }
    }

    private suspend fun run(
        id: String,
        sitting: Sitting,
    ) {
        val profile = attempt { player.deviceProfile() }.getOrElse { return sitting.fail(it, sitting.item.title) }
        var current = sitting.item
        var minted = attempt { minter.mint(current, profile, null) }.getOrElse { return sitting.fail(it, current.title) }
        var upNext = attempt { nextEpisodes.after(current) }.getOrNull()
        var remintedOnce = false
        var position = 0L
        try {
            player
                .play(minted.request.copy(sessionId = id, next = upNext?.title))
                .collect { event ->
                    when (event) {
                        is PlayerEvent.Positioned -> position = event.positionMs
                        else -> Unit
                    }
                    when (event) {
                        is PlayerEvent.Started -> {
                            sitting.outcome.value = PlaybackOutcome.Playing(current.title)
                            report(minted, PlayReport.STARTED, position)
                        }
                        is PlayerEvent.Progress -> report(minted, PlayReport.PROGRESS, position)
                        is PlayerEvent.Paused -> report(minted, PlayReport.PAUSED, position)
                        is PlayerEvent.Resumed -> report(minted, PlayReport.RESUMED, position)
                        is PlayerEvent.Finished -> report(minted, PlayReport.FINISHED, position)
                        is PlayerEvent.Stopped -> report(minted, PlayReport.STOPPED, position)
                        is PlayerEvent.SourceRejected -> {
                            if (remintedOnce) {
                                sitting.outcome.value = PlaybackOutcome.SourceExpired(current.title)
                            } else {
                                remintedOnce = true
                                minted = reload(id, current, profile, event.positionMs, upNext, sitting) ?: minted
                            }
                        }
                        is PlayerEvent.RetryRequested -> {
                            remintedOnce = false
                            minted = reload(id, current, profile, event.positionMs, upNext, sitting) ?: minted
                        }
                        is PlayerEvent.NextRequested -> {
                            val next = upNext
                            if (next != null) {
                                val following = attempt { nextEpisodes.after(next) }.getOrNull()
                                val fresh = reload(id, next, profile, 0L, following, sitting)
                                if (fresh != null) {
                                    report(minted, PlayReport.STOPPED, event.positionMs)
                                    current = next
                                    upNext = following
                                    minted = fresh
                                    remintedOnce = false
                                    position = 0L
                                }
                            }
                        }
                        is PlayerEvent.TracksChosen -> Unit
                    }
                }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { report(minted, PlayReport.STOPPED, position) }
            throw e
        }
        // A sitting that ended on its own is forgotten, whether or not the first frame came; one that ended in a refusal or an expired
        // source stays, for the screen to say so.
        if (sitting.outcome.value.endsOnItsOwn()) sittings.update { it - id }
    }

    /** Mints [item] again at [positionMs] and hands it to the player under the same session; null, with the outcome set, when the server refuses. */
    private suspend fun reload(
        id: String,
        item: PlayItem,
        profile: PlaybackDeviceProfile,
        positionMs: Long,
        upNext: PlayItem?,
        sitting: Sitting,
    ): Minted? {
        val minted =
            attempt { minter.mint(item, profile, positionMs) }.getOrElse {
                sitting.fail(it, item.title)
                return null
            }
        player.update(minted.request.copy(sessionId = id, next = upNext?.title))
        sitting.outcome.value = PlaybackOutcome.Playing(item.title)
        return minted
    }

    private suspend fun report(
        minted: Minted,
        kind: PlayReport,
        positionMs: Long,
    ) {
        attempt { reporter.report(minted.report, kind, positionMs) }
    }

    private fun PlaybackOutcome.endsOnItsOwn() = this !is PlaybackOutcome.Refused && this !is PlaybackOutcome.SourceExpired

    private fun Sitting.fail(
        error: Throwable,
        title: PlayTitle,
    ) {
        outcome.value =
            when (error) {
                is JellyfinHttpException -> PlaybackOutcome.Refused(title, error.refusal(), error.reason)
                is IOException -> PlaybackOutcome.Refused(title, PlaybackRefusal.Unreachable)
                else -> PlaybackOutcome.Refused(title, PlaybackRefusal.Generic)
            }
    }

    private fun JellyfinHttpException.refusal(): PlaybackRefusal =
        when (code) {
            HTTP_UNAUTHORIZED -> PlaybackRefusal.SignedOut
            HTTP_FORBIDDEN -> PlaybackRefusal.NoPermission
            HTTP_NOT_FOUND -> PlaybackRefusal.NotFound
            else -> PlaybackRefusal.Generic
        }
}
