package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What a server that has not finished its first-run setup holds: its name, the language of its own pages, and the language and
 * country it reads metadata for. Kept whole, so a save sends back every field the server sent, changed only where the user chose.
 * These routes are anonymous until `POST /Startup/Complete`, so no token is involved.
 */
data class StartupConfiguration(
    private val json: JsonObject,
) {
    /** Whether the server sent a name. An older server may not, and the step shows no field for one. */
    val hasServerName: Boolean get() = STARTUP_SERVER_NAME in json

    val serverName: String get() = json.string(STARTUP_SERVER_NAME).orEmpty()
    val uiCulture: String get() = json.string(STARTUP_UI_CULTURE).orEmpty()
    val metadataLanguage: String get() = json.string(STARTUP_METADATA_LANGUAGE).orEmpty()
    val metadataCountry: String get() = json.string(STARTUP_METADATA_COUNTRY).orEmpty()

    fun withServerName(name: String) = with(STARTUP_SERVER_NAME, name)

    fun withUiCulture(culture: String) = with(STARTUP_UI_CULTURE, culture)

    fun withMetadataLanguage(language: String) = with(STARTUP_METADATA_LANGUAGE, language)

    fun withMetadataCountry(country: String) = with(STARTUP_METADATA_COUNTRY, country)

    private fun with(
        key: String,
        value: String,
    ) = StartupConfiguration(JsonObject(json + (key to JsonPrimitive(value))))

    internal fun toJson(): JsonObject = json
}

/** The setup configuration as the server holds it now. */
suspend fun JellyfinClient.startupConfiguration(): StartupConfiguration = StartupConfiguration(getObject(STARTUP_CONFIGURATION))

/** Saves [configuration] as the server's setup configuration. */
suspend fun JellyfinClient.saveStartupConfiguration(configuration: StartupConfiguration) {
    post(STARTUP_CONFIGURATION, configuration.toJson())
}

/** The name the server suggests for its first account, or null when it suggests none (an older server may not). */
suspend fun JellyfinClient.startupFirstUserName(): String? = getObject(STARTUP_FIRST_USER).string("Name")?.takeIf { it.isNotBlank() }

/**
 * Creates the server's first account, which is its administrator. The password is sent once, in the body of this request to the
 * server the user chose, and kept nowhere. The route is anonymous until `POST /Startup/Complete`.
 */
suspend fun JellyfinClient.saveStartupUser(
    name: String,
    password: String,
) {
    post(
        STARTUP_USER,
        buildJsonObject {
            put("Name", name)
            put("Password", password)
        },
    )
}

private const val STARTUP_FIRST_USER = "Startup/FirstUser"
private const val STARTUP_USER = "Startup/User"
private const val STARTUP_CONFIGURATION = "Startup/Configuration"
private const val STARTUP_SERVER_NAME = "ServerName"
private const val STARTUP_UI_CULTURE = "UICulture"
private const val STARTUP_METADATA_LANGUAGE = "PreferredMetadataLanguage"
private const val STARTUP_METADATA_COUNTRY = "MetadataCountryCode"
