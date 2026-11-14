package com.cooper.binge.companion.jellyfin.demo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant

private const val TV_MINUTES_AGO = 12L
private const val TABLET_HOURS_AGO = 5L
private const val LAPTOP_DAYS_AGO = 2L

/** The devices signed in to the server. They can be renamed and removed while the demo is open. */
internal class DemoDevices : DemoState {
    private val seen = Instant.now()
    private val devices: MutableList<JsonObject> = seed()
    private val customNames: MutableMap<String, String> = mutableMapOf()

    private fun seed() =
        mutableListOf(
            device("demo-session-3-device", "Pixel", "Ephyra", DEMO_USER_NAME, Duration.ZERO),
            device("demo-session-1-device", "Living room TV", "Jellyfin for Android TV", "Sam", Duration.ofMinutes(TV_MINUTES_AGO)),
            device("demo-session-2-device", "Tablet", "Jellyfin for Android", "Kids", Duration.ofHours(TABLET_HOURS_AGO)),
            device("demo-device-4", "Office laptop", "Jellyfin Web", "Alex", Duration.ofDays(LAPTOP_DAYS_AGO)),
        )

    override fun reset() {
        devices.clear()
        devices.addAll(seed())
        customNames.clear()
    }

    override fun routes(): List<DemoRoute> =
        listOf(
            route("GET", "Devices", "Info") { call, _ -> info(call.query("id")) },
            route("POST", "Devices", "Options") { call, _ -> rename(call.query("id"), call.jsonBody().string("CustomName").orEmpty()) },
            route(
                "GET",
                "Devices",
            ) { _, _ -> ok(buildJsonObject { put("Items", kotlinx.serialization.json.JsonArray(devices.toList())) }) },
            route("DELETE", "Devices") { call, _ -> remove(call.query("id")) },
        )

    private fun info(id: String?): DemoAnswer {
        if (devices.none { it.string("Id") == id }) return demoNotFound
        return ok(buildJsonObject { customNames[id]?.let { put("CustomName", it) } })
    }

    private fun rename(
        id: String?,
        name: String,
    ): DemoAnswer {
        if (id == null || devices.none { it.string("Id") == id }) return demoNotFound
        if (name.isBlank()) customNames.remove(id) else customNames[id] = name
        return noContent
    }

    private fun remove(id: String?): DemoAnswer = if (devices.removeAll { it.string("Id") == id }) noContent else demoNotFound

    private fun device(
        id: String,
        name: String,
        app: String,
        user: String,
        ago: Duration,
    ) = buildJsonObject {
        put("Id", id)
        put("Name", name)
        put("AppName", app)
        put("LastUserName", user)
        put("DateLastActivity", seen.minus(ago).toString())
    }
}
