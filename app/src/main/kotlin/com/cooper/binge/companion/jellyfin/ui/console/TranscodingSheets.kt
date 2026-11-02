package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.TextEntrySurface
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.DEINTERLACE_METHODS
import com.cooper.binge.companion.jellyfin.server.ENCODER_PRESETS
import com.cooper.binge.companion.jellyfin.server.ENCODING_ACCELERATION
import com.cooper.binge.companion.jellyfin.server.ENCODING_DEINTERLACE
import com.cooper.binge.companion.jellyfin.server.ENCODING_DOWNMIX_ALGORITHM
import com.cooper.binge.companion.jellyfin.server.ENCODING_PRESET
import com.cooper.binge.companion.jellyfin.server.ENCODING_TEMP_PATH
import com.cooper.binge.companion.jellyfin.server.ENCODING_THREADS
import com.cooper.binge.companion.jellyfin.server.ENCODING_TONEMAPPING_ALGORITHM
import com.cooper.binge.companion.jellyfin.server.ENCODING_TONEMAPPING_MODE
import com.cooper.binge.companion.jellyfin.server.ENCODING_TONEMAPPING_RANGE
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.TONEMAPPING_ALGORITHMS
import com.cooper.binge.companion.jellyfin.server.TONEMAPPING_MODES
import com.cooper.binge.companion.jellyfin.server.TONEMAPPING_RANGES
import com.cooper.binge.companion.jellyfin.server.accelerationTypes
import com.cooper.binge.companion.jellyfin.server.downmixAlgorithms
import com.cooper.binge.companion.jellyfin.server.encodingChoice
import com.cooper.binge.companion.jellyfin.server.encodingStored
import com.cooper.binge.companion.jellyfin.server.int
import com.cooper.binge.companion.jellyfin.server.string
import kotlinx.serialization.json.JsonPrimitive

private const val AUTO_THREADS = -1
private const val MAX_THREADS = 16
private const val PATH_LINES = 2

/** Whichever sheet is open over the page. */
@Composable
internal fun TranscodingSheets(
    state: TranscodingUiState.Ready,
    actions: TranscodingActions,
    folders: FolderBrowseState,
    folderActions: FolderActions,
) {
    val sheet = state.sheet ?: return
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = { actions.onSheet(null) }) {
        when (sheet) {
            is TranscodingSheet.Number ->
                NumberForm(
                    sheet.field,
                    shownNumber(
                        sheet.field,
                        if (sheet.field.kind ==
                            ConfigurationKind.Encoding
                        ) {
                            state.encoding
                        } else {
                            state.server
                        },
                    ),
                    actions,
                )
            is TranscodingSheet.Text -> TextForm(sheet.field, state.encoding.string(sheet.field.key).orEmpty(), actions)
            TranscodingSheet.TempFolder ->
                TempFolderForm(
                    state.encoding.string(ENCODING_TEMP_PATH).orEmpty(),
                    folders,
                    folderActions,
                    actions,
                )
        }
    }
}

/** A number in its range and unit, from a sheet: Save keeps it if it is in range, and says what range if it isn't. */
@Composable
internal fun NumberForm(
    field: NumberField,
    current: String,
    actions: TranscodingActions,
) {
    var text by rememberSaveable { mutableStateOf(current) }
    val valid = field.parse(text) != null
    val min = shownBound(field.min)
    val max = shownBound(field.max)
    TextEntrySurface(
        title = stringResource(field.title()),
        value = text,
        onValueChange = { text = it },
        onSubmit = { actions.onNumber(field, text) },
        onCancel = { actions.onSheet(null) },
        submitLabel = stringResource(R.string.library_save),
        header = {
            Text(
                stringResource(R.string.transcoding_range_hint, unitText(field.unit, min), unitText(field.unit, max)),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        submitEnabled = valid,
        error = stringResource(R.string.transcoding_number_invalid, min, max).takeIf { text.isNotBlank() && !valid },
        minLines = 1,
        maxLines = 1,
        keyboardOptions = KeyboardOptions(keyboardType = if (field.whole) KeyboardType.Number else KeyboardType.Decimal),
    )
}

private fun shownBound(bound: Double): String = if (bound % 1.0 == 0.0) bound.toLong().toString() else bound.toString()

internal fun NumberField.title(): Int =
    when (this) {
        NumberField.DownmixBoost -> R.string.transcoding_downmix_boost
        NumberField.ThrottleDelay -> R.string.transcoding_throttle_delay
        NumberField.SegmentKeep -> R.string.transcoding_segment_keep
        NumberField.Desaturation -> R.string.transcoding_desaturation
        NumberField.PeakBrightness -> R.string.transcoding_peak
        NumberField.ToneMappingParam -> R.string.transcoding_tonemapping_param
        NumberField.VppBrightness -> R.string.transcoding_vpp_brightness
        NumberField.VppContrast -> R.string.transcoding_vpp_contrast
        NumberField.MinResumePercent -> R.string.transcoding_min_resume
        NumberField.MaxResumePercent -> R.string.transcoding_max_resume
        NumberField.MinResumeSeconds -> R.string.transcoding_min_duration
        NumberField.MinAudiobookResume -> R.string.transcoding_min_audiobook
        NumberField.MaxAudiobookResume -> R.string.transcoding_max_audiobook
        NumberField.BitrateLimit -> R.string.transcoding_bitrate
    }

/** A device or path typed in a sheet; blank leaves the setting unset. Saves with its button. */
@Composable
internal fun TextForm(
    field: TextField,
    current: String,
    actions: TranscodingActions,
) {
    var text by rememberSaveable { mutableStateOf(current) }
    TextEntrySurface(
        title =
            stringResource(
                when (field) {
                    TextField.VaapiDevice -> R.string.transcoding_vaapi_device
                    TextField.QsvDevice -> R.string.transcoding_qsv_device
                    TextField.FallbackFont -> R.string.transcoding_fallback_font_path
                },
            ),
        value = text,
        onValueChange = { text = it },
        onSubmit = {
            actions.onEncoding(field.key, JsonPrimitive(text.trim()))
            actions.onSheet(null)
        },
        onCancel = { actions.onSheet(null) },
        submitLabel = stringResource(R.string.library_save),
        hint = stringResource(if (field == TextField.FallbackFont) R.string.transcoding_font_hint else R.string.transcoding_device_hint),
        submitEnabled = true,
        minLines = 1,
        maxLines = PATH_LINES,
    )
}

/** Where the server keeps what it transcodes: a folder chosen from the server's own, or typed; blank for the default. */
@Composable
internal fun TempFolderForm(
    current: String,
    folders: FolderBrowseState,
    folderActions: FolderActions,
    actions: TranscodingActions,
    startTyping: Boolean = false,
) {
    var typing by rememberSaveable { mutableStateOf(startTyping) }
    var path by rememberSaveable { mutableStateOf(current) }
    val choose = { value: String ->
        actions.onEncoding(ENCODING_TEMP_PATH, JsonPrimitive(value.trim()))
        actions.onSheet(null)
    }
    if (typing) {
        TextEntrySurface(
            title = stringResource(R.string.transcoding_temp_path),
            value = path,
            onValueChange = { path = it },
            onSubmit = { choose(path) },
            onCancel = { typing = false },
            cancelLabel = stringResource(R.string.library_back),
            submitLabel = stringResource(R.string.library_save),
            hint = stringResource(R.string.transcoding_temp_hint),
            submitEnabled = true,
            minLines = 1,
            maxLines = PATH_LINES,
        )
    } else {
        FolderPicker(
            state = folders,
            actions = folderActions,
            busy = false,
            onChoose = choose,
            onType = { typing = true },
            onCancel = { actions.onSheet(null) },
        )
    }
}

@Composable
internal fun TranscodingPickerSheet(
    picker: TranscodingPicker,
    state: TranscodingUiState.Ready,
    profile: JellyfinServerProfile,
    actions: TranscodingActions,
    onDismiss: () -> Unit,
) {
    val config = state.encoding
    when (picker) {
        TranscodingPicker.Acceleration ->
            Choices(
                R.string.transcoding_acceleration,
                accelerationTypes(
                    profile,
                ),
                encodingChoice(config.string(ENCODING_ACCELERATION), "none"),
                {
                    accelerationLabel(it)
                },
                onDismiss,
            ) {
                actions.onEncoding(ENCODING_ACCELERATION, JsonPrimitive(encodingStored(it, "none", profile)))
            }
        TranscodingPicker.Algorithm ->
            Choices(R.string.transcoding_algorithm, TONEMAPPING_ALGORITHMS, config.string(ENCODING_TONEMAPPING_ALGORITHM) ?: "none", {
                algorithmLabel(it)
            }, onDismiss) {
                actions.onEncoding(ENCODING_TONEMAPPING_ALGORITHM, JsonPrimitive(it))
            }
        TranscodingPicker.Mode ->
            Choices(
                R.string.transcoding_mode,
                TONEMAPPING_MODES,
                config.string(ENCODING_TONEMAPPING_MODE) ?: "auto",
                { modeLabel(it) },
                onDismiss,
            ) {
                actions.onEncoding(ENCODING_TONEMAPPING_MODE, JsonPrimitive(it))
            }
        TranscodingPicker.Range ->
            Choices(
                R.string.transcoding_range,
                TONEMAPPING_RANGES,
                config.string(ENCODING_TONEMAPPING_RANGE) ?: "auto",
                { modeLabel(it) },
                onDismiss,
            ) {
                actions.onEncoding(ENCODING_TONEMAPPING_RANGE, JsonPrimitive(it))
            }
        TranscodingPicker.Preset ->
            Choices(
                R.string.transcoding_preset,
                ENCODER_PRESETS,
                encodingChoice(config.string(ENCODING_PRESET), "auto"),
                { presetLabel(it) },
                onDismiss,
            ) {
                actions.onEncoding(ENCODING_PRESET, JsonPrimitive(encodingStored(it, "auto", profile)))
            }
        TranscodingPicker.Deinterlace ->
            Choices(R.string.transcoding_deinterlace, DEINTERLACE_METHODS, config.string(ENCODING_DEINTERLACE) ?: "yadif", {
                deinterlaceLabel(it)
            }, onDismiss) {
                actions.onEncoding(ENCODING_DEINTERLACE, JsonPrimitive(it))
            }
        TranscodingPicker.Downmix ->
            Choices(R.string.transcoding_downmix, downmixAlgorithms(profile), config.string(ENCODING_DOWNMIX_ALGORITHM) ?: "None", {
                downmixLabel(it)
            }, onDismiss) {
                actions.onEncoding(ENCODING_DOWNMIX_ALGORITHM, JsonPrimitive(it))
            }
        TranscodingPicker.Threads -> {
            val current = config.int(ENCODING_THREADS) ?: AUTO_THREADS
            val all = (listOf(AUTO_THREADS) + (1..MAX_THREADS) + current).distinct()
            BingeChoiceSheet(
                title = stringResource(R.string.transcoding_threads),
                choices = BingeChoiceList.Ready(all.map { BingeChoice(it.toString(), threadsLabel(it)) }),
                selected = current.toString(),
                onSelect = { value -> value.toIntOrNull()?.let { actions.onEncoding(ENCODING_THREADS, JsonPrimitive(it)) } },
                onDismissRequest = onDismiss,
            )
        }
    }
}

/** A pick from [values], and the server's own value if it is another. */
@Composable
private fun Choices(
    title: Int,
    values: List<String>,
    current: String,
    label: @Composable (String) -> String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    BingeChoiceSheet(
        title = stringResource(title),
        choices = BingeChoiceList.Ready((values + current).distinct().map { BingeChoice(it, label(it)) }),
        selected = current,
        onSelect = onPick,
        onDismissRequest = onDismiss,
    )
}
