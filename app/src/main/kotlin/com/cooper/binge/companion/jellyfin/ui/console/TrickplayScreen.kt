package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.LowPriority
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_PRIORITIES
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_SCAN_BEHAVIORS
import com.cooper.binge.companion.jellyfin.server.TrickplaySettings
import com.binge.designsystem.R as DesR

/** What the trickplay page can ask of its view model, and of the console. */
data class TrickplayActions(
    val onEdit: (TrickplayField?) -> Unit = {},
    val onSave: (TrickplayField, String) -> Unit = { _, _ -> },
    val onHwAcceleration: (Boolean) -> Unit = {},
    val onHwEncoding: (Boolean) -> Unit = {},
    val onKeyFrameOnly: (Boolean) -> Unit = {},
    val onScanBehavior: (String) -> Unit = {},
    val onProcessPriority: (String) -> Unit = {},
    val onInterval: (Int) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** The picker open over the page. */
private enum class TrickplayPicker { Scan, Priority, Interval }

/** Lines a failed save's message takes, so it never moves the rows. */
private const val STATUS_LINES = 2

/** Lines a switch's detail may wrap to, so it reads in full at large fonts. */
private const val DETAIL_LINES = 6

private const val MILLIS_PER_SECOND = 1000

/** The intervals offered, in seconds, beside a custom one. */
private val INTERVAL_PRESETS_SECONDS = listOf(1, 2, 5, 10, 20, 30, 60)

private const val CUSTOM = "custom"

/**
 * The server's trickplay (seek thumbnail) options: what makes the images, and what they are. Each
 * change saves as it's made; a number saves when its dialog does. Key frames only shows on a server
 * that has it ([keyFrameOnlyAvailable], 10.10).
 */
@Composable
fun TrickplayScreen(
    state: TrickplayUiState,
    actions: TrickplayActions,
    keyFrameOnlyAvailable: Boolean = true,
) {
    var picker by rememberSaveable { mutableStateOf<TrickplayPicker?>(null) }
    BingeScreenScaffold(title = stringResource(R.string.trickplay_title), bar = ScreenBar.Small, onBack = actions.onBack) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                TrickplayUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                is TrickplayUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is TrickplayUiState.Ready -> {
                    TrickplayBody(state, actions, keyFrameOnlyAvailable, inner) { picker = it }
                    picker?.let { TrickplayPickerSheet(it, state.settings, actions) { picker = null } }
                    state.editing?.let { FieldDialog(it, state.settings, actions) }
                }
            }
        }
    }
}

@Composable
private fun TrickplayBody(
    state: TrickplayUiState.Ready,
    actions: TrickplayActions,
    keyFrameOnlyAvailable: Boolean,
    inner: PaddingValues,
    onPick: (TrickplayPicker) -> Unit,
) {
    val settings = state.settings
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        Text(stringResource(R.string.trickplay_body), style = MaterialTheme.typography.bodyMedium)
        ProcessingGroup(settings, actions, keyFrameOnlyAvailable, onPick)
        ImagesGroup(settings, actions, onPick)
        Text(
            text = state.saveFailed?.let { stringResource(R.string.settings_not_saved, stringResource(it.message)) }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            minLines = STATUS_LINES,
            maxLines = STATUS_LINES,
        )
    }
}

@Composable
private fun ProcessingGroup(
    settings: TrickplaySettings,
    actions: TrickplayActions,
    keyFrameOnlyAvailable: Boolean,
    onPick: (TrickplayPicker) -> Unit,
) {
    ItemGroup(
        title = stringResource(R.string.trickplay_processing),
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Outlined.Memory,
                    label = stringResource(R.string.trickplay_hw_acceleration),
                    detail = stringResource(R.string.trickplay_hw_acceleration_detail),
                    supportingMaxLines = DETAIL_LINES,
                    toggled = settings.hwAcceleration,
                    onClick = { actions.onHwAcceleration(!settings.hwAcceleration) },
                ),
                ListItem(
                    icon = Icons.Outlined.Videocam,
                    label = stringResource(R.string.trickplay_hw_encoding),
                    detail = stringResource(R.string.trickplay_hw_encoding_detail),
                    supportingMaxLines = DETAIL_LINES,
                    toggled = settings.hwEncoding,
                    onClick = { actions.onHwEncoding(!settings.hwEncoding) },
                ),
                ListItem(
                    icon = Icons.Outlined.Bolt,
                    label = stringResource(R.string.trickplay_key_frames),
                    detail = stringResource(R.string.trickplay_key_frames_detail),
                    supportingMaxLines = DETAIL_LINES,
                    toggled = settings.keyFrameOnly,
                    onClick = { actions.onKeyFrameOnly(!settings.keyFrameOnly) },
                ).takeIf { keyFrameOnlyAvailable },
                ListItem(
                    icon = Icons.Outlined.Sync,
                    label = stringResource(R.string.trickplay_scan),
                    detail = scanLabel(settings.scanBehavior),
                    onClick = { onPick(TrickplayPicker.Scan) },
                ),
                ListItem(
                    icon = Icons.Outlined.LowPriority,
                    label = stringResource(R.string.trickplay_priority),
                    detail = priorityLabel(settings.processPriority),
                    onClick = { onPick(TrickplayPicker.Priority) },
                ),
                ListItem(
                    icon = Icons.Outlined.Speed,
                    label = stringResource(R.string.trickplay_threads),
                    detail =
                        if (settings.threads == 0) {
                            stringResource(R.string.trickplay_threads_auto)
                        } else {
                            settings.threads.toString()
                        },
                    onClick = { actions.onEdit(TrickplayField.Threads) },
                ),
            ),
    )
}

@Composable
private fun ImagesGroup(
    settings: TrickplaySettings,
    actions: TrickplayActions,
    onPick: (TrickplayPicker) -> Unit,
) {
    ItemGroup(
        title = stringResource(R.string.trickplay_images),
        rows =
            listOf(
                ListItem(
                    icon = Icons.Outlined.Timer,
                    label = stringResource(R.string.trickplay_interval),
                    detail = intervalLabel(settings.intervalMillis),
                    onClick = { onPick(TrickplayPicker.Interval) },
                ),
                ListItem(
                    icon = Icons.Outlined.AspectRatio,
                    label = stringResource(R.string.trickplay_widths),
                    detail = settings.widths.map { stringResource(R.string.trickplay_pixels, it) }.joinToString(", "),
                    onClick = { actions.onEdit(TrickplayField.Widths) },
                ),
                ListItem(
                    icon = Icons.Outlined.ViewColumn,
                    label = stringResource(R.string.trickplay_tile_width),
                    detail = pluralStringResource(R.plurals.trickplay_images_count, settings.tileWidth, settings.tileWidth),
                    onClick = { actions.onEdit(TrickplayField.TileWidth) },
                ),
                ListItem(
                    icon = Icons.Outlined.ViewAgenda,
                    label = stringResource(R.string.trickplay_tile_height),
                    detail = pluralStringResource(R.plurals.trickplay_images_count, settings.tileHeight, settings.tileHeight),
                    onClick = { actions.onEdit(TrickplayField.TileHeight) },
                ),
                ListItem(
                    icon = Icons.Outlined.HighQuality,
                    label = stringResource(R.string.trickplay_jpeg_quality),
                    detail = settings.jpegQuality.toString(),
                    onClick = { actions.onEdit(TrickplayField.JpegQuality) },
                ),
                ListItem(
                    icon = Icons.Outlined.Tune,
                    label = stringResource(R.string.trickplay_qscale),
                    detail = settings.qscale.toString(),
                    onClick = { actions.onEdit(TrickplayField.Qscale) },
                ),
            ),
    )
}

@Composable
private fun scanLabel(value: String): String =
    when (value) {
        "NonBlocking" -> stringResource(R.string.trickplay_scan_non_blocking)
        "Blocking" -> stringResource(R.string.trickplay_scan_blocking)
        else -> value
    }

@Composable
private fun priorityLabel(value: String): String =
    when (value) {
        "High" -> stringResource(R.string.trickplay_priority_high)
        "AboveNormal" -> stringResource(R.string.trickplay_priority_above_normal)
        "Normal" -> stringResource(R.string.trickplay_priority_normal)
        "BelowNormal" -> stringResource(R.string.trickplay_priority_below_normal)
        "Idle" -> stringResource(R.string.trickplay_priority_idle)
        else -> value
    }

/** An interval as people say it: whole seconds when it is, else milliseconds. */
@Composable
private fun intervalLabel(millis: Int): String =
    if (millis % MILLIS_PER_SECOND == 0) {
        stringResource(R.string.trickplay_every_seconds, millis / MILLIS_PER_SECOND)
    } else {
        stringResource(R.string.trickplay_every_millis, millis)
    }

@Composable
private fun TrickplayPickerSheet(
    picker: TrickplayPicker,
    settings: TrickplaySettings,
    actions: TrickplayActions,
    onDismiss: () -> Unit,
) {
    when (picker) {
        TrickplayPicker.Scan ->
            BingeChoiceSheet(
                title = stringResource(R.string.trickplay_scan),
                choices = BingeChoiceList.Ready(TRICKPLAY_SCAN_BEHAVIORS.map { BingeChoice(it, scanLabel(it)) }),
                selected = settings.scanBehavior,
                onSelect = actions.onScanBehavior,
                onDismissRequest = onDismiss,
            )
        TrickplayPicker.Priority ->
            BingeChoiceSheet(
                title = stringResource(R.string.trickplay_priority),
                choices = BingeChoiceList.Ready(TRICKPLAY_PRIORITIES.map { BingeChoice(it, priorityLabel(it)) }),
                selected = settings.processPriority,
                onSelect = actions.onProcessPriority,
                onDismissRequest = onDismiss,
            )
        TrickplayPicker.Interval -> {
            val presets = INTERVAL_PRESETS_SECONDS.map { it * MILLIS_PER_SECOND }
            val choices =
                (presets + settings.intervalMillis).distinct().sorted().map { BingeChoice(it.toString(), intervalLabel(it)) } +
                    BingeChoice(CUSTOM, stringResource(R.string.trickplay_custom))
            BingeChoiceSheet(
                title = stringResource(R.string.trickplay_interval),
                choices = BingeChoiceList.Ready(choices),
                selected = settings.intervalMillis.toString(),
                onSelect = { value ->
                    if (value == CUSTOM) actions.onEdit(TrickplayField.Interval) else value.toIntOrNull()?.let(actions.onInterval)
                },
                onDismissRequest = onDismiss,
            )
        }
    }
}

/**
 * A number's (or the widths') dialog: Save keeps what's typed if it is in range, and says so if it isn't;
 * Cancel leaves the setting as it was. [inWindow] false draws its body for a frame.
 */
@Composable
internal fun FieldDialog(
    field: TrickplayField,
    settings: TrickplaySettings,
    actions: TrickplayActions,
    inWindow: Boolean = true,
) {
    val current =
        when (field) {
            TrickplayField.Interval -> settings.intervalMillis.toString()
            TrickplayField.Widths -> settings.widths.joinToString(", ")
            TrickplayField.TileWidth -> settings.tileWidth.toString()
            TrickplayField.TileHeight -> settings.tileHeight.toString()
            TrickplayField.Qscale -> settings.qscale.toString()
            TrickplayField.JpegQuality -> settings.jpegQuality.toString()
            TrickplayField.Threads -> settings.threads.toString()
        }
    var value by rememberSaveable(field) { mutableStateOf(current) }
    var tried by rememberSaveable(field) { mutableStateOf(false) }
    val valid = if (field == TrickplayField.Widths) parseWidths(value) != null else field.parse(value) != null
    val (title, body) =
        when (field) {
            TrickplayField.Interval -> R.string.trickplay_interval to R.string.trickplay_interval_body
            TrickplayField.Widths -> R.string.trickplay_widths to R.string.trickplay_widths_body
            TrickplayField.TileWidth -> R.string.trickplay_tile_width to R.string.trickplay_tile_body
            TrickplayField.TileHeight -> R.string.trickplay_tile_height to R.string.trickplay_tile_body
            TrickplayField.Qscale -> R.string.trickplay_qscale to R.string.trickplay_qscale_body
            TrickplayField.JpegQuality -> R.string.trickplay_jpeg_quality to R.string.trickplay_jpeg_quality_body
            TrickplayField.Threads -> R.string.trickplay_threads to R.string.trickplay_threads_body
        }
    val save = {
        tried = true
        if (valid) actions.onSave(field, value)
    }
    ConsoleConfirm(
        title = stringResource(title),
        message = stringResource(body),
        confirmLabel = stringResource(R.string.trickplay_save),
        onConfirm = save,
        onDismissRequest = { actions.onEdit(null) },
        inWindow = inWindow,
        extraContent = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(title)) },
                singleLine = true,
                isError = tried && !valid,
                supportingText = {
                    if (tried && !valid) {
                        Text(
                            if (field == TrickplayField.Widths) {
                                stringResource(R.string.trickplay_widths_invalid, field.min, field.max)
                            } else {
                                stringResource(R.string.trickplay_number_invalid, field.min, field.max)
                            },
                        )
                    }
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = if (field == TrickplayField.Widths) KeyboardType.Text else KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

private const val LOADING_ROWS = 10
