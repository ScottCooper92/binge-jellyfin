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
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Tv
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
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.SingleChoiceList
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchorIf
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ProviderChannel
import com.binge.designsystem.R as DesR

/** What the channel mappings page can ask of its view model, and of the console. */
data class ChannelMappingsActions(
    val onSelect: (String?) -> Unit = {},
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
                        TunerChannelPane(state, selected, actions, PaddingValues(bottom = inner.calculateBottomPadding()))
                    }
                }
            }
        } else {
            ProviderChannels(state, actions, inner, twoPanes = false, modifier = Modifier.fillMaxSize())
            if (selected != null) TunerChannelSheet(state, selected, actions)
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
 * The tuner channels to pair with [channel], in the design system's single-choice sheet with its filter. A pick closes the sheet. A
 * tuner with no channels has nothing to filter, so it says so in a sheet of its own.
 */
@Composable
private fun TunerChannelSheet(
    state: ChannelMappingsUiState.Ready,
    channel: ProviderChannel,
    actions: ChannelMappingsActions,
) {
    val close = { actions.onSelect(null) }
    if (state.options.tunerChannels.isEmpty()) {
        BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = close) { _ ->
            EmptyScreen(message = stringResource(R.string.channel_mappings_no_tuner_channels), icon = Icons.Outlined.Tv)
        }
        return
    }
    BingeChoiceSheet(
        title = stringResource(R.string.channel_mappings_pick_title, channel.name),
        choices = tunerChoices(state, channel),
        selected = state.pairedTuner(channel),
        onSelect = actions.onPick,
        onDismissRequest = close,
        filterPlaceholder = stringResource(R.string.channel_mappings_search),
    )
}

/** The same choice beside the provider list, from [TWO_PANE_WIDTH]: the filter and rows of the sheet, with its title in a header. */
@Composable
internal fun TunerChannelPane(
    state: ChannelMappingsUiState.Ready,
    channel: ProviderChannel,
    actions: ChannelMappingsActions,
    padding: PaddingValues,
) {
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
        if (state.options.tunerChannels.isEmpty()) {
            EmptyScreen(message = stringResource(R.string.channel_mappings_no_tuner_channels), icon = Icons.Outlined.Tv)
        } else {
            SingleChoiceList(
                choices = tunerChoices(state, channel),
                selected = state.pairedTuner(channel),
                filterPlaceholder = stringResource(R.string.channel_mappings_search),
                modifier = Modifier.padding(padding),
                onSelect = actions.onPick,
            )
        }
    }
}

/** The tuner channel shown as the pick for [channel]: the first one paired with it, since a single choice marks one. */
private fun ChannelMappingsUiState.Ready.pairedTuner(channel: ProviderChannel): String? =
    options.tunerChannels.firstOrNull { it.providerChannelId == channel.id }?.id

/** The tuner channels as choices for [channel]. One paired with a channel says which, since choosing it moves the pairing. */
@Composable
internal fun tunerChoices(
    state: ChannelMappingsUiState.Ready,
    channel: ProviderChannel,
): BingeChoiceList<String> {
    val names = state.options.providerChannels.associate { it.id to it.name }
    val picked = state.pairedTuner(channel)
    return BingeChoiceList.Ready(
        state.options.tunerChannels.map { tuner ->
            val pairedWith = tuner.providerChannelId?.takeIf { tuner.id != picked }?.let { names[it] }
            BingeChoice(
                value = tuner.id,
                label = tuner.name,
                supportingText = pairedWith?.let { stringResource(R.string.channel_mappings_paired_with, it) },
                icon = Icons.Outlined.Tv,
            )
        },
    )
}
