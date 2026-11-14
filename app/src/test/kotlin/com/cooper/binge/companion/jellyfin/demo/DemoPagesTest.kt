package com.cooper.binge.companion.jellyfin.demo

import com.cooper.binge.companion.jellyfin.server.BackupOptions
import com.cooper.binge.companion.jellyfin.server.ClientIdentity
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.TaskStatus
import com.cooper.binge.companion.jellyfin.server.addLibrary
import com.cooper.binge.companion.jellyfin.server.addLibraryPath
import com.cooper.binge.companion.jellyfin.server.apiKeys
import com.cooper.binge.companion.jellyfin.server.backups
import com.cooper.binge.companion.jellyfin.server.createApiKey
import com.cooper.binge.companion.jellyfin.server.createBackup
import com.cooper.binge.companion.jellyfin.server.deleteTunerHost
import com.cooper.binge.companion.jellyfin.server.disablePlugin
import com.cooper.binge.companion.jellyfin.server.libraries
import com.cooper.binge.companion.jellyfin.server.liveTvSummary
import com.cooper.binge.companion.jellyfin.server.logFiles
import com.cooper.binge.companion.jellyfin.server.logTail
import com.cooper.binge.companion.jellyfin.server.plugins
import com.cooper.binge.companion.jellyfin.server.quickConnectEnabled
import com.cooper.binge.companion.jellyfin.server.removeLibrary
import com.cooper.binge.companion.jellyfin.server.renameLibrary
import com.cooper.binge.companion.jellyfin.server.revokeApiKey
import com.cooper.binge.companion.jellyfin.server.runTask
import com.cooper.binge.companion.jellyfin.server.saveServerConfiguration
import com.cooper.binge.companion.jellyfin.server.saveTunerHost
import com.cooper.binge.companion.jellyfin.server.serverConfiguration
import com.cooper.binge.companion.jellyfin.server.tasks
import com.cooper.binge.companion.jellyfin.server.uninstallPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPagesTest {
    private val client =
        JellyfinClient(
            OkHttpClient.Builder().addInterceptor(DemoTransport()).build(),
            DEMO_ORIGIN.toHttpUrl(),
            ClientIdentity("Ephyra", "Test", "device-1", "1.0"),
            Dispatchers.Unconfined,
            DEMO_TOKEN,
        )

    @Test
    fun `a task that is run shows as running, then as completed`() =
        runBlocking {
            val failed = client.tasks().first { it.lastRun?.status == TaskStatus.Failed }
            client.runTask(failed.id)
            assertTrue(client.tasks().first { it.id == failed.id }.running)
            val done =
                withTimeout(15_000) {
                    while (client.tasks().first { it.id == failed.id }.running) delay(250)
                    client.tasks().first { it.id == failed.id }
                }
            assertEquals(TaskStatus.Completed, done.lastRun?.status)
        }

    @Test
    fun `a library is added, renamed, given a folder and removed`() =
        runBlocking {
            client.addLibrary("Anime", "tvshows", "/media/anime")
            client.renameLibrary("Anime", "Cartoons")
            client.addLibraryPath("Cartoons", "/media/home-videos")
            assertEquals(listOf("/media/anime", "/media/home-videos"), client.libraries().first { it.name == "Cartoons" }.folders)
            client.removeLibrary("Cartoons")
            assertFalse(client.libraries().any { it.name == "Cartoons" })
        }

    @Test
    fun `a library added under a renamed library's old name gets an id of its own`() =
        runBlocking {
            client.renameLibrary("Movies", "Films")
            client.addLibrary("Movies", "movies", "/media/more-movies")
            val ids = client.libraries().map { it.id }
            assertEquals(ids.size, ids.toSet().size)
        }

    @Test
    fun `a backup is made and an API key is made and revoked`() =
        runBlocking {
            val before = client.backups().size
            client.createBackup(BackupOptions(database = true, metadata = true))
            assertEquals(before + 1, client.backups().size)
            val (created, all) = client.createApiKey("Sonarr")
            assertTrue(all.size == 3 && created?.app == "Sonarr")
            client.revokeApiKey(created!!.key)
            assertEquals(2, client.apiKeys().size)
        }

    @Test
    fun `a plugin is switched off and another is uninstalled`() =
        runBlocking {
            val anidb = client.plugins().first { it.name == "AniDB" }
            client.disablePlugin(anidb)
            assertEquals(PluginStatus.Disabled, client.plugins().first { it.name == "AniDB" }.status)
            client.uninstallPlugin(client.plugins().first { it.name == "Playback Reporting" })
            assertFalse(client.plugins().any { it.name == "Playback Reporting" })
        }

    @Test
    fun `the server's configuration is kept as it is saved, and Quick Connect follows it`() =
        runBlocking {
            assertTrue(client.quickConnectEnabled())
            val saved = JsonObject(client.serverConfiguration() + ("QuickConnectAvailable" to JsonPrimitive(false)))
            client.saveServerConfiguration(saved)
            assertFalse(client.quickConnectEnabled())
            assertEquals(false, (client.serverConfiguration()["QuickConnectAvailable"] as JsonPrimitive).content.toBoolean())
        }

    @Test
    fun `a tuner is added and removed`() =
        runBlocking {
            val added =
                client.saveTunerHost(
                    JsonObject(
                        mapOf(
                            "Type" to JsonPrimitive("m3u"),
                            "Url" to JsonPrimitive("http://192.168.1.9/list.m3u"),
                        ),
                    ),
                )
            assertEquals(2, client.liveTvSummary(withGuide = true).tuners.size)
            client.deleteTunerHost((added["Id"] as JsonPrimitive).content)
            assertEquals(1, client.liveTvSummary(withGuide = true).tuners.size)
        }

    @Test
    fun `a log file reads as log entries`() =
        runBlocking {
            val file = client.logFiles().first()
            assertTrue(client.logTail(file.name).entries.isNotEmpty())
        }
}
