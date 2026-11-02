package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.cooper.binge.companion.jellyfin.R
import com.binge.designsystem.R as DesR

/**
 * The loading state of a console page: [rows] placeholder rows the height of an `ItemGroup` row, so the page settles
 * without jumping. [rows] is chosen per page, to roughly fill what its settled list does.
 */
@Composable
internal fun ConsoleLoading(
    inner: PaddingValues,
    rows: Int,
) = ListRowSkeletonColumn(
    contentPadding = inner,
    count = rows,
    height = dimensionResource(DesR.dimen.item_group_icon_size) + dimensionResource(DesR.dimen.item_group_row_padding_v) * 2,
    description = stringResource(R.string.console_loading),
)
