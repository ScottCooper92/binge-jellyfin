package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.resolvedContentInset
import com.cooper.binge.companion.jellyfin.R
import com.binge.designsystem.R as DesR

/**
 * The loading state of a console page: [rows] placeholder rows the height of an `ItemGroup` row, so the page settles
 * without jumping. [rows] is chosen per page, to roughly fill what its settled list does.
 *
 * A settings page under `FormScreen` also gets the form's own content inset around its body, and leads with a page's intro
 * sentence: pass [intro] for it, or [formInset] alone for a page that has none. The intro is static, so the skeleton shows
 * the text itself and the first group lands where the plate stood at any font size. [lead] is what the settled page puts between
 * the intro and its first group, as a plate. [plainIntro] is the same for a page that opens with a plain line of text.
 */
@Composable
internal fun ConsoleLoading(
    inner: PaddingValues,
    rows: Int,
    modifier: Modifier = Modifier,
    intro: String? = null,
    formInset: Boolean = intro != null,
    plainIntro: String? = null,
    lead: (@Composable () -> Unit)? = null,
) {
    val inset = if (formInset) resolvedContentInset() else 0.dp
    val direction = LocalLayoutDirection.current
    // The settled body spaces its blocks by padding_l, the skeleton column by list_row_spacing: the header makes up the difference.
    val gap = (dimensionResource(DesR.dimen.padding_l) - dimensionResource(DesR.dimen.list_row_spacing)).coerceAtLeast(0.dp)
    ListRowSkeletonColumn(
        contentPadding =
            PaddingValues(
                start = inner.calculateStartPadding(direction) + inset,
                top = inner.calculateTopPadding() + inset,
                end = inner.calculateEndPadding(direction) + inset,
                bottom = inner.calculateBottomPadding() + inset,
            ),
        modifier = modifier,
        count = rows,
        height = dimensionResource(DesR.dimen.item_group_icon_size) + dimensionResource(DesR.dimen.item_group_row_padding_v) * 2,
        description = stringResource(R.string.console_loading),
        header =
            (intro ?: plainIntro)?.let { text ->
                {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
                        modifier = Modifier.padding(bottom = gap),
                    ) {
                        if (intro != null) PageIntro(text) else PlainIntro(text)
                        lead?.invoke()
                    }
                }
            },
    )
}

/** The sentence that opens a page which has not moved to `PageIntro`: the page and its skeleton share it, so they cannot drift. */
@Composable
internal fun PlainIntro(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)
