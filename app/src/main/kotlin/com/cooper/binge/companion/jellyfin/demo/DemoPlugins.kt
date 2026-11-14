package com.cooper.binge.companion.jellyfin.demo

import com.binge.companion.sdk.attempt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val PLUGINS =
    """
    [{"Id":"a629c3f3d6a64a1e9f5b2d0c1b7e0001","Name":"AniDB","Version":"9.0.0.0","Description":"Get metadata for anime from AniDB.","Status":"Active","CanUninstall":true,"ConfigurationFileName":"Jellyfin.Plugin.AniDB.xml"},
     {"Id":"a629c3f3d6a64a1e9f5b2d0c1b7e0002","Name":"Open Subtitles","Version":"20.0.0.0","Description":"Download subtitles from Open Subtitles.","Status":"Active","CanUninstall":true,"ConfigurationFileName":"Jellyfin.Plugin.OpenSubtitles.xml"},
     {"Id":"a629c3f3d6a64a1e9f5b2d0c1b7e0003","Name":"TMDb","Version":"10.10.7.0","Description":"Get metadata for movies and TV shows from TheMovieDb.","Status":"Active","CanUninstall":false,"ConfigurationFileName":""},
     {"Id":"a629c3f3d6a64a1e9f5b2d0c1b7e0004","Name":"Playback Reporting","Version":"17.0.0.0","Description":"Collects playback statistics.","Status":"Disabled","CanUninstall":true,"ConfigurationFileName":""}]
    """

private const val PACKAGES =
    """
    [{"name":"Playlists","guid":"b0000000000000000000000000000001","description":"Keep playlists in sync.","overview":"Keep playlists in sync with your libraries.","owner":"jellyfin","category":"General",
      "versions":[{"version":"12.0.0.0","changelog":"Fixes sorting.","targetAbi":"10.10.0.0","repositoryName":"Jellyfin Stable","repositoryUrl":"https://repo.jellyfin.org/files/plugin/manifest.json"}]},
     {"name":"Trakt","guid":"b0000000000000000000000000000002","description":"Scrobble what you watch to Trakt.","overview":"Scrobble what you watch to Trakt.","owner":"jellyfin","category":"General",
      "versions":[{"version":"27.0.0.0","changelog":"Now uses the new Trakt API.","targetAbi":"10.10.0.0","repositoryName":"Jellyfin Stable","repositoryUrl":"https://repo.jellyfin.org/files/plugin/manifest.json"}]},
     {"name":"Fanart","guid":"b0000000000000000000000000000003","description":"Get artwork from Fanart.tv.","overview":"Get artwork from Fanart.tv.","owner":"jellyfin","category":"Metadata",
      "versions":[{"version":"11.0.0.0","changelog":"","targetAbi":"10.10.0.0","repositoryName":"Jellyfin Stable","repositoryUrl":"https://repo.jellyfin.org/files/plugin/manifest.json"}]}]
    """

private const val REPOSITORIES =
    """[{"Name":"Jellyfin Stable","Url":"https://repo.jellyfin.org/files/plugin/manifest.json","Enabled":true}]"""

/** The plugins installed, the catalogue they come from, and the repositories that feed it. */
internal class DemoPlugins : DemoState {
    private val plugins: MutableList<JsonObject> = seed()
    private val repositories: MutableList<JsonObject> = (json(REPOSITORIES) as JsonArray).map { it as JsonObject }.toMutableList()

    private fun seed() = (json(PLUGINS) as JsonArray).map { it as JsonObject }.toMutableList()

    override fun reset() {
        plugins.clear()
        plugins.addAll(seed())
        repositories.clear()
        repositories.addAll((json(REPOSITORIES) as JsonArray).map { it as JsonObject })
    }

    override fun routes(): List<DemoRoute> =
        listOf(
            route("GET", "Plugins") { _, _ -> ok(plugins.toList()) },
            route("POST", "Plugins", "{}", "{}", "Enable") { _, (id) -> status(id, "Active") },
            route("POST", "Plugins", "{}", "{}", "Disable") { _, (id) -> status(id, "Disabled") },
            route(
                "DELETE",
                "Plugins",
                "{}",
                "{}",
            ) { _, (id) -> if (plugins.removeAll { it.string("Id") == id }) noContent else demoNotFound },
            route("GET", "web", "ConfigurationPages") { _, _ -> ok(JsonArray(emptyList())) },
            route("GET", "Packages") { _, _ -> ok(json(PACKAGES)) },
            route("POST", "Packages", "Installed", "{}") { call, (name) -> install(name, call.query("version").orEmpty()) },
            route("DELETE", "Packages", "Installing", "{}") { _, _ -> noContent },
            route("GET", "Repositories") { _, _ -> ok(repositories.toList()) },
            route("POST", "Repositories") { call, _ -> saveRepositories(call) },
        )

    private fun status(
        id: String,
        status: String,
    ): DemoAnswer {
        val index = plugins.indexOfFirst { it.string("Id") == id }
        if (index < 0) return demoNotFound
        plugins[index] = plugins[index].with("Status", JsonPrimitive(status))
        return noContent
    }

    /** A plugin installed from the catalogue shows up in the list, waiting for the restart a real server needs. */
    private fun install(
        name: String,
        version: String,
    ): DemoAnswer {
        plugins +=
            jsonObject(
                """{"Id":"${name.hashCode().toUInt().toString(
                    HEX,
                ).padStart(
                    ID_LENGTH,
                    '0',
                )}","Name":"$name","Version":"$version","Description":"","Status":"Restart","CanUninstall":true,"ConfigurationFileName":""}""",
            )
        return noContent
    }

    private fun saveRepositories(call: DemoCall): DemoAnswer {
        val saved = (attempt { json(call.body) as? JsonArray }.getOrNull() ?: return demoNotFound).mapNotNull { it as? JsonObject }
        repositories.clear()
        repositories.addAll(saved)
        return noContent
    }

    private companion object {
        const val HEX = 16
        const val ID_LENGTH = 32
    }
}
