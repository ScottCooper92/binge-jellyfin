package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.CatalogPackage
import com.binge.designsystem.R as DesR

/** What the catalog tab can ask of its view model. */
data class CatalogActions(
    val onOpen: (String) -> Unit = {},
    val onInstall: (CatalogPackage) -> Unit = {},
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
        state.packages.groupBy { it.category }.forEach { (category, packages) ->
            item(key = "category:$category") {
                ItemGroup(title = category.ifBlank { null }, rows = packages.map { it.row(state, actions, it.name == openPackage) })
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
            CreateFooter(
                label = stringResource(R.string.catalog_install),
                icon = Icons.Outlined.Download,
                onClick = { pkg?.let(actions.onInstall) },
                visible =
                    ready != null &&
                        pkg != null &&
                        pkg.installable(ready.server) != null &&
                        pkg.name !in ready.installed &&
                        pkg.name !in ready.installing,
            )
        },
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when {
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
        version?.changelog?.let { changelog -> item { Text(changelog, style = MaterialTheme.typography.bodySmall) } }
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
                        onClick = { actions.onInstall(pkg) },
                        leadingIcon = Icons.Outlined.Download,
                        modifier = Modifier.fillMaxWidth(),
                    )
            }
        }
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
