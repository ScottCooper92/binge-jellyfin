package com.cooper.binge.companion.jellyfin

import android.app.Application

/** Holds the [AppGraph] for the life of the process. */
class JellyfinApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        // Built at start: building the analytics registers it with the consent gate, and the switch holds crash collection to the
        // user's answer from the first launch.
        graph.analytics
        graph.crashReporting.start()
    }
}
