package com.cooper.binge.companion.jellyfin.review

import android.app.Activity
import com.cooper.binge.companion.jellyfin.flags.FeatureFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The review prompt: counts the things a user finished, and asks Play for its review card once they have earned it.
 * It is off until the `review_prompt_enabled` flag is on, and only ever asks when the user returns to the root,
 * never over a screen of their own making.
 */
class ReviewPrompt(
    private val store: ReviewSignalStore,
    private val launcher: ReviewPromptLauncher,
    private val featureFlags: FeatureFlags,
    private val scope: CoroutineScope,
) : ReviewSignalSink {
    internal var clock: () -> Long = System::currentTimeMillis

    @Volatile private var suppressed = false

    /** Stamps the first launch, once. */
    fun start() {
        scope.launch { store.stampFirstLaunchIfUnset(clock()) }
    }

    /** The user did something real and it worked. */
    override fun recordPositiveAction() {
        scope.launch { store.incrementPositiveActionCount() }
    }

    /** No ask for the rest of this process, for a moment that a rating must not follow. */
    fun suppressForThisSession() {
        suppressed = true
    }

    /** Asks if the flag is on, nothing suppresses it, and the user has earned it. */
    fun maybeAsk(activity: Activity) {
        if (suppressed || !featureFlags.flags.value.reviewPrompt) return
        scope.launch {
            val now = clock()
            if (!ReviewPromptEligibility.isEligible(store.signals(), ReviewPromptThresholds(), now)) return@launch
            store.recordPromptRequested(now)
            launcher.launch(activity)
        }
    }
}
