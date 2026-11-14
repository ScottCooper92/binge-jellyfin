package com.cooper.binge.companion.jellyfin.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal const val DEMO_USER_ID = "d3m00000000000000000000000000001"
internal const val DEMO_USER_NAME = "Alex"

private const val MOVIES = 128
private const val SERIES = 34
private const val WITHOUT_TMDB_ID = 0
private const val TICKS_PER_MINUTE = 600_000_000L
private const val EPISODES = 612
private const val RUNTIME_MINUTES = 118
private const val POSITION_MINUTES = 42

/**
 * The demo server's state and what it answers. One instance lives as long as the process. Pages added in later slices
 * bring their routes and their state here, and a way to put the sample data back when the demo is left.
 */
internal class DemoModel {
    private val routes: List<DemoRoute> by lazy { serverRoutes() }

    @Synchronized
    fun answer(call: DemoCall): DemoAnswer = routes.answer(call) ?: notInTheDemo

    private fun serverRoutes(): List<DemoRoute> =
        listOf(
            route("GET", "System", "Info", "Public") { _, _ -> ok(publicInfo()) },
            route("GET", "System", "Info") { _, _ -> ok(systemInfo()) },
            route("GET", "QuickConnect", "Enabled") { _, _ -> ok(JsonPrimitive(false)) },
            route("POST", "Users", "AuthenticateByName") { _, _ -> ok(authentication()) },
            route("POST", "Sessions", "Logout") { _, _ -> noContent },
            route("GET", "Users", "Me") { _, _ -> ok(administrator()) },
            route("GET", "Users") { _, _ -> ok(users()) },
            route("GET", "Sessions") { _, _ -> ok(sessions()) },
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
            put("User", administrator())
        }

    private fun administrator() = user(DEMO_USER_ID, DEMO_USER_NAME, admin = true)

    private fun users() =
        listOf(
            administrator(),
            user("d3m00000000000000000000000000002", "Sam", admin = false),
            user("d3m00000000000000000000000000003", "Kids", admin = false),
        )

    private fun user(
        id: String,
        name: String,
        admin: Boolean,
    ) = buildJsonObject {
        put("Id", id)
        put("Name", name)
        put("ServerId", DEMO_SERVER_ID)
        put("HasPassword", true)
        putJsonObject("Policy") {
            put("IsAdministrator", admin)
            put("IsDisabled", false)
            put("EnableUserPreferenceAccess", true)
            put("EnableRemoteControlOfOtherUsers", admin)
            put("EnableSharedDeviceControl", admin)
            put("EnableLiveTvManagement", admin)
            put("EnableLiveTvAccess", true)
            put("AuthenticationProviderId", "Jellyfin.Server.Implementations.Users.DefaultAuthenticationProvider")
        }
    }

    private fun sessions() =
        listOf(
            buildJsonObject {
                put("Id", "demo-session-1")
                put("UserId", "d3m00000000000000000000000000002")
                put("UserName", "Sam")
                put("DeviceName", "Living room TV")
                put("DeviceId", "demo-device-1")
                put("Client", "Jellyfin for Android TV")
                put("SupportsMediaControl", true)
                put("SupportsRemoteControl", true)
                putJsonObject("NowPlayingItem") {
                    put("Id", "demo-item-1")
                    put("Name", "The Long Voyage")
                    put("Type", "Movie")
                    put("RunTimeTicks", RUNTIME_MINUTES * TICKS_PER_MINUTE)
                }
                putJsonObject("PlayState") {
                    put("PositionTicks", POSITION_MINUTES * TICKS_PER_MINUTE)
                    put("IsPaused", false)
                    put("CanSeek", true)
                    put("PlayMethod", "DirectPlay")
                }
                putJsonArray("SupportedCommands") {}
            },
        )

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
