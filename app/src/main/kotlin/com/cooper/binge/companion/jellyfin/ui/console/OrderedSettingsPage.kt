package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.cooper.binge.companion.jellyfin.R
import com.binge.designsystem.R as DesR

/** Switches for one named entry, then its place in the order. A missing [place] disables both moves. */
@Composable
internal fun OrderedSettingsPage(
    title: String?,
    switches: List<ListItem>,
    place: Int?,
    size: Int,
    onMove: (Int) -> Unit,
    position: ListItem? = null,
    firstDetail: String? = null,
    lastDetail: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        ItemGroup(title = title, rows = switches)
        ItemGroup(
            title = stringResource(R.string.home_layout_order),
            rows =
                listOfNotNull(
                    position,
                    ListItem(
                        icon = Icons.Outlined.ArrowUpward,
                        label = stringResource(R.string.home_layout_move_up),
                        detail = firstDetail.takeIf { place == 0 },
                        enabled = place != null && place > 0,
                        destination = ListItemDestination.Action,
                        onClick = { onMove(-1) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.ArrowDownward,
                        label = stringResource(R.string.home_layout_move_down),
                        detail = lastDetail.takeIf { place == size - 1 },
                        enabled = place != null && place < size - 1,
                        destination = ListItemDestination.Action,
                        onClick = { onMove(1) },
                    ),
                ),
        )
    }
}
