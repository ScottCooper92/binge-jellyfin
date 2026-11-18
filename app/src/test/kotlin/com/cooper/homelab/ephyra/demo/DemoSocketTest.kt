package com.cooper.homelab.ephyra.demo

import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.nowPlayingFeed
import com.cooper.homelab.ephyra.server.runTask
import com.cooper.homelab.ephyra.server.taskPushes
import com.cooper.homelab.ephyra.server.tasks
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
            DiscardLogger,
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
