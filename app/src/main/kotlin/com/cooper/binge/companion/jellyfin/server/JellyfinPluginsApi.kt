package com.cooper.binge.companion.jellyfin.server

import com.binge.companion.sdk.attempt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Where a plugin stands, as `Status` says. [Restart] is a change waiting for the server to restart. The
 * server's `Superceded` (its own spelling) and 10.11's `Superseded` read the same; a status this app
 * doesn't know reads as [Unknown].
 */
enum class PluginStatus {
    Active,
    Restart,
    Disabled,
    NotSupported,
    Malfunctioned,
    Superseded,
    Deleted,
    Unknown,
    ;

    /** Whether the plugin can be switched on or off: one that is running or turned off, not one that can't run at all. */
    val canToggle: Boolean get() = this == Active || this == Disabled || this == Restart
}

/** A plugin the server has installed, as `/Plugins` lists it. Admin only. */
data class ServerPlugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val status: PluginStatus,
    val canUninstall: Boolean,
    /** Whether the plugin has a settings file, which means it may have a settings page. */
    val hasSettings: Boolean,
) {
    /** Running or waiting for a restart to run: the state a switch shows as on. */
    val enabled: Boolean get() = status != PluginStatus.Disabled
}

/** The server's installed plugins, by name. */
suspend fun JellyfinClient.plugins(): List<ServerPlugin> {
    val answer = get(PLUGINS) as? JsonArray ?: throw JellyfinUnreadableAnswer(PLUGINS)
    return answer.mapNotNull { (it as? JsonObject)?.pluginOrNull() }.sortedBy { it.name.lowercase() }
}

private fun JsonObject.pluginOrNull(): ServerPlugin? {
    val id = string("Id") ?: return null
    val version = string("Version") ?: return null
    return ServerPlugin(
        id = id,
        name = string("Name").orEmpty(),
        version = version,
        description = string("Description").orEmpty(),
        status = pluginStatus(string("Status")),
        canUninstall = bool("CanUninstall") != false,
        hasSettings = !string("ConfigurationFileName").isNullOrBlank(),
    )
}

internal fun pluginStatus(name: String?): PluginStatus =
    when (name) {
        "Superceded", "Superseded" -> PluginStatus.Superseded
        else -> PluginStatus.entries.firstOrNull { it.name == name && it != PluginStatus.Unknown } ?: PluginStatus.Unknown
    }

/** Switches [plugin] on. It takes effect when the server restarts. */
suspend fun JellyfinClient.enablePlugin(plugin: ServerPlugin) {
    post("Plugins/${plugin.id}/${plugin.version}/Enable", null)
}

/** Switches [plugin] off. It takes effect when the server restarts. */
suspend fun JellyfinClient.disablePlugin(plugin: ServerPlugin) {
    post("Plugins/${plugin.id}/${plugin.version}/Disable", null)
}

/** Uninstalls [plugin]'s version. It goes when the server restarts. */
suspend fun JellyfinClient.uninstallPlugin(plugin: ServerPlugin) {
    delete("Plugins/${plugin.id}/${plugin.version}")
}

/**
 * The page the server serves for [plugin]'s settings, as the name the web client opens it by, or null if
 * it has none. The page is Jellyfin web's own and is not drawn here (see [pluginSettingsUrl]).
 */
suspend fun JellyfinClient.pluginSettingsPage(plugin: ServerPlugin): String? {
    val pages = attempt { get(CONFIGURATION_PAGES) as? JsonArray }.getOrNull() ?: return null
    return pages
        .mapNotNull { it as? JsonObject }
        .firstOrNull { it.string("PluginId")?.replace("-", "").equals(plugin.id.replace("-", ""), ignoreCase = true) }
        ?.string("Name")
}

/**
 * Jellyfin web's address for a plugin settings page, on the user's own server. It carries no token: the
 * web client signs in as it always does, in the browser.
 */
fun JellyfinClient.pluginSettingsUrl(page: String): String =
    url("web/index.html")
        .newBuilder()
        .fragment("!/configurationpage?name=${java.net.URLEncoder.encode(page, "UTF-8")}")
        .build()
        .toString()

private const val PLUGINS = "Plugins"
private const val CONFIGURATION_PAGES = "web/ConfigurationPages"
