package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSearchField
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchorIf
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ProviderChannel
import com.cooper.binge.companion.jellyfin.server.TunerChannel
import com.binge.designsystem.R as DesR

/** What the channel mappings page can ask of its view model, and of the console. */
data class ChannelMappingsActions(
    val onSelect: (String?) -> Unit = {},
    val onSearch: (String) -> Unit = {},
    val onPick: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

private const val LOADING_ROWS = 8

/** Rows to a group, so a guide of hundreds of channels composes the screenful in view rather than all of them. */
private const val GROUP_ROWS = 20

/** Narrower than this, the tuner channels open in a sheet; from it, they sit in a second pane beside the list. */
private val TWO_PANE_WIDTH = 600.dp

/**
 * A guide data provider's channels, each with the tuner channel it is paired with or "Not matched". A tap chooses another: in a
 * searchable sheet on a phone, and in a second pane beside the list from [TWO_PANE_WIDTH] (an unfolded phone, a tablet). The
 * pick is saved as it is made.
 */
@Composable
fun ChannelMappingsScreen(
    state: ChannelMappingsUiState,
    actions: ChannelMappingsActions,
) {
    val host = remember { SnackbarHostState() }
    val title = (state as? ChannelMappingsUiState.Ready)?.options?.providerName?.takeIf { it.isNotBlank() }
    BingeScreenScaffold(
        title = stringResource(R.string.channel_mappings_title),
        subtitle = title,
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                ChannelMappingsUiState.Loading ->
                    ConsoleLoading(inner, LOADING_ROWS, plainIntro = stringResource(R.string.channel_mappings_body), formInset = false)
                is ChannelMappingsUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is ChannelMappingsUiState.Ready -> {
                    ChannelMappingsBody(state, actions, inner)
                    RefusedMessage(state.refused, host, R.string.channel_mappings_not_changed)
                }
            }
        }
    }
}

@Composable
private fun ChannelMappingsBody(
    state: ChannelMappingsUiState.Ready,
    actions: ChannelMappingsActions,
    inner: PaddingValues,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPanes = maxWidth >= TWO_PANE_WIDTH
        val selected = state.options.providerChannels.firstOrNull { it.id == state.selected }
        if (twoPanes) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.pane_spacer))) {
                ProviderChannels(state, actions, inner, twoPanes = true, modifier = Modifier.weight(1f))
                Column(Modifier.weight(1f).padding(top = inner.calculateTopPadding())) {
                    if (selected == null) {
                        Text(
                            text = stringResource(R.string.channel_mappings_pick_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
                        )
                    } else {
                        TunerChannelChoices(state, selected, actions, PaddingValues(bottom = inner.calculateBottomPadding()))
                    }
                }
            }
        } else {
            ProviderChannels(state, actions, inner, twoPanes = false, modifier = Modifier.fillMaxSize())
            if (selected != null) {
                BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = { actions.onSelect(null) }) { _ ->
                    TunerChannelChoices(state, selected, actions, PaddingValues(), onPicked = { actions.onSelect(null) })
                }
            }
        }
    }
}

/** The provider's channels, [GROUP_ROWS] to a group. With [twoPanes] the one whose tuner channels are open is marked. */
@Composable
private fun ProviderChannels(
    state: ChannelMappingsUiState.Ready,
    actions: ChannelMappingsActions,
    inner: PaddingValues,
    twoPanes: Boolean,
    modifier: Modifier = Modifier,
) {
    val unmatched = stringResource(R.string.channel_mappings_unmatched)
    val rows =
        state.options.providerChannels.map { channel ->
            val paired = state.options.pairedWith(channel.id)
            ListItem(
                icon = Icons.Outlined.LiveTv,
                label = channel.name,
                detail =
                    when {
                        paired.isEmpty() -> unmatched
                        paired.size == 1 -> paired.first().name
                        else -> stringResource(R.string.channel_mappings_more, paired.first().name, paired.size - 1)
                    },
                selected = twoPanes && channel.id == state.selected,
                onClick = { actions.onSelect(channel.id) },
            )
        }
    LazyColumn(
        modifier = modifier,
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        item(key = "intro") {
            PlainIntro(stringResource(R.string.channel_mappings_body))
        }
        if (rows.isEmpty()) {
            item(key = "empty") { ConsoleEmptyScreen(stringResource(R.string.channel_mappings_no_channels), Icons.Outlined.Tv) }
        }
        rows.chunked(GROUP_ROWS).forEachIndexed { index, group ->
            item(key = "group-$index") {
                ItemGroup(
                    title = null,
                    rows = group,
                    modifier = Modifier.layoutAnchorIf(index == 0, LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
                )
            }
        }
    }
}

/**
 * The tuner channels to pair with [channel], narrowed by a search. The channel paired now is ticked; one that is paired with another
 * channel says so, since choosing it moves it. [onPicked] runs after a pick, for a sheet that closes.
 */
@Composable
internal fun TunerChannelChoices(
    state: ChannelMappingsUiState.Ready,
    channel: ProviderChannel,
    actions: ChannelMappingsActions,
    padding: PaddingValues,
    onPicked: () -> Unit = {},
) {
    val names = state.options.providerChannels.associate { it.id to it.name }
    val matching = state.matching
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.channel_mappings_pick_title, channel.name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier.padding(
                    horizontal = dimensionResource(DesR.dimen.padding_m),
                    vertical = dimensionResource(DesR.dimen.padding_s),
                ),
        )
        BingeSearchField(
            query = state.query,
            onQueryChange = actions.onSearch,
            onClear = { actions.onSearch("") },
            placeholder = stringResource(R.string.channel_mappings_search),
            modifier =
                Modifier.padding(
                    horizontal = dimensionResource(DesR.dimen.padding_m),
                    vertical = dimensionResource(DesR.dimen.padding_s),
                ),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
        ) {
            if (matching.isEmpty()) {
                val none = state.options.tunerChannels.isEmpty()
                item(key = "empty") {
                    ConsoleEmptyScreen(
                        stringResource(if (none) R.string.channel_mappings_no_tuner_channels else R.string.channel_mappings_no_match),
                        if (none) Icons.Outlined.Tv else Icons.Outlined.SearchOff,
                    )
                }
            }
            matching.chunked(GROUP_ROWS).forEachIndexed { index, group ->
                item(key = "tuner-$index") {
                    ItemGroup(
                        title = null,
                        rows =
                            group.map { tuner ->
                                tunerRow(tuner, channel, names) {
                                    actions.onPick(tuner.id)
                                    onPicked()
                                }
                            },
                    )
                }
            }
        }
    }
}

/** A tuner channel as a choice for [channel]: ticked when paired with it, saying so when it is paired with another, which choosing moves. */
@Composable
private fun tunerRow(
    tuner: TunerChannel,
    channel: ProviderChannel,
    names: Map<String, String>,
    onPick: () -> Unit,
): ListItem {
    val here = tuner.providerChannelId == channel.id
    val elsewhere = tuner.providerChannelId?.takeIf { !here }?.let { names[it] }
    return ListItem(
        icon = Icons.Outlined.Tv,
        label = tuner.name,
        detail = elsewhere?.let { stringResource(R.string.channel_mappings_paired_with, it) },
        // A pick opens nothing, so a row that is not the pairing carries no chevron.
        trailingContent = { if (here) Icon(Icons.Outlined.Check, contentDescription = null) },
        selected = here,
        onClick = onPick,
    )
}
