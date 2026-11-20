package com.cooper.homelab.ephyra

import android.app.Application
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.cooper.homelab.ephyra.notifications.NotificationWorkerFactory
import com.cooper.homelab.ephyra.ui.DemoImageFetcher

/** Holds the [AppGraph] for the life of the process. */
class JellyfinApp :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    val graph: AppGraph by lazy { AppGraph(this) }

    /** WorkManager starts through this, so the notification poll's worker is built with the graph's runner. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(NotificationWorkerFactory { graph.pollRunner }).build()

    /**
     * The image loader the design system draws every picture with. It is Coil's default, with one fetcher added that answers the demo
     * server's pictures; it hands every other address back, so a real server's pictures load as they did.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).components { add(DemoImageFetcher.Factory()) }.build()

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
