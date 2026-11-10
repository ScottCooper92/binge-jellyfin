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
import com.binge.designsystem.component.SwitchRow
import com.binge.designsystem.R as DesR

/**
 * A number setting in a sheet: the [title] over a slider that shows its value as it moves and saves once, when it lands ([onSave]),
 * for a setting that saves as made. [openEndLabel] is the stop past [range] that means no limit, never or automatic, and is `null`.
 * A [value] outside [range], or between two of its [step]s, reads as stored, and nothing is saved until the user moves the control.
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
    auto: AutoOption? = null,
    step: Int = 1,
) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onDismiss) {
        NumberSliderContent(title, value, range, format, openEndLabel, onSave, auto, step)
    }
}

/**
 * A switch above the slider for a value that is not a number, such as the server choosing for itself: [label] names it, [on] says it is
 * set, and [onChange] is called as the switch is flipped, which saves at once. While it is on the slider is dimmed and keeps the last
 * number it showed. Turning the switch off hands [onChange] that number (`null` for the open end, when there was none), which the
 * caller saves.
 */
internal data class AutoOption(
    val label: String,
    val on: Boolean,
    val onChange: (on: Boolean, number: Int?) -> Unit,
)

/** The sheet's body, which draws without a window for a frame. */
@Composable
internal fun NumberSliderContent(
    title: String,
    value: Int?,
    range: IntRange,
    format: (Int) -> String,
    openEndLabel: String?,
    onSave: (Int?) -> Unit,
    auto: AutoOption? = null,
    step: Int = 1,
) {
    // The slider's own value while it moves; it is read when the control lands, so a stepper's tap is saved as it was left.
    var draft by rememberSaveable(value) { mutableStateOf(value) }
    // The last number the slider showed, kept while auto is on (the caller's value is none then) so that turning auto off restores it.
    var kept by rememberSaveable { mutableStateOf(value) }
    var isAuto by rememberSaveable(auto?.on) { mutableStateOf(auto?.on == true) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        auto?.let { option ->
            SwitchRow(
                label = option.label,
                checked = isAuto,
                onCheckedChange = { on ->
                    isAuto = on
                    if (!on) draft = kept
                    option.onChange(on, if (on) null else kept)
                },
            )
        }
        BingeNumberSlider(
            value = if (isAuto) kept else draft,
            onValueChange = {
                draft = it
                kept = it
            },
            range = range,
            format = format,
            description = title,
            openEndLabel = openEndLabel,
            onValueChangeFinished = { onSave(draft) },
            enabled = !isAuto,
            step = step,
        )
    }
}
