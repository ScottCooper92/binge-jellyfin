package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.feedback.openInBrowser
import com.binge.designsystem.R as DesR

private const val LICENCE_DETAIL_MAX_LINES = 2
private const val LOADING_ROWS = 8

/** Rows per group: the page is lazy by group, so a few hundred libraries are never all composed at once. */
internal const val LICENCE_GROUP_SIZE = 12

/** The open-source licences page, with a view model per opening that reads the shipped list once. */
@Composable
internal fun LicencesRoute(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val licences: LicencesViewModel = viewModel(factory = viewModelFactory { initializer { LicencesViewModel(graph.licences) } })
    val state by licences.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LicencesScreen(state = state, onBack = onBack, onOpenWebsite = context::openInBrowser)
}

/** Every library the app ships, with its version and licence, each linking to its own site. */
@Composable
internal fun LicencesScreen(
    state: LicencesUiState,
    onBack: () -> Unit,
    onOpenWebsite: (String) -> Unit,
) {
    BingeScreenScaffold(title = stringResource(R.string.licences_title), bar = ScreenBar.Small, onBack = onBack) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                LicencesUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                // Not ErrorScreen: the list is bundled at build time, so Retry could never succeed.
                LicencesUiState.Unreadable -> EmptyLicences(inner, R.string.licences_unreadable)
                is LicencesUiState.Ready ->
                    if (state.entries.isEmpty()) {
                        EmptyLicences(
                            inner,
                            R.string.licences_empty,
                        )
                    } else {
                        LicenceList(state.entries, inner, onOpenWebsite)
                    }
            }
        }
    }
}

@Composable
private fun EmptyLicences(
    inner: PaddingValues,
    message: Int,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = inner) {
        item { ConsoleEmptyScreen(stringResource(message), Icons.Outlined.Description) }
    }
}

@Composable
private fun LicenceList(
    entries: List<LicenceEntry>,
    inner: PaddingValues,
    onOpenWebsite: (String) -> Unit,
) {
    val unspecified = stringResource(R.string.licences_unspecified)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        itemsIndexed(entries.chunked(LICENCE_GROUP_SIZE), key = { index, _ -> index }) { _, group ->
            ItemGroup(title = null, rows = group.map { it.toRow(unspecified, onOpenWebsite) })
        }
    }
}

@Composable
private fun LicenceEntry.toRow(
    unspecified: String,
    onOpenWebsite: (String) -> Unit,
): ListItem {
    val licenceNames = licences.joinToString().ifEmpty { unspecified }
    val link = website
    return ListItem(
        icon = Icons.Outlined.Description,
        label = name,
        detail = version?.let { stringResource(R.string.licences_detail_format, it, licenceNames) } ?: licenceNames,
        // The licence names are what the row is for, so they may wrap rather than lose their end.
        supportingMaxLines = LICENCE_DETAIL_MAX_LINES,
        clickable = link != null,
        destination = ListItemDestination.External,
        onClick = { link?.let(onOpenWebsite) },
    )
}
