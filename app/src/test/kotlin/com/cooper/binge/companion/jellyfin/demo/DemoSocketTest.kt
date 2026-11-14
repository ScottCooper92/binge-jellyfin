package com.cooper.binge.companion.jellyfin.demo

import com.cooper.binge.companion.jellyfin.server.ClientIdentity
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.nowPlayingFeed
import com.cooper.binge.companion.jellyfin.server.runTask
import com.cooper.binge.companion.jellyfin.server.taskPushes
import com.cooper.binge.companion.jellyfin.server.tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSocketTest {
    private val client =
        JellyfinClient(
            OkHttpClient.Builder().addInterceptor(DemoTransport()).build(),
            DEMO_ORIGIN.toHttpUrl(),
            ClientIdentity("Ephyra", "Test", "device-1", "1.0"),
            Dispatchers.Unconfined,
            DEMO_TOKEN,
        )

    @Test
    fun `the event socket pushes what is playing, as a real server does`() =
        runBlocking {
            val playing = withTimeout(10_000) { client.nowPlayingFeed().first() }
            assertEquals(2, playing.size)
        }

    @Test
    fun `a task that is started is seen running over the socket, then finished`() =
        runBlocking {
            val task = client.tasks().first { it.name == "Optimise database" }
            client.runTask(task.id)
            val running = withTimeout(10_000) { client.taskPushes().first { pushed -> pushed.first { it.id == task.id }.running } }
            assertTrue(running.first { it.id == task.id }.percent != null)
            val finished = withTimeout(15_000) { client.taskPushes().first { pushed -> !pushed.first { it.id == task.id }.running } }
            assertTrue(finished.first { it.id == task.id }.lastRun != null)
        }
}
