package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeNumberSlider
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.R as DesR

/**
 * A number setting in a sheet: the [title] over a slider that shows its value as it moves and saves once, when it lands ([onSave]),
 * for a setting that saves as made. [openEndLabel] is the stop past [range] that means no limit, never or automatic, and is `null`.
 * A [value] outside [range] reads as stored, and nothing is saved until the user moves the control.
 */
@Composable
internal fun NumberSliderSheet(
    title: String,
    value: Int?,
    range: IntRange,
    format: (Int) -> String,
    openEndLabel: String?,
    onSave: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onDismiss) {
        NumberSliderContent(title, value, range, format, openEndLabel, onSave)
    }
}

/** The sheet's body, which draws without a window for a frame. */
@Composable
internal fun NumberSliderContent(
    title: String,
    value: Int?,
    range: IntRange,
    format: (Int) -> String,
    openEndLabel: String?,
    onSave: (Int?) -> Unit,
) {
    // The slider's own value while it moves; it is read when the control lands, so a stepper's tap is saved as it was left.
    var draft by rememberSaveable(value) { mutableStateOf(value) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        BingeNumberSlider(
            value = draft,
            onValueChange = { draft = it },
            range = range,
            format = format,
            description = title,
            openEndLabel = openEndLabel,
            onValueChangeFinished = { onSave(draft) },
        )
    }
}
