package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeTextField

/**
 * The console's text field: the design system's [BingeTextField] without its clear button, so a field looks as it did before
 * that button existed. Turning it on everywhere is the one `clearable = false` below. Unlike [BingeTextField] a field here
 * submits on Done unless it asks for Next.
 */
@Composable
internal fun ConsoleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    secret: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    revealed: Boolean? = null,
    onToggleReveal: (() -> Unit)? = null,
) = BingeTextField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    modifier = modifier,
    secret = secret,
    enabled = enabled,
    singleLine = singleLine,
    minLines = minLines,
    maxLines = maxLines,
    keyboardType = keyboardType,
    placeholder = placeholder,
    supporting = supporting,
    isError = isError,
    clearable = false,
    revealed = revealed,
    onToggleReveal = onToggleReveal,
    imeAction = imeAction,
    onDone = onDone,
)
