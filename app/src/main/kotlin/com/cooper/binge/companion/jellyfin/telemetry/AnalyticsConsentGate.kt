package com.cooper.binge.companion.jellyfin.telemetry

import android.util.Log
import com.binge.companion.sdk.attempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "AnalyticsConsentGate"

/**
 * The one owner of "may analytics report", which every backend registers with. One owner, so no
 * two readers of the preference can disagree.
 *
 * [isGranted] is the flag a backend checks before it sends, and [awaitRead] is what a caller
 * holding an event waits on. [granted] turns true only after every registered backend has run its
 * open hook, so whatever observes it cannot outrun a backend still opted out; only the tests
 * observe it today. Fails closed: nothing is granted until a stored grant is read.
 */
class AnalyticsConsentGate(
    prefs: TelemetryPrefs,
    scope: CoroutineScope,
) {
    @Volatile
    var isGranted: Boolean = false
        private set

    private val hooks = CopyOnWriteArrayList<(Boolean) -> Unit>()

    private val grantedState = MutableStateFlow(false)

    /** True once consent is granted and every registered backend has opened for it. */
    val granted: StateFlow<Boolean> = grantedState.asStateFlow()

    private val readState = MutableStateFlow(false)

    init {
        scope.launch {
            prefs.analyticsConsent
                .map { it == AnalyticsConsent.GRANTED }
                .distinctUntilChanged()
                .collect(::publish)
        }
    }

    /**
     * Registers a backend's open/close hook. It is replayed at once only when the gate is
     * already open: a backend starts closed, and replaying a close would touch its SDK before
     * the user has answered. A hook must be idempotent, since a registration racing a grant
     * can see both the replay and the emission.
     */
    fun register(onConsentChanged: (granted: Boolean) -> Unit) {
        hooks += onConsentChanged
        if (isGranted) onConsentChanged.runIsolated(granted = true)
    }

    /**
     * Suspends until the stored answer has been read and every backend has reacted to it. Before
     * then [isGranted] is false for an install that did agree, so a caller holding an event
     * waits here rather than dropping it.
     */
    suspend fun awaitRead() {
        readState.first { it }
    }

    private fun publish(granted: Boolean) {
        // The first read of an unanswered or declined choice changes nothing: every backend
        // starts closed, and telling one to close would touch its SDK before the user answers.
        if (granted != isGranted) {
            // The flag first, so a hook's own reporting passes the gate it is opening; the
            // observable last, so an awaiting caller cannot outrun a backend still opening.
            isGranted = granted
            hooks.forEach { it.runIsolated(granted) }
            grantedState.value = granted
        }
        readState.value = true
    }

    /**
     * A hook calls into a third-party SDK. One that throws is logged and skipped: escaping,
     * it would end the collector and leave every caller of [awaitRead] waiting for good.
     */
    private fun ((Boolean) -> Unit).runIsolated(granted: Boolean) {
        attempt { this(granted) }
            .onFailure { Log.e(TAG, "An analytics backend failed to react to consent=$granted", it) }
    }
}
