package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ProviderGroup
import com.cooper.binge.companion.jellyfin.server.providers
import com.binge.designsystem.R as DesR

/** The words for a group of providers. */
internal fun ProviderGroup.title(): Int =
    when (this) {
        ProviderGroup.Readers -> R.string.library_readers
        ProviderGroup.Subtitles -> R.string.library_subtitle_providers
        ProviderGroup.Lyrics -> R.string.library_lyric_providers
        ProviderGroup.Segments -> R.string.library_segment_providers
    }

/** A group's providers in the order the library asks them, each opening its own page, as the Home screen's libraries do. */
@Composable
internal fun ProvidersPage(
    group: ProviderGroup,
    state: LibraryUiState.Ready,
    actions: LibraryActions,
    inner: PaddingValues,
) {
    val all = state.options.providers(group, group.available(state.available))
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        Text(stringResource(R.string.library_providers_body), style = MaterialTheme.typography.bodyMedium)
        ItemGroup(
            title = null,
            rows =
                all.map { provider ->
                    ListItem(
                        icon = Icons.Outlined.Extension,
                        label = provider.name,
                        detail = stringResource(if (provider.enabled) R.string.library_provider_on else R.string.library_provider_off),
                        onClick = { actions.onPage(LibraryPage.One(group, provider.name)) },
                    )
                },
        )
        FailureText(state.saveFailed?.let { stringResource(R.string.settings_not_saved, stringResource(it.message)) })
    }
}

/** One provider: whether the library uses it, and its place in the order. */
@Composable
internal fun ProviderPage(
    page: LibraryPage.One,
    state: LibraryUiState.Ready,
    actions: LibraryActions,
    inner: PaddingValues,
) {
    val all = state.options.providers(page.group, page.group.available(state.available))
    val place = all.indexOfFirst { it.name.equals(page.name, ignoreCase = true) }
    val provider = all.getOrNull(place)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        if (provider != null) {
            ItemGroup(
                title = null,
                rows =
                    listOf(
                        ListItem(
                            icon = Icons.Outlined.PowerSettingsNew,
                            label = stringResource(R.string.library_provider_use),
                            toggled = provider.enabled,
                            onClick = { actions.onProvider(page.group, provider.name, !provider.enabled) },
                        ),
                    ),
            )
            ItemGroup(
                title = stringResource(R.string.home_layout_order),
                rows =
                    listOf(
                        ListItem(
                            icon = Icons.Outlined.ArrowUpward,
                            label = stringResource(R.string.home_layout_move_up),
                            enabled = place > 0,
                            destination = ListItemDestination.Action,
                            onClick = { actions.onMove(page.group, provider.name, -1) },
                        ),
                        ListItem(
                            icon = Icons.Outlined.ArrowDownward,
                            label = stringResource(R.string.home_layout_move_down),
                            enabled = place < all.lastIndex,
                            destination = ListItemDestination.Action,
                            onClick = { actions.onMove(page.group, provider.name, 1) },
                        ),
                    ),
            )
        }
        FailureText(state.saveFailed?.let { stringResource(R.string.settings_not_saved, stringResource(it.message)) })
    }
}
