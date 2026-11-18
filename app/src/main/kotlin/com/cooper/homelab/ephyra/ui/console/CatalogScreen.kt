package com.cooper.homelab.ephyra.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchorIf
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.server.CatalogPackage
import com.cooper.homelab.ephyra.server.PackageVersion
import com.cooper.homelab.ephyra.server.compareVersions
import com.binge.designsystem.R as DesR

/** What the catalog tab can ask of its view model. */
data class CatalogActions(
    val onOpen: (String) -> Unit = {},
    val onInstall: (CatalogPackage, PackageVersion?) -> Unit = { _, _ -> },
    val onCancel: (CatalogPackage) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** The catalog: packages by category, the one open beside it marked. A tap opens the package's own screen ([PackageScreen]). */
@Composable
internal fun CatalogTab(
    state: CatalogUiState,
    actions: CatalogActions,
    inner: PaddingValues,
    listState: LazyListState,
    openPackage: String?,
) {
    when (state) {
        CatalogUiState.Loading -> PluginsLoading(inner)
        is CatalogUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry)
        is CatalogUiState.Ready ->
            when {
                state.packages.isEmpty() ->
                    EmptyScreen(
                        message = stringResource(R.string.catalog_empty),
                        title = stringResource(R.string.catalog_empty_title),
                    )
                else -> PackageList(state, actions, inner, listState, openPackage)
            }
    }
}

@Composable
private fun PackageList(
    state: CatalogUiState.Ready,
    actions: CatalogActions,
    inner: PaddingValues,
    listState: LazyListState,
    openPackage: String?,
) {
    LazyColumn(
        state = listState,
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        state.packages.groupBy { it.category }.entries.forEachIndexed { index, (category, packages) ->
            item(key = "category:$category") {
                ItemGroup(
                    title = category.ifBlank { null },
                    rows = packages.map { it.row(state, actions, it.name == openPackage) },
                    modifier = Modifier.layoutAnchorIf(index == 0, LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
                )
            }
        }
    }
}

@Composable
private fun CatalogPackage.row(
    state: CatalogUiState.Ready,
    actions: CatalogActions,
    open: Boolean,
): ListItem {
    val note =
        when {
            name in state.installing -> stringResource(R.string.catalog_installing)
            name in state.installed -> stringResource(R.string.catalog_installed)
            else -> null
        }
    return ListItem(
        icon = Icons.Outlined.Extension,
        label = name,
        detail = description.ifBlank { null },
        note = note?.let { ListItemNote(it, icon = if (name in state.installing) Icons.Outlined.Download else Icons.Outlined.CheckCircle) },
        selected = open,
        onClick = { actions.onOpen(name) },
    )
}

/**
 * One package: what it is and who made it, the version Install would put on this server (the newest built
 * for its release or older), and Install, in the footer, or its progress with Cancel. A package with no such version says so.
 * A screen of its own above the catalog on the stack, so Back returns to it.
 */
@Composable
fun PackageScreen(
    state: CatalogUiState,
    name: String,
    actions: CatalogActions,
    onBack: () -> Unit,
) {
    val ready = state as? CatalogUiState.Ready
    val pkg = ready?.packages?.firstOrNull { it.name == name }
    val gone = ready != null && pkg == null
    LaunchedEffect(gone) { if (gone) onBack() }
    val host = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    BingeScreenScaffold(
        title = pkg?.name ?: stringResource(R.string.plugins_title),
        bar = ScreenBar.Small,
        onBack = onBack,
        snackbarHostState = host,
        bottomBar = {
            val installing = ready != null && pkg != null && pkg.name in ready.installing
            val installed = ready != null && pkg != null && pkg.name in ready.installed
            CreateFooter(
                label = stringResource(if (installed) R.string.catalog_installed else R.string.catalog_install),
                icon = if (installed) Icons.Outlined.CheckCircle else Icons.Outlined.Download,
                onClick = { pkg?.let { actions.onInstall(it, null) } },
                // Always there once the package is known, so the page never moves under a tap: disabled while the install
                // runs and once the plugin is installed, where it reads "Installed".
                visible = ready != null && pkg != null && (pkg.installable(ready.server) != null || installing || installed),
                enabled = !installing && !installed,
                loading = installing,
            )
        },
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when {
                // The bare row skeleton, with no geometry test (#514): a package opens from the loaded catalog, so this shows only
                // after the app is restored with the page open. The settled page starts with the package's overview, a text of
                // unknown length, so no skeleton can reserve where its first group sits.
                state is CatalogUiState.Loading -> PluginsLoading(inner)
                state is CatalogUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry)
                ready != null && pkg != null -> PackageContent(pkg, ready, actions, inner, listState, inlineInstall = false)
            }
            RefusedMessage(ready?.actionFailed, host, R.string.plugins_not_changed)
        }
    }
}

/** What fills a landscape tablet's third pane while no plugin or package is open. */
@Composable
internal fun PluginPlaceholder() {
    EmptyScreen(
        message = stringResource(R.string.plugins_select),
        title = stringResource(R.string.plugins_select_title),
        icon = Icons.Outlined.Extension,
    )
}

@Composable
private fun PackageContent(
    pkg: CatalogPackage,
    state: CatalogUiState.Ready,
    actions: CatalogActions,
    inner: PaddingValues,
    listState: LazyListState,
    inlineInstall: Boolean,
) {
    val version = pkg.installable(state.server)
    val installing = pkg.name in state.installing
    val installId = state.installing[pkg.name]
    LazyColumn(
        state = listState,
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        item { Text(pkg.overview.ifBlank { pkg.description }, style = MaterialTheme.typography.bodyMedium) }
        item {
            ItemGroup(
                title = null,
                rows =
                    listOfNotNull(
                        pkg.owner.takeIf { it.isNotBlank() }?.let {
                            ListItem(
                                icon = Icons.Outlined.Info,
                                label = stringResource(R.string.catalog_owner),
                                detail = it,
                                clickable = false,
                            )
                        },
                        ListItem(
                            icon = Icons.Outlined.Download,
                            label = stringResource(R.string.catalog_version),
                            detail =
                                version?.let { listOf(it.version, it.repositoryName).filter(String::isNotBlank).joinToString(" · ") }
                                    ?: stringResource(R.string.catalog_no_version),
                            supportingMaxLines = 3,
                            clickable = false,
                        ),
                    ),
            )
        }
        version?.changelog?.let { changelog -> item { ChangelogMarkdown(changelog, MaterialTheme.typography.bodySmall) } }
        item { Outcome(state.outcomes[pkg.name]) }
        item {
            when {
                installing ->
                    BingeTextButton(
                        label = stringResource(R.string.catalog_cancel),
                        onClick = { actions.onCancel(pkg) },
                        enabled = installId != null,
                    )
                version != null && pkg.name !in state.installed && inlineInstall ->
                    BingeFilledButton(
                        label = stringResource(R.string.catalog_install),
                        onClick = { actions.onInstall(pkg, null) },
                        leadingIcon = Icons.Outlined.Download,
                        modifier = Modifier.fillMaxWidth(),
                    )
            }
        }
        if (pkg.versions.isNotEmpty()) item { VersionsGroup(pkg, state, actions) }
    }
}

@Composable
private fun Outcome(outcome: InstallOutcome?) {
    val text =
        when (outcome) {
            InstallOutcome.Done -> R.string.catalog_done
            InstallOutcome.Failed -> R.string.catalog_failed
            InstallOutcome.Cancelled -> R.string.catalog_cancelled
            null -> return
        }
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color =
            if (outcome ==
                InstallOutcome.Failed
            ) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
    )
}

/**
 * Every version the catalog lists, newest first, with the date it was published and which is installed. A version built for a newer
 * server than this one shows but can't be opened. A tap opens the version's changelog and its Install ([VersionSheet]).
 */
@Composable
private fun VersionsGroup(
    pkg: CatalogPackage,
    state: CatalogUiState.Ready,
    actions: CatalogActions,
) {
    var open by rememberSaveable(pkg.name) { mutableStateOf<String?>(null) }
    val installedVersion = state.installedVersions[pkg.name]
    val busy = pkg.name in state.installing
    ItemGroup(
        title = stringResource(R.string.catalog_versions),
        rows =
            pkg.history().map { version ->
                val installedNow = installedVersion != null && compareVersions(installedVersion, version.version) == 0
                val fits = version.fits(state.server)
                ListItem(
                    icon = if (installedNow) Icons.Outlined.CheckCircle else Icons.Outlined.Download,
                    label = version.version,
                    detail =
                        if (fits) {
                            version.timestamp?.let(::publishedDate)
                        } else {
                            stringResource(R.string.catalog_needs_newer, version.targetAbi.orEmpty())
                        },
                    note =
                        if (installedNow) {
                            ListItemNote(
                                stringResource(R.string.catalog_installed),
                                icon = Icons.Outlined.CheckCircle,
                            )
                        } else {
                            null
                        },
                    enabled = fits,
                    onClick = { open = version.version },
                )
            },
    )
    pkg.versions.firstOrNull { it.version == open }?.let { version ->
        VersionSheet(
            pkg = pkg,
            version = version,
            installedVersion = installedVersion,
            canInstall =
                version.fits(
                    state.server,
                ) &&
                    !busy &&
                    (installedVersion == null || compareVersions(installedVersion, version.version) != 0),
            onInstall = {
                actions.onInstall(pkg, version)
                open = null
            },
            onDismiss = { open = null },
        )
    }
}

/** The date part of a repository's timestamp (`2026-05-04T10:00:00Z`), or the whole text when it isn't one. */
private fun publishedDate(timestamp: String): String = timestamp.substringBefore('T')

/** One version: its changelog, and Install. Putting an older version over a newer one asks first. */
@Composable
internal fun VersionSheet(
    pkg: CatalogPackage,
    version: PackageVersion,
    installedVersion: String?,
    canInstall: Boolean,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    inWindow: Boolean = true,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val downgrade = installedVersion != null && version.isOlderThan(installedVersion)
    if (confirming) {
        OlderVersionConfirm(
            pkg,
            version,
            installedVersion.orEmpty(),
            onConfirm = {
                confirming = false
                onInstall()
            },
            onDismiss = { confirming = false },
        )
    }
    val body: @Composable () -> Unit = {
        VersionContent(pkg, version, canInstall) { if (downgrade) confirming = true else onInstall() }
    }
    if (inWindow) {
        BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onDismiss) { body() }
    } else {
        body()
    }
}

/** The sheet's body, which draws without a window for a frame. */
@Composable
internal fun VersionContent(
    pkg: CatalogPackage,
    version: PackageVersion,
    canInstall: Boolean,
    onInstall: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(
            "${pkg.name} ${version.version}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val changelog = version.changelog
        if (changelog == null) {
            Text(stringResource(R.string.catalog_no_changelog), style = MaterialTheme.typography.bodyMedium)
        } else {
            ChangelogMarkdown(changelog, MaterialTheme.typography.bodyMedium)
        }
        BingeFilledButton(
            label = stringResource(R.string.catalog_install),
            onClick = onInstall,
            leadingIcon = Icons.Outlined.Download,
            enabled = canInstall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The confirm before an older version goes over a newer one. With [inWindow] false it draws without a window, for a frame. */
@Composable
internal fun OlderVersionConfirm(
    pkg: CatalogPackage,
    version: PackageVersion,
    installedVersion: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.catalog_older_title, version.version),
        message = stringResource(R.string.catalog_older_message, pkg.name, installedVersion),
        confirmLabel = stringResource(R.string.catalog_install),
        onConfirm = onConfirm,
        onDismissRequest = onDismiss,
        inWindow = inWindow,
    )
}
