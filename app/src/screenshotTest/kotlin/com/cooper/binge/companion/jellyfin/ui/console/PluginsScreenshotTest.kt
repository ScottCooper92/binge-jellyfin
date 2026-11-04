package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.LocalIsSinglePaneNav
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinWidePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinWideSpanishPreview
import com.cooper.binge.companion.jellyfin.server.CatalogPackage
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import com.cooper.binge.companion.jellyfin.server.PackageVersion
import com.cooper.binge.companion.jellyfin.server.PluginRepository
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerPlugin

private fun plugin(
    id: String,
    name: String,
    status: PluginStatus = PluginStatus.Active,
    settings: Boolean = true,
    description: String = "",
) = ServerPlugin(id, name, "1.2.3.4", description, status, canUninstall = true, hasSettings = settings)

private val installedPlugins =
    listOf(
        plugin("a", "AniDB", description = "Fetches anime metadata and artwork from AniDB."),
        plugin("b", "Playback Reporting", PluginStatus.Disabled),
        plugin("c", "Open Subtitles", PluginStatus.Restart, settings = false),
        plugin("d", "Old thing", PluginStatus.Superseded, settings = false),
    )

private val version =
    PackageVersion("13.0.0.0", "Fixes the report on a slow disk.", "10.10.0.0", "Jellyfin Stable", "https://repo.example/stable.json")

private val catalogPackages =
    listOf(
        CatalogPackage(
            "g1",
            "Playback Reporting",
            "Reports who watched what.",
            "Shows who watched what, and when, across every user.",
            "jellyfin",
            "General",
            listOf(version),
        ),
        CatalogPackage("g2", "Trakt", "Syncs watched state with Trakt.", "", "jellyfin", "General", listOf(version)),
        CatalogPackage("g3", "AniDB", "Anime metadata.", "", "jellyfin", "Metadata", listOf(version)),
        CatalogPackage(
            "g4",
            "Future Plugin",
            "Needs a newer server.",
            "",
            "someone",
            "Metadata",
            listOf(version.copy(targetAbi = "10.12.0.0")),
        ),
    )

private val repositories =
    listOf(
        PluginRepository("Jellyfin Stable", "https://repo.jellyfin.org/files/plugin/manifest.json", true),
        PluginRepository("My own", "https://plugins.example.net/manifest.json", true),
    )

private val server = JellyfinVersion.parse("10.10.7")

@Composable
private fun Plugins(
    installed: PluginsUiState = PluginsUiState.Ready(installedPlugins),
    catalog: CatalogUiState = CatalogUiState.Ready(catalogPackages, installed = setOf("AniDB"), server = server),
    repos: RepositoriesUiState = RepositoriesUiState.Ready(repositories),
) = PluginsScreen(installed, catalog, repos, PluginsActions(), CatalogActions(), RepositoriesActions(), onBack = {})

/** The plugins page: the installed list across the matrix, then a plugin's page, the catalog, the repositories and each sheet and confirm. */
class PluginsScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun installed() = Plugins()

    /** A pull in progress: the list stays, with the pull spinner over it. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun installedRefreshing() = Plugins(installed = PluginsUiState.Ready(installedPlugins, refreshing = true))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun installedQuiet() = Plugins(installed = PluginsUiState.Ready(installedPlugins.filter { it.status != PluginStatus.Restart }))

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun plugin() = Plugins(installed = PluginsUiState.Ready(installedPlugins, open = "a"))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun pluginCannotRun() = Plugins(installed = PluginsUiState.Ready(installedPlugins, open = "d"))

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun uninstalling() = Surface { UninstallDialog(installedPlugins.first(), PluginsActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun installedEmpty() = Plugins(installed = PluginsUiState.Ready(emptyList()))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun installedFailed() = Plugins(installed = PluginsUiState.Failed(ConsoleFailure.Unreachable))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = Plugins(installed = PluginsUiState.Loading)

    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinSpanishPreviews
    @Composable
    fun catalog() = CatalogTabPreview(CatalogUiState.Ready(catalogPackages, installed = setOf("AniDB"), server = server))

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun catalogPackage() = CatalogTabPreview(CatalogUiState.Ready(catalogPackages, open = "Playback Reporting", server = server))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun catalogInstalling() =
        CatalogTabPreview(
            CatalogUiState.Ready(
                catalogPackages,
                open = "Playback Reporting",
                installing = mapOf("Playback Reporting" to "install-1"),
                server = server,
            ),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun catalogDone() =
        CatalogTabPreview(
            CatalogUiState.Ready(
                catalogPackages,
                open = "Playback Reporting",
                installed = setOf("Playback Reporting"),
                outcomes = mapOf("Playback Reporting" to InstallOutcome.Done),
                server = server,
            ),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun catalogNoVersion() = CatalogTabPreview(CatalogUiState.Ready(catalogPackages, open = "Future Plugin", server = server))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun catalogEmpty() = CatalogTabPreview(CatalogUiState.Ready(emptyList(), server = server))

    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinSpanishPreviews
    @Composable
    fun repositories() = RepositoriesTabPreview(RepositoriesUiState.Ready(repositories))

    /** A pull in progress: the list stays, with the pull spinner over it. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun repositoriesRefreshing() = RepositoriesTabPreview(RepositoriesUiState.Ready(repositories, refreshing = true))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun repositoriesEmpty() = RepositoriesTabPreview(RepositoriesUiState.Ready(emptyList()))

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun addRepositoryName() = Surface { AddRepositoryForm(saving = false, actions = RepositoriesActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun addRepositoryAddress() = Surface { AddRepositoryForm(saving = false, actions = RepositoriesActions(), startStep = 1) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun removingRepository() = Surface { RemoveRepositoryDialog(repositories.first(), RepositoriesActions(), inWindow = false) }
}

/** The page opened on its catalog tab. */
@Composable
private fun CatalogTabPreview(catalog: CatalogUiState) = TabOpened(1, catalog = catalog)

/** The page opened on its repositories tab. */
@Composable
private fun RepositoriesTabPreview(repos: RepositoriesUiState) = TabOpened(2, repos = repos)

@Composable
private fun TabOpened(
    tab: Int,
    catalog: CatalogUiState = CatalogUiState.Ready(catalogPackages, server = server),
    repos: RepositoriesUiState = RepositoriesUiState.Ready(repositories),
) = PluginsScreen(
    PluginsUiState.Ready(installedPlugins),
    catalog,
    repos,
    PluginsActions(),
    CatalogActions(),
    RepositoriesActions(),
    onBack = {},
    initialTab = tab,
)

@Composable
private fun SplitPlugins(
    installed: PluginsUiState = PluginsUiState.Ready(installedPlugins),
    catalog: CatalogUiState = CatalogUiState.Ready(catalogPackages, server = server),
    tab: Int = 0,
) = CompositionLocalProvider(LocalPageSpansTwoPanes provides true, LocalIsSinglePaneNav provides false) {
    PluginsScreen(
        installed,
        catalog,
        RepositoriesUiState.Ready(repositories),
        PluginsActions(),
        CatalogActions(),
        RepositoriesActions(),
        onBack = {},
        initialTab = tab,
    )
}

/** The plugins page on a window with room for two panes: the tabs beside the open plugin or package, or beside the invitation. */
class PluginsSplitScreenshotTest {
    /** A plugin open: its name and page, and its row marked in the installed list. */
    @PreviewTest
    @JellyfinWidePreview
    @JellyfinWideSpanishPreview
    @Composable
    fun pluginOpen() = SplitPlugins(PluginsUiState.Ready(installedPlugins, open = "a"))

    /** Nothing open: the installed list beside the invitation to pick one. */
    @PreviewTest
    @JellyfinWidePreview
    @JellyfinWideSpanishPreview
    @Composable
    fun nothingOpen() = SplitPlugins()

    /** A catalog package open: the catalog tab beside its page, with Install. */
    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun packageOpen() = SplitPlugins(catalog = CatalogUiState.Ready(catalogPackages, open = "Playback Reporting", server = server), tab = 1)
}
