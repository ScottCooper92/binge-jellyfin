package com.cooper.binge.companion.jellyfin.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MOVIES = 128
private const val SERIES = 34
private const val WITHOUT_TMDB_ID = 0
private const val EPISODES = 612

/**
 * The demo server's state and what it answers. One instance lives as long as the process. Each page's routes and state
 * live in a `DemoState`, which can put its sample data back when the demo is left.
 */
internal class DemoModel {
    private val users = DemoUsers()
    private val sessions = DemoSessions()
    private val devices = DemoDevices()
    private val activity = DemoActivity(users)
    private val libraries = DemoLibraries()
    private val lookups = DemoLookups(libraries)
    private val tasks = DemoTasks()
    private val files = DemoServerFiles()
    private val plugins = DemoPlugins()
    private val configuration = DemoConfiguration()
    private val states: List<DemoState> =
        listOf(users, sessions, devices, activity, libraries, lookups, tasks, files, plugins, configuration)

    private val routes: List<DemoRoute> by lazy {
        serverRoutes() + states.flatMap { it.routes() }
    }

    @Synchronized
    fun answer(call: DemoCall): DemoAnswer = routes.answer(call) ?: notInTheDemo

    /** Puts every change back: the sample data as it was when the demo was opened. */
    @Synchronized
    fun reset() = states.forEach(DemoState::reset)

    private fun serverRoutes(): List<DemoRoute> =
        listOf(
            route("GET", "System", "Info", "Public") { _, _ -> ok(publicInfo()) },
            route("GET", "System", "Info") { _, _ -> ok(systemInfo()) },
            route("GET", "QuickConnect", "Enabled") { _, _ -> ok(JsonPrimitive(configuration.quickConnectAvailable())) },
            route("POST", "Users", "AuthenticateByName") { _, _ -> ok(authentication()) },
            route("POST", "QuickConnect", "Authorize") { _, _ -> noContent },
            route("POST", "Sessions", "Logout") { _, _ -> reset().let { noContent } },
            route("GET", "Items", "Counts") { _, _ -> ok(itemCounts()) },
            route("GET", "Items") { call, _ -> ok(items(call)) },
        )

    private fun publicInfo() =
        buildJsonObject {
            put("ServerName", DEMO_SERVER_NAME)
            put("Version", DEMO_VERSION)
            put("Id", DEMO_SERVER_ID)
            put("LocalAddress", DEMO_ORIGIN)
            put("ProductName", "Jellyfin Server")
            put("StartupWizardCompleted", true)
        }

    private fun systemInfo() =
        buildJsonObject {
            put("ServerName", DEMO_SERVER_NAME)
            put("Version", DEMO_VERSION)
            put("Id", DEMO_SERVER_ID)
            put("LocalAddress", DEMO_ORIGIN)
            put("SystemArchitecture", "Arm64")
            put("OperatingSystemDisplayName", "Linux")
            put("HasUpdateAvailable", false)
            put("HasPendingRestart", false)
            put("CanSelfRestart", true)
            put("IsShuttingDown", false)
        }

    private fun authentication() =
        buildJsonObject {
            put("AccessToken", DEMO_TOKEN)
            put("ServerId", DEMO_SERVER_ID)
            put("User", users.administrator())
        }

    private fun itemCounts() =
        buildJsonObject {
            put("MovieCount", MOVIES)
            put("SeriesCount", SERIES)
            put("EpisodeCount", EPISODES)
        }

    /**
     * Item queries. The library-index read (`hasTmdbId=true`, with the items) answers empty, so the exported Service has
     * no demo titles to tell Binge about. The coverage reads ask for one record and want the total.
     */
    private fun items(call: DemoCall) =
        buildJsonObject {
            put("Items", JsonArray(emptyList()))
            put("StartIndex", 0)
            val total =
                when (call.query("hasTmdbId")) {
                    "true" -> 0
                    "false" -> WITHOUT_TMDB_ID
                    else -> MOVIES + SERIES
                }
            put("TotalRecordCount", total)
        }
}
