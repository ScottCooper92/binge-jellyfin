package com.cooper.homelab.ephyra.ui

import android.os.Looper
import com.cooper.homelab.ephyra.FakeJellyfin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.serialization.json.JsonObject
import org.robolectric.Shadows.shadowOf

/** Runs the main looper once: what a device does all the time, and a Robolectric test only when something asks. */
internal fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

private const val IDLE_WAIT_MILLIS = 1L

/**
 * A wait for a test whose Main is [MainLooperDispatcher]. [block] runs off the main thread, so it can wait on a flow or a server
 * with the usual timeouts, while this thread, which is Main, keeps running the looper, so the view model's work still gets done.
 * `runBlocking` here would hold Main and the work it waits for could never run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> idling(block: suspend CoroutineScope.() -> T): T {
    val job = CoroutineScope(Dispatchers.IO).async(block = block)
    while (!job.isCompleted) {
        idleMain()
        Thread.sleep(IDLE_WAIT_MILLIS)
    }
    idleMain()
    return job.getCompleted()
}

/** [FakeJellyfin.awaitBody] for a test whose Main is [MainLooperDispatcher]: it sleeps between looks, so the main looper runs meanwhile. */
internal fun FakeJellyfin.awaitBodyIdling(
    path: String,
    check: (JsonObject) -> Boolean,
): JsonObject = idling { awaitBody(path, check = check) }
