package com.cooper.binge.companion.jellyfin

import android.app.Application
import androidx.work.Configuration
import com.cooper.binge.companion.jellyfin.notifications.NotificationWorkerFactory

/** Holds the [AppGraph] for the life of the process. */
class JellyfinApp :
    Application(),
    Configuration.Provider {
    val graph: AppGraph by lazy { AppGraph(this) }

    /** WorkManager starts through this, so the notification poll's worker is built with the graph's runner. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(NotificationWorkerFactory { graph.pollRunner }).build()

    override fun onCreate() {
        super.onCreate()
        // Built at start: building the analytics registers it with the consent gate, and the switch holds crash collection to the
        // user's answer from the first launch.
        graph.analytics
        graph.crashReporting.start()
        graph.reviewPrompt.start()
        graph.settleTips()
        graph.notificationPlanner.start()
        graph.startCleartextConsent()
    }
}
