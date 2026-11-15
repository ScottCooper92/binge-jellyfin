package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeNumberSlider
import com.binge.designsystem.component.BingeSheetKind
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.tip.TIP_STEPS
import com.binge.designsystem.R as DesR

private val DEFAULT_TIP_INDEX = TIP_STEPS.indexOf(5)

/**
 * The tip jar's sheet: a slider over the steps Play has priced, and a button that gives the chosen one. Each
 * step is its own Play product, so the amount is never free-typed, and every price is Play's own local one. The
 * steps are not evenly spaced, so the slider runs over their positions in [TIP_STEPS], not over the amounts.
 */
@Composable
internal fun TipSheet(
    prices: Map<Int, String>,
    onGive: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onDismiss) {
        TipSheetContent(prices = prices, onGive = onGive)
    }
}

@Composable
internal fun TipSheetContent(
    prices: Map<Int, String>,
    onGive: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var position by remember { mutableIntStateOf(DEFAULT_TIP_INDEX) }
    val price = { index: Int -> prices[TIP_STEPS[index]].orEmpty() }
    Column(
        modifier = modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.screen_content_inset)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(stringResource(R.string.tip_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.tip_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BingeNumberSlider(
            value = position,
            onValueChange = { position = it ?: DEFAULT_TIP_INDEX },
            range = TIP_STEPS.indices,
            format = price,
            description = stringResource(R.string.tip_amount),
        )
        BingeFilledButton(
            label = stringResource(R.string.tip_give, price(position)),
            onClick = { onGive(TIP_STEPS[position]) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
