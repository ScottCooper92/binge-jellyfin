package com.cooper.homelab.ephyra.playback

import com.binge.companion.sdk.attempt
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.player.api.MediaPlayer
import com.cooper.homelab.player.api.PlayRequest
import com.cooper.homelab.player.api.PlayTitle
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import com.cooper.homelab.player.api.PlaybackOutcome
import com.cooper.homelab.player.api.PlaybackRefusal
import com.cooper.homelab.player.api.PlaybackSittingControl
import com.cooper.homelab.player.api.PlayerEvent
import com.cooper.homelab.player.api.SubtitlePreference
import com.cooper.homelab.player.api.TrackChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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

        /** What the player last said it is on; null until it says, and again whenever the sitting runs from its start. */
        val choice = MutableStateFlow<StreamChoice?>(null)

        /** A switch of audio the server did not make; the old source plays on. */
        val audioFailures = MutableSharedFlow<AudioSwitchFailure>(extraBufferCapacity = 1)
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

    /** The switches of audio the server did not make, for the screen to say so. The play goes on with the audio it had. */
    fun audioSwitchFailures(sessionId: String): Flow<AudioSwitchFailure> =
        sittings.flatMapLatest { it[sessionId]?.audioFailures ?: emptyFlow() }

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

    /** What a sitting is on while it plays: the item and its source, what follows, and where the play stands. */
    private class Playing(
        var current: PlayItem,
        var minted: Minted,
        var upNext: PlayItem?,
    ) {
        var remintedOnce = false
        var position = 0L
    }

    private suspend fun run(
        id: String,
        sitting: Sitting,
    ) {
        sitting.choice.value = null
        val profile = attempt { player.deviceProfile() }.getOrElse { return sitting.fail(it, sitting.item.title) }
        val first = attempt { minter.mint(sitting.item, profile, null, null) }.getOrElse { return sitting.fail(it, sitting.item.title) }
        val play = Playing(sitting.item, first, attempt { nextEpisodes.after(sitting.item) }.getOrNull())
        try {
            player
                .play(first.request.copy(sessionId = id, next = play.upNext?.title))
                .collect { event ->
                    if (event is PlayerEvent.Positioned) play.position = event.positionMs
                    handle(event, id, sitting, profile, play)
                }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { report(play.minted, PlayReport.STOPPED, play.position) }
            throw e
        }
        // A sitting that ended on its own is forgotten, whether or not the first frame came; one that ended in a refusal or an expired
        // source stays, for the screen to say so.
        if (sitting.outcome.value.endsOnItsOwn()) sittings.update { it - id }
    }

    private suspend fun handle(
        event: PlayerEvent,
        id: String,
        sitting: Sitting,
        profile: PlaybackDeviceProfile,
        play: Playing,
    ) {
        val minted = play.minted
        when (event) {
            is PlayerEvent.Started -> {
                sitting.outcome.value = PlaybackOutcome.Playing(play.current.title)
                report(minted, PlayReport.STARTED, play.position)
            }
            is PlayerEvent.Progress -> report(minted, PlayReport.PROGRESS, play.position)
            is PlayerEvent.Paused -> report(minted, PlayReport.PAUSED, play.position)
            is PlayerEvent.Resumed -> report(minted, PlayReport.RESUMED, play.position)
            is PlayerEvent.Finished -> report(minted, PlayReport.FINISHED, play.position)
            is PlayerEvent.Stopped -> report(minted, PlayReport.STOPPED, play.position)
            is PlayerEvent.SourceRejected -> {
                if (play.remintedOnce) {
                    sitting.outcome.value = PlaybackOutcome.SourceExpired(play.current.title)
                } else {
                    play.remintedOnce = true
                    replace(play, reload(id, play.current, profile, event.positionMs, play.upNext, sitting))
                }
            }
            is PlayerEvent.RetryRequested -> {
                play.remintedOnce = false
                replace(play, reload(id, play.current, profile, event.positionMs, play.upNext, sitting))
            }
            is PlayerEvent.AudioOptionRequested -> switchAudio(event, id, sitting, profile, play)
            is PlayerEvent.NextRequested -> moveToNext(event, id, sitting, profile, play)
            is PlayerEvent.TracksChosen -> sitting.choice.update { it.after(event) }
        }
    }

    /** The user picked another audio stream from the host's list: the same item is minted at the position on that stream, in the same session. */
    private suspend fun switchAudio(
        event: PlayerEvent.AudioOptionRequested,
        id: String,
        sitting: Sitting,
        profile: PlaybackDeviceProfile,
        play: Playing,
    ) {
        val picked = pickAudio(sitting.choice.value, play.minted, event.key) ?: return
        val fresh =
            reload(
                id,
                play.current,
                profile,
                event.positionMs,
                play.upNext,
                sitting,
                picked,
                onFailure = { sitting.audioFailures.tryEmit(it.audioSwitchFailure()) },
                accept = { it.isOnAudio(event.key) },
            )
        if (fresh != null) {
            sitting.choice.value = picked
            replace(play, fresh)
        }
    }

    /** Takes [fresh] as the source in play, if there is one, and tells the server the old one is no longer used. */
    private suspend fun replace(
        play: Playing,
        fresh: Minted?,
    ) {
        if (fresh == null) return
        val old = play.minted
        play.minted = fresh
        report(old, PlayReport.REPLACED, play.position)
    }

    /** The next episode: minted first on the tracks carried over, then the one playing is reported stopped. */
    private suspend fun moveToNext(
        event: PlayerEvent.NextRequested,
        id: String,
        sitting: Sitting,
        profile: PlaybackDeviceProfile,
        play: Playing,
    ) {
        val next = play.upNext ?: return
        val following = attempt { nextEpisodes.after(next) }.getOrNull()
        val carried = sitting.choice.value?.copy(audioStream = null)
        val fresh = reload(id, next, profile, 0L, following, sitting, carried) ?: return
        sitting.choice.value = carried
        report(play.minted, PlayReport.STOPPED, event.positionMs)
        play.current = next
        play.upNext = following
        play.minted = fresh
        play.remintedOnce = false
        play.position = 0L
    }

    /**
     * Mints [item] again at [positionMs] on the tracks of [choice] and hands it to the player under the same session. Null when the server
     * refuses, which [onFailure] says: by default the sitting ends in a refusal, which a switch of audio (the old source still plays) does not.
     */
    private suspend fun reload(
        id: String,
        item: PlayItem,
        profile: PlaybackDeviceProfile,
        positionMs: Long,
        upNext: PlayItem?,
        sitting: Sitting,
        choice: StreamChoice? = sitting.choice.value,
        onFailure: (Throwable) -> Unit = { sitting.fail(it, item.title) },
        accept: (Minted) -> Boolean = { true },
    ): Minted? {
        val minted =
            attempt { minter.mint(item, profile, positionMs, choice) }.getOrElse {
                onFailure(it)
                return null
            }
        if (!accept(minted)) {
            onFailure(SourceNotOnChoice())
            return null
        }
        player.update(minted.request.copy(sessionId = id, next = upNext?.title).opensOn(choice))
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
}

/** The server answered, but with a source that is not on the track asked for. */
private class SourceNotOnChoice : Exception()

/** Whether the source on offer is on the audio option [key], as the server says it is: a pick the server did not make is not a switch. */
private fun Minted.isOnAudio(key: String): Boolean = request.audioOptions.firstOrNull { it.current }?.key == key

private fun PlaybackOutcome.endsOnItsOwn() = this !is PlaybackOutcome.Refused && this !is PlaybackOutcome.SourceExpired

private fun JellyfinHttpException.refusal(): PlaybackRefusal =
    when (code) {
        HTTP_UNAUTHORIZED -> PlaybackRefusal.SignedOut
        HTTP_FORBIDDEN -> PlaybackRefusal.NoPermission
        HTTP_NOT_FOUND -> PlaybackRefusal.NotFound
        else -> PlaybackRefusal.Generic
    }

/** The choice that follows the user's pick of the option [key] the source offered, or null for a key it did not offer. */
private fun pickAudio(
    now: StreamChoice?,
    minted: Minted,
    key: String,
): StreamChoice? {
    val option = minted.request.audioOptions.firstOrNull { it.key == key } ?: return null
    val index = option.key.toIntOrNull() ?: return null
    return StreamChoice(TrackChoice(option.language, option.label), now?.subtitle, index)
}

/**
 * What the player says it is on. While the user's own pick from the host's list stands, that pick stays the audio: the stream has
 * one track, and what the player calls it says nothing of the choice.
 */
private fun StreamChoice?.after(event: PlayerEvent.TracksChosen): StreamChoice =
    StreamChoice(
        audio = if (this?.audioStream != null) audio else event.audio,
        subtitle = event.subtitle,
        audioStream = this?.audioStream,
    )

private fun Throwable.audioSwitchFailure(): AudioSwitchFailure =
    if (this is IOException && this !is JellyfinHttpException) AudioSwitchFailure.Unreachable else AudioSwitchFailure.Refused

/** The request asking the player to open on the tracks of [choice], as the user left them; the player's own default where there is none. */
private fun PlayRequest.opensOn(choice: StreamChoice?): PlayRequest =
    if (choice == null) {
        this
    } else {
        copy(
            preferredAudio = choice.audio,
            preferredSubtitle = choice.subtitle?.let { SubtitlePreference.Track(it) } ?: SubtitlePreference.Off,
        )
    }
