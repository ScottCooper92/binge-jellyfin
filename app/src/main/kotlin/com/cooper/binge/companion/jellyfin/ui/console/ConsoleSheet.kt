package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.BingeSheetNav
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
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

/** The page of its sheet that a folder form's "Type a path" step is. */
private const val TYPE_PATH_PAGE = "type-a-path"

/**
 * Whether a folder form's "Type a path" step is showing, and how to enter and leave it. The step is a page of the form's
 * sheet, so Back returns to the folder list before it closes the sheet (after closing the keyboard, if it is up); a frame
 * passes [On] or [Off] to draw one or the other.
 */
internal class TypePath(
    val typing: Boolean,
    val open: () -> Unit,
    val close: () -> Unit,
) {
    companion object {
        val Off = TypePath(false, {}, {})
        val On = TypePath(true, {}, {})
    }
}

/** The "Type a path" step of this sheet, for a folder form to read and to enter and leave. */
internal fun BingeSheetNav.typePath() = TypePath(page == TYPE_PATH_PAGE, { push(TYPE_PATH_PAGE) }, { pop() })

/**
 * System Back runs [back] instead of closing the sheet, for a sheet whose steps are its caller's state rather than pages of its
 * own. Null leaves Back to the sheet, which closes it. While the keyboard is up the sheet's own handler closes the keyboard first.
 */
@Composable
internal fun StepBack(back: (() -> Unit)?) {
    val keyboardUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BackHandler(enabled = back != null && !keyboardUp) { back?.invoke() }
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
    isSubmitting: Boolean = false,
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
        isSubmitting = isSubmitting,
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
    submitting: Boolean = false,
    error: String? = null,
    submitEnabled: Boolean = true,
    fields: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        // The fields scroll when the form is taller than the sheet, and Cancel and the submit button stay docked below them.
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            fields()
            // A failed submit keeps the form and its values, and says why here.
            error?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            BingeOutlinedButton(
                label = stringResource(DesR.string.text_entry_cancel),
                onClick = onCancel,
                enabled = !submitting,
                modifier = Modifier.weight(1f),
            )
            BingeFilledButton(
                label = submitLabel,
                onClick = onSubmit,
                enabled = submitEnabled,
                loading = submitting,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A row group outside a padded form, aligned with the sheet header's content inset. */
@Composable
internal fun ConsoleSheetGroup(
    title: String?,
    rows: List<ListItem>,
) {
    ItemGroup(title = title, rows = rows, modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)))
}
