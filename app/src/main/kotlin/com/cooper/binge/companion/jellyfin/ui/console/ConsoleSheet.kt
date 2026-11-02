package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.TextEntrySurface
import com.binge.designsystem.R as DesR

/**
 * A console page's form: a task sheet, so everything that takes input is a sheet and dialogs stay for confirmations. With
 * [inWindow] false it draws [content] alone, which a screen frame can capture (a modal window does not).
 */
@Composable
internal fun ConsoleSheet(
    onDismissRequest: () -> Unit,
    inWindow: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (inWindow) {
        BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = onDismissRequest) { content() }
    } else {
        content()
    }
}

/**
 * The design system's [TextEntrySurface] for one typed value, in the console's form: the page's [body] under the title,
 * one line unless [singleLine] is false, Done submitting. [submitEnabled] is true by default because every value here may
 * be left empty or is checked by [onSubmit].
 */
@Composable
internal fun ConsoleTextEntry(
    title: String,
    body: String,
    value: String,
    onValueChange: (String) -> Unit,
    submitLabel: String,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    TextEntrySurface(
        title = title,
        supportingText = body,
        value = value,
        onValueChange = onValueChange,
        onSubmit = onSubmit,
        onCancel = onCancel,
        submitLabel = submitLabel,
        modifier = modifier,
        hint = hint,
        submitEnabled = true,
        error = error,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
    )
}

/**
 * A form sheet built from [ConsoleTextField]s, for a form of more than one labelled field, which the design system's
 * single-value text-entry surface cannot hold: it draws that surface's chrome (title, the page's [body], Cancel and the submit
 * button) around the console's own fields.
 */
@Composable
internal fun ConsoleFieldsForm(
    title: String,
    body: String,
    submitLabel: String,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        fields()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            BingeOutlinedButton(label = stringResource(DesR.string.text_entry_cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            BingeFilledButton(label = submitLabel, onClick = onSubmit, modifier = Modifier.weight(1f))
        }
    }
}
