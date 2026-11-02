package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.cooper.binge.companion.jellyfin.R

/**
 * The console's one text field, so keyboard options, error text, password reveal and focus behave the same everywhere.
 *
 * @param secret masks the value, asks for a password keyboard and gives the field its own show/hide toggle, so a pasted
 *   value can be checked before it is sent.
 * @param initiallyRevealed starts a [secret] field showing its value; only a frame needs it.
 * @param supporting a line under the field, in the error colour when [isError]. Empty keeps the slot without text, so a field
 *   that shows an error later does not change height when it appears; null leaves no slot.
 * @param imeAction [ImeAction.Next] moves focus on, so the last field of a form passes [ImeAction.Done]. Done drops the
 *   keyboard and then runs [onDone]. A multi-line field keeps the keyboard's Enter whatever this says.
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
    keyboardType: KeyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    initiallyRevealed: Boolean = false,
) {
    val focus = LocalFocusManager.current
    var revealed by remember { mutableStateOf(initiallyRevealed) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        supportingText = supporting?.let { { if (it.isNotEmpty()) Text(it) } },
        trailingIcon = if (secret) ({ RevealToggle(revealed) { revealed = !revealed } }) else null,
        visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) imeAction else ImeAction.Default),
        keyboardActions =
            KeyboardActions(
                onNext = { focus.moveFocus(FocusDirection.Next) },
                onDone = {
                    focus.clearFocus()
                    onDone?.invoke()
                },
            ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun RevealToggle(
    revealed: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = stringResource(if (revealed) R.string.field_hide_password else R.string.field_show_password),
        )
    }
}
