package com.cooper.binge.companion.jellyfin.review

import android.app.Activity
import android.content.Context
import com.binge.companion.sdk.attempt
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.tasks.await

/** Asks Google Play to show its in-app review card over an activity. Play may show nothing. */
fun interface ReviewPromptLauncher {
    suspend fun launch(activity: Activity)
}

/**
 * [ReviewPromptLauncher] over Play's In-App Review API. It only translates to that API and awaits its tasks. Play limits
 * how often the card appears and says nothing when it does not, so a failure of either call is simply no card.
 */
class PlayReviewPromptLauncher(
    private val context: Context,
) : ReviewPromptLauncher {
    override suspend fun launch(activity: Activity) {
        attempt {
            val manager = ReviewManagerFactory.create(context)
            manager.launchReviewFlow(activity, manager.requestReviewFlow().await()).await()
        }
    }
}
