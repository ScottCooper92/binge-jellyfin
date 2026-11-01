package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.template.EmptyScreen
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.CatalogPackage
import com.binge.designsystem.R as DesR

/** What the catalog tab can ask of its view model. */
data class CatalogActions(
    val onOpen: (String?) -> Unit = {},
    val onInstall: (CatalogPackage) -> Unit = {},
    val onCancel: (CatalogPackage) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** The catalog: packages by category, and a package's own page with Install. */
@Composable
internal fun CatalogTab(
    state: CatalogUiState,
    open: CatalogPackage?,
    actions: CatalogActions,
    inner: PaddingValues,
) {
    when (state) {
        CatalogUiState.Loading -> PluginsLoading(inner)
        is CatalogUiState.Failed -> PluginsFailed(state.failure, actions.onRetry)
        is CatalogUiState.Ready ->
            when {
                open != null -> PackagePage(open, state, actions, inner)
                state.packages.isEmpty() ->
                    EmptyScreen(
                        message = stringResource(R.string.catalog_empty),
                        title = stringResource(R.string.catalog_empty_title),
                    )
                else -> PackageList(state, actions, inner)
            }
    }
}

@Composable
private fun PackageList(
    state: CatalogUiState.Ready,
    actions: CatalogActions,
    inner: PaddingValues,
) {
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        state.packages.groupBy { it.category }.forEach { (category, packages) ->
            item(key = category) {
                ItemGroup(title = category.ifBlank { null }, rows = packages.map { it.row(state, actions) })
            }
        }
        item { FailureLine(state.actionFailed) }
    }
}

@Composable
private fun CatalogPackage.row(
    state: CatalogUiState.Ready,
    actions: CatalogActions,
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
        onClick = { actions.onOpen(name) },
    )
}

/**
 * One package: what it is and who made it, the version Install would put on this server (the newest built
 * for its release or older), and Install, or its progress with Cancel. A package with no such version says so.
 */
@Composable
private fun PackagePage(
    pkg: CatalogPackage,
    state: CatalogUiState.Ready,
    actions: CatalogActions,
    inner: PaddingValues,
) {
    val version = pkg.installable(state.server)
    val installing = pkg.name in state.installing
    val installId = state.installing[pkg.name]
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
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
                version != null && pkg.name !in state.installed ->
                    BingeFilledButton(
                        label = stringResource(R.string.catalog_install),
                        onClick = { actions.onInstall(pkg) },
                        leadingIcon = Icons.Outlined.Download,
                        modifier = Modifier.fillMaxWidth(),
                    )
            }
        }
        item { FailureLine(state.actionFailed) }
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
