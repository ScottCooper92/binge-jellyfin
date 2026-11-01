package com.cooper.binge.companion.jellyfin.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One version of a package in the catalog, and the server release it was built for (`targetAbi`). */
data class PackageVersion(
    val version: String,
    val changelog: String?,
    val targetAbi: String?,
    val repositoryName: String,
    val repositoryUrl: String,
) {
    /**
     * Whether this version was built for [server] or an older release, as the server itself checks. A version
     * with no target, or a server whose version can't be read, fits: the server's own answer stays the backstop.
     */
    fun fits(server: JellyfinVersion?): Boolean {
        val abi = JellyfinVersion.parse(targetAbi?.takeIf { it.isNotBlank() }) ?: return true
        return server == null || abi <= server
    }
}

/** A plugin the catalog offers, as `/Packages` lists it. */
data class CatalogPackage(
    val id: String,
    val name: String,
    val description: String,
    val overview: String,
    val owner: String,
    val category: String,
    val versions: List<PackageVersion>,
) {
    /** The newest version built for [server] or older: what Install offers. Null when none fits. */
    fun installable(server: JellyfinVersion?): PackageVersion? =
        versions.filter { it.fits(server) }.maxWithOrNull(compareBy { JellyfinVersion.parse(it.version) ?: JellyfinVersion(0, 0, 0) })
}

/** The catalog, by name. Admin only. */
suspend fun JellyfinClient.packages(): List<CatalogPackage> {
    val answer = get(PACKAGES) as? JsonArray ?: throw JellyfinUnreadableAnswer(PACKAGES)
    return answer.mapNotNull { (it as? JsonObject)?.packageOrNull() }.sortedBy { it.name.lowercase() }
}

private fun JsonObject.packageOrNull(): CatalogPackage? {
    val name = string("name") ?: return null
    return CatalogPackage(
        id = string("guid").orEmpty(),
        name = name,
        description = string("description").orEmpty(),
        overview = string("overview").orEmpty(),
        owner = string("owner").orEmpty(),
        category = string("category").orEmpty(),
        versions = array("versions").mapNotNull { (it as? JsonObject)?.versionOrNull() },
    )
}

private fun JsonObject.versionOrNull(): PackageVersion? {
    val version = string("version") ?: return null
    return PackageVersion(
        version = version,
        changelog = string("changelog")?.takeIf { it.isNotBlank() },
        targetAbi = string("targetAbi"),
        repositoryName = string("repositoryName").orEmpty(),
        repositoryUrl = string("repositoryUrl").orEmpty(),
    )
}

/** Installs [version] of [pkg] from its repository. The server downloads it in the background; see [installEvents]. */
suspend fun JellyfinClient.installPackage(
    pkg: CatalogPackage,
    version: PackageVersion,
) {
    val query =
        buildMap {
            put("version", version.version)
            if (pkg.id.isNotBlank()) put("assemblyGuid", pkg.id)
            if (version.repositoryUrl.isNotBlank()) put("repositoryUrl", version.repositoryUrl)
        }
    post("Packages/Installed/${pkg.name}", null, query)
}

/** Cancels the install the server calls [installId]. */
suspend fun JellyfinClient.cancelInstall(installId: String) {
    delete("Packages/Installing/$installId")
}

/** How an install the server told this socket about is going. [id] is what [cancelInstall] takes. */
sealed interface InstallEvent {
    val name: String

    data class Started(
        val id: String,
        override val name: String,
    ) : InstallEvent

    data class Done(
        override val name: String,
    ) : InstallEvent

    data class Failed(
        override val name: String,
    ) : InstallEvent

    data class Cancelled(
        override val name: String,
    ) : InstallEvent
}

/**
 * The installs the server reports, on a socket of the caller's own. The server sends them to every
 * administrator's socket without being asked, so nothing is subscribed to.
 */
fun JellyfinClient.installEvents(): Flow<InstallEvent> = socketMessages().mapNotNull { it.installEvent() }

internal fun JsonObject.installEvent(): InstallEvent? {
    val info = this["Data"] as? JsonObject ?: return null
    val name = info.string("Name") ?: return null
    return when (string("MessageType")) {
        "PackageInstalling" -> info.string("Guid")?.let { InstallEvent.Started(it, name) }
        "PackageInstallationCompleted" -> InstallEvent.Done(name)
        "PackageInstallationFailed" -> InstallEvent.Failed(name)
        "PackageInstallationCancelled" -> InstallEvent.Cancelled(name)
        else -> null
    }
}

/**
 * A plugin repository, as `/Repositories` lists them. [raw] is the entry as the server sent it, so a save
 * sends back what this app doesn't read.
 */
data class PluginRepository(
    val name: String,
    val url: String,
    val enabled: Boolean,
    val raw: JsonObject? = null,
) {
    fun toJson(): JsonObject =
        raw ?: JsonObject(mapOf("Name" to JsonPrimitive(name), "Url" to JsonPrimitive(url), "Enabled" to JsonPrimitive(enabled)))
}

/** The repositories the server fetches its catalog from. Admin only. */
suspend fun JellyfinClient.repositories(): List<PluginRepository> {
    val answer = get(REPOSITORIES) as? JsonArray ?: throw JellyfinUnreadableAnswer(REPOSITORIES)
    return answer.mapNotNull { element ->
        val json = element as? JsonObject ?: return@mapNotNull null
        val url = json.string("Url") ?: return@mapNotNull null
        PluginRepository(json.string("Name").orEmpty(), url, json.bool("Enabled") != false, json)
    }
}

/** Replaces the repositories with [repositories]: the server takes the whole list, so one left out is removed. */
suspend fun JellyfinClient.saveRepositories(repositories: List<PluginRepository>) {
    post(REPOSITORIES, JsonArray(repositories.map { it.toJson() }))
}

private const val PACKAGES = "Packages"
private const val REPOSITORIES = "Repositories"
