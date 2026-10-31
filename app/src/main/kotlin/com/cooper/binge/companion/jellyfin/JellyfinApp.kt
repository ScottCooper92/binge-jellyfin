package com.cooper.binge.companion.jellyfin

import android.app.Application

/** Holds the [AppGraph] for the life of the process. */
class JellyfinApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
