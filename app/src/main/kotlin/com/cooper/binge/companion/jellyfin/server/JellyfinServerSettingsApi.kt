package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** The server's general settings, from its configuration (`/System/Configuration`). Admin only. */
data class ServerSettings(
    val name: String = "",
    /** The two-letter language metadata is fetched in, as the server stores it (`en`). */
    val metadataLanguage: String = "",
    /** The two-letter country ratings and release dates are read for (`US`). */
    val metadataCountry: String = "",
    val quickConnect: Boolean = true,
)

/** The login screen's branding (`/System/Configuration/branding`). Admin only. */
data class Branding(
    val loginDisclaimer: String = "",
    val customCss: String = "",
    /** Jellyfin's web client shows a splash screen while it loads. Set from 10.9. */
    val splashscreen: Boolean = false,
)

/** A country the server can read metadata for. */
data class Country(
    val code: String,
    val displayName: String,
)

internal const val SERVER_NAME = "ServerName"
internal const val METADATA_LANGUAGE = "PreferredMetadataLanguage"
internal const val METADATA_COUNTRY = "MetadataCountryCode"
internal const val QUICK_CONNECT_AVAILABLE = "QuickConnectAvailable"
internal const val LOGIN_DISCLAIMER = "LoginDisclaimer"
internal const val CUSTOM_CSS = "CustomCss"
internal const val SPLASHSCREEN_ENABLED = "SplashscreenEnabled"

fun JsonObject.serverSettings(): ServerSettings =
    ServerSettings(
        name = string(SERVER_NAME).orEmpty(),
        metadataLanguage = string(METADATA_LANGUAGE).orEmpty(),
        metadataCountry = string(METADATA_COUNTRY).orEmpty(),
        quickConnect = bool(QUICK_CONNECT_AVAILABLE) != false,
    )

fun JsonObject.branding(): Branding =
    Branding(
        loginDisclaimer = string(LOGIN_DISCLAIMER).orEmpty(),
        customCss = string(CUSTOM_CSS).orEmpty(),
        splashscreen = bool(SPLASHSCREEN_ENABLED) == true,
    )

/** The server's whole configuration, kept whole so a save sends back what it didn't change. */
suspend fun JellyfinClient.serverConfiguration(): JsonObject = getObject(SYSTEM_CONFIGURATION)

/** Replaces the server's configuration with [configuration]. */
suspend fun JellyfinClient.saveServerConfiguration(configuration: JsonObject) {
    post(SYSTEM_CONFIGURATION, configuration)
}

/** The branding configuration, kept whole for the same reason. */
suspend fun JellyfinClient.brandingConfiguration(): JsonObject = getObject(BRANDING)

suspend fun JellyfinClient.saveBranding(configuration: JsonObject) {
    post(BRANDING, configuration)
}

/** The countries the server knows, as `/Localization/Countries` lists them. */
suspend fun JellyfinClient.countries(): List<Country> {
    val answer = get(COUNTRIES) as? JsonArray ?: throw JellyfinUnreadableAnswer(COUNTRIES)
    return answer
        .mapNotNull { element ->
            val json = element as? JsonObject ?: return@mapNotNull null
            val code = json.string("TwoLetterISORegionName")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            Country(code, json.string("DisplayName") ?: code)
        }.distinctBy { it.code }
}

/** Shuts the server down. It doesn't come back by itself: someone has to start it where it runs. */
suspend fun JellyfinClient.shutdownServer() {
    post("System/Shutdown", null)
}

private const val SYSTEM_CONFIGURATION = "System/Configuration"
private const val BRANDING = "System/Configuration/branding"
private const val COUNTRIES = "Localization/Countries"
