package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ENCODING_AUDIO_VBR
import com.cooper.binge.companion.jellyfin.server.ENCODING_DECODING_CODECS
import com.cooper.binge.companion.jellyfin.server.ENCODING_DEINTERLACE
import com.cooper.binge.companion.jellyfin.server.ENCODING_DEINTERLACE_DOUBLE_RATE
import com.cooper.binge.companion.jellyfin.server.ENCODING_DOWNMIX_ALGORITHM
import com.cooper.binge.companion.jellyfin.server.ENCODING_ENHANCED_NVDEC
import com.cooper.binge.companion.jellyfin.server.ENCODING_FALLBACK_FONT
import com.cooper.binge.companion.jellyfin.server.ENCODING_FALLBACK_FONT_PATH
import com.cooper.binge.companion.jellyfin.server.ENCODING_HARDWARE_ENCODING
import com.cooper.binge.companion.jellyfin.server.ENCODING_HEVC
import com.cooper.binge.companion.jellyfin.server.ENCODING_INTEL_LOW_POWER_HEVC
import com.cooper.binge.companion.jellyfin.server.ENCODING_PRESET
import com.cooper.binge.companion.jellyfin.server.ENCODING_QSV_DEVICE
import com.cooper.binge.companion.jellyfin.server.ENCODING_SEGMENT_DELETION
import com.cooper.binge.companion.jellyfin.server.ENCODING_SUBTITLE_EXTRACTION
import com.cooper.binge.companion.jellyfin.server.ENCODING_SYSTEM_NATIVE
import com.cooper.binge.companion.jellyfin.server.ENCODING_TEMP_PATH
import com.cooper.binge.companion.jellyfin.server.ENCODING_THREADS
import com.cooper.binge.companion.jellyfin.server.ENCODING_THROTTLING
import com.cooper.binge.companion.jellyfin.server.ENCODING_TONEMAPPING
import com.cooper.binge.companion.jellyfin.server.ENCODING_TONEMAPPING_ALGORITHM
import com.cooper.binge.companion.jellyfin.server.ENCODING_TONEMAPPING_MODE
import com.cooper.binge.companion.jellyfin.server.ENCODING_TONEMAPPING_RANGE
import com.cooper.binge.companion.jellyfin.server.ENCODING_VAAPI_DEVICE
import com.cooper.binge.companion.jellyfin.server.ENCODING_VIDEO_TOOLBOX_TONEMAPPING
import com.cooper.binge.companion.jellyfin.server.ENCODING_VPP_TONEMAPPING
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.bool
import com.cooper.binge.companion.jellyfin.server.decodingCodecs
import com.cooper.binge.companion.jellyfin.server.encodingChoice
import com.cooper.binge.companion.jellyfin.server.hasIntelOptions
import com.cooper.binge.companion.jellyfin.server.hasToneMapping
import com.cooper.binge.companion.jellyfin.server.hasToneMappingSwitch
import com.cooper.binge.companion.jellyfin.server.int
import com.cooper.binge.companion.jellyfin.server.isAccelerated
import com.cooper.binge.companion.jellyfin.server.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** The choices the page opens in a sheet. */
internal enum class TranscodingPicker { Acceleration, Algorithm, Mode, Range, Preset, Threads, Deinterlace, Downmix }

/** Whether the server sent [key]: a setting it doesn't have isn't shown. */
private fun JsonObject.sent(key: String): Boolean = key in this

/** A switch in the encoding configuration, or nothing when the server didn't send it. */
@Composable
private fun TranscodingUiState.Ready.switch(
    key: String,
    icon: ImageVector,
    label: Int,
    actions: TranscodingActions,
    detail: Int? = null,
): ListItem? {
    if (!encoding.sent(key)) return null
    val on = encoding.bool(key) ?: false
    return ListItem(
        icon = icon,
        label = stringResource(label),
        detail = detail?.let { stringResource(it) },
        supportingMaxLines = TRANSCODING_DETAIL_LINES,
        toggled = on,
        onClick = { actions.onEncoding(key, JsonPrimitive(!on)) },
    )
}

/** A row for a number: its value and unit, opening its sheet. */
@Composable
private fun TranscodingUiState.Ready.number(
    field: NumberField,
    icon: ImageVector,
    label: Int,
    actions: TranscodingActions,
): ListItem? {
    val config = if (field.kind == ConfigurationKind.Encoding) encoding else server
    if (!config.sent(field.key)) return null
    return ListItem(
        icon = icon,
        label = stringResource(label),
        detail = numberLabel(field, shownNumber(field, config)),
        onClick = { actions.onSheet(TranscodingSheet.Number(field)) },
    )
}

/** [field]'s stored value as people read it: whole when it is, in the unit shown. */
internal fun shownNumber(
    field: NumberField,
    config: JsonObject,
): String {
    val stored = (config[field.key] as? JsonPrimitive)?.doubleOrNull ?: return ""
    val shown = stored / field.scale
    return if (shown % 1.0 ==
        0.0
    ) {
        shown.toLong().toString()
    } else {
        "%s".format(java.util.Locale.ROOT, shown.toBigDecimal().stripTrailingZeros().toPlainString())
    }
}

@Composable
internal fun numberLabel(
    field: NumberField,
    shown: String,
): String =
    if (field.unit == NumberUnit.Mbps &&
        shown == "0"
    ) {
        stringResource(R.string.transcoding_no_limit)
    } else {
        unitText(field.unit, shown)
    }

/** [shown] with its [unit]. */
@Composable
internal fun unitText(
    unit: NumberUnit,
    shown: String,
): String =
    when (unit) {
        NumberUnit.None -> shown
        NumberUnit.Seconds -> stringResource(R.string.transcoding_unit_seconds, shown)
        NumberUnit.Percent -> stringResource(R.string.transcoding_unit_percent, shown)
        NumberUnit.Minutes -> stringResource(R.string.transcoding_unit_minutes, shown)
        NumberUnit.Mbps -> stringResource(R.string.transcoding_unit_mbps, shown)
    }

/** The rows under the type that belong to it: the devices it reads. */
@Composable
internal fun hardwareRows(
    state: TranscodingUiState.Ready,
    type: String,
    profile: JellyfinServerProfile,
    actions: TranscodingActions,
): List<ListItem> =
    listOfNotNull(
        ListItem(
            icon = Icons.Outlined.Settings,
            label = stringResource(R.string.transcoding_vaapi_device),
            detail = state.encoding.string(ENCODING_VAAPI_DEVICE)?.ifBlank { null } ?: stringResource(R.string.transcoding_not_set),
            onClick = { actions.onSheet(TranscodingSheet.Text(TextField.VaapiDevice)) },
        ).takeIf { type == "vaapi" },
        ListItem(
            icon = Icons.Outlined.Settings,
            label = stringResource(R.string.transcoding_qsv_device),
            detail = state.encoding.string(ENCODING_QSV_DEVICE)?.ifBlank { null } ?: stringResource(R.string.transcoding_not_set),
            onClick = { actions.onSheet(TranscodingSheet.Text(TextField.QsvDevice)) },
        ).takeIf { type == "qsv" && profile.hasQsvDeviceAndDownmix && state.encoding.sent(ENCODING_QSV_DEVICE) },
    )

/** Hardware decoding by codec, and the encoding switches, for a type that does the work in hardware. */
@Composable
internal fun HardwareGroups(
    state: TranscodingUiState.Ready,
    type: String,
    actions: TranscodingActions,
) {
    if (!isAccelerated(type)) return
    val on = (state.encoding[ENCODING_DECODING_CODECS] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
    val codecs = decodingCodecs(type)
    if (codecs.isNotEmpty()) {
        ItemGroup(
            title = stringResource(R.string.transcoding_decoding),
            rows =
                codecs.map { codec ->
                    ListItem(
                        icon = Icons.Outlined.Videocam,
                        label = stringResource(codecLabel(codec)),
                        toggled = codec in on,
                        onClick = { actions.onCodec(codec, codec !in on) },
                    )
                },
        )
    }
    ItemGroup(
        title = stringResource(R.string.transcoding_encoding),
        rows =
            listOfNotNull(
                state.switch(ENCODING_HARDWARE_ENCODING, Icons.Outlined.Memory, R.string.transcoding_hardware_encoding, actions),
                state.switch(ENCODING_HEVC, Icons.Outlined.Hd, R.string.transcoding_hevc, actions, R.string.transcoding_hevc_detail),
                state
                    .switch(
                        ENCODING_INTEL_LOW_POWER_HEVC,
                        Icons.Outlined.Speed,
                        R.string.transcoding_low_power,
                        actions,
                        R.string.transcoding_low_power_detail,
                    ).takeIf { hasIntelOptions(type) },
                state
                    .switch(
                        ENCODING_ENHANCED_NVDEC,
                        Icons.Outlined.Architecture,
                        R.string.transcoding_nvdec,
                        actions,
                        R.string.transcoding_nvdec_detail,
                    ).takeIf { type == "nvenc" },
                state
                    .switch(
                        ENCODING_SYSTEM_NATIVE,
                        Icons.Outlined.Memory,
                        R.string.transcoding_native,
                        actions,
                        R.string.transcoding_native_detail,
                    ).takeIf { type == "qsv" },
            ),
    )
}

@Composable
internal fun ToneMappingGroup(
    state: TranscodingUiState.Ready,
    type: String,
    profile: JellyfinServerProfile,
    actions: TranscodingActions,
    onPick: (TranscodingPicker) -> Unit,
) {
    if (!hasToneMapping(type)) return
    val config = state.encoding
    val hardware = hasToneMappingSwitch(type)
    ItemGroup(
        title = stringResource(R.string.transcoding_tonemapping),
        rows =
            listOfNotNull(
                state
                    .switch(
                        ENCODING_TONEMAPPING,
                        Icons.Outlined.Palette,
                        R.string.transcoding_tonemapping_enable,
                        actions,
                    ).takeIf { hardware },
                state
                    .switch(
                        ENCODING_VPP_TONEMAPPING,
                        Icons.Outlined.Palette,
                        R.string.transcoding_vpp,
                        actions,
                        R.string.transcoding_vpp_detail,
                    ).takeIf { hasIntelOptions(type) },
                state
                    .switch(ENCODING_VIDEO_TOOLBOX_TONEMAPPING, Icons.Outlined.Palette, R.string.transcoding_toolbox, actions)
                    .takeIf { type == "videotoolbox" && profile.hasAdvancedEncoding },
                ListItem(
                    icon = Icons.Outlined.Tune,
                    label = stringResource(R.string.transcoding_algorithm),
                    detail = algorithmLabel(config.string(ENCODING_TONEMAPPING_ALGORITHM) ?: "none"),
                    onClick = { onPick(TranscodingPicker.Algorithm) },
                ).takeIf { config.sent(ENCODING_TONEMAPPING_ALGORITHM) },
                ListItem(
                    icon = Icons.Outlined.Tune,
                    label = stringResource(R.string.transcoding_mode),
                    detail = modeLabel(config.string(ENCODING_TONEMAPPING_MODE) ?: "auto"),
                    onClick = { onPick(TranscodingPicker.Mode) },
                ).takeIf { hardware && config.sent(ENCODING_TONEMAPPING_MODE) },
                ListItem(
                    icon = Icons.Outlined.Tune,
                    label = stringResource(R.string.transcoding_range),
                    detail = modeLabel(config.string(ENCODING_TONEMAPPING_RANGE) ?: "auto"),
                    onClick = { onPick(TranscodingPicker.Range) },
                ).takeIf { config.sent(ENCODING_TONEMAPPING_RANGE) },
                state.number(NumberField.Desaturation, Icons.Outlined.Palette, R.string.transcoding_desaturation, actions),
                state.number(NumberField.PeakBrightness, Icons.Outlined.Brightness6, R.string.transcoding_peak, actions),
                state.number(NumberField.ToneMappingParam, Icons.Outlined.Tune, R.string.transcoding_tonemapping_param, actions),
                state.number(NumberField.VppBrightness, Icons.Outlined.Brightness6, R.string.transcoding_vpp_brightness, actions).takeIf {
                    hasIntelOptions(type)
                },
                state
                    .number(
                        NumberField.VppContrast,
                        Icons.Outlined.Contrast,
                        R.string.transcoding_vpp_contrast,
                        actions,
                    ).takeIf { hasIntelOptions(type) },
            ),
    )
}

@Composable
internal fun QualityGroup(
    state: TranscodingUiState.Ready,
    actions: TranscodingActions,
    onPick: (TranscodingPicker) -> Unit,
) {
    val config = state.encoding
    ItemGroup(
        title = stringResource(R.string.transcoding_quality),
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Outlined.VideoSettings,
                    label = stringResource(R.string.transcoding_preset),
                    detail = presetLabel(encodingChoice(config.string(ENCODING_PRESET), "auto")),
                    onClick = { onPick(TranscodingPicker.Preset) },
                ).takeIf { config.sent(ENCODING_PRESET) },
                ListItem(
                    icon = Icons.Outlined.Layers,
                    label = stringResource(R.string.transcoding_threads),
                    detail = threadsLabel(config.int(ENCODING_THREADS) ?: -1),
                    onClick = { onPick(TranscodingPicker.Threads) },
                ).takeIf { config.sent(ENCODING_THREADS) },
                ListItem(
                    icon = Icons.Outlined.Tune,
                    label = stringResource(R.string.transcoding_deinterlace),
                    detail = deinterlaceLabel(config.string(ENCODING_DEINTERLACE) ?: "yadif"),
                    onClick = { onPick(TranscodingPicker.Deinterlace) },
                ).takeIf { config.sent(ENCODING_DEINTERLACE) },
                state.switch(
                    ENCODING_DEINTERLACE_DOUBLE_RATE,
                    Icons.Outlined.Speed,
                    R.string.transcoding_double_rate,
                    actions,
                    R.string.transcoding_double_rate_detail,
                ),
            ),
    )
}

@Composable
internal fun AudioGroup(
    state: TranscodingUiState.Ready,
    profile: JellyfinServerProfile,
    actions: TranscodingActions,
    onPick: (TranscodingPicker) -> Unit,
) {
    val config = state.encoding
    ItemGroup(
        title = stringResource(R.string.transcoding_audio),
        rows =
            listOfNotNull(
                state.number(NumberField.DownmixBoost, Icons.Outlined.GraphicEq, R.string.transcoding_downmix_boost, actions),
                ListItem(
                    icon = Icons.Outlined.AudioFile,
                    label = stringResource(R.string.transcoding_downmix),
                    detail = downmixLabel(config.string(ENCODING_DOWNMIX_ALGORITHM) ?: "None"),
                    onClick = { onPick(TranscodingPicker.Downmix) },
                ).takeIf { profile.hasAdvancedEncoding && config.sent(ENCODING_DOWNMIX_ALGORITHM) },
                state
                    .switch(
                        ENCODING_AUDIO_VBR,
                        Icons.Outlined.AudioFile,
                        R.string.transcoding_vbr,
                        actions,
                        R.string.transcoding_vbr_detail,
                    ).takeIf { profile.hasAdvancedEncoding },
            ),
    )
}

/** Throttling and segments, subtitles and paths, then when playback resumes, and the bitrate it streams remotely. */
@Composable
internal fun StreamingGroups(
    state: TranscodingUiState.Ready,
    profile: JellyfinServerProfile,
    actions: TranscodingActions,
) {
    ItemGroup(
        title = stringResource(R.string.transcoding_throttling),
        rows =
            listOfNotNull(
                state.switch(
                    ENCODING_THROTTLING,
                    Icons.Outlined.Pause,
                    R.string.transcoding_throttle,
                    actions,
                    R.string.transcoding_throttle_detail,
                ),
                state.number(NumberField.ThrottleDelay, Icons.Outlined.HourglassBottom, R.string.transcoding_throttle_delay, actions),
                state
                    .switch(
                        ENCODING_SEGMENT_DELETION,
                        Icons.Outlined.Layers,
                        R.string.transcoding_segment_deletion,
                        actions,
                        R.string.transcoding_segment_deletion_detail,
                    ).takeIf { profile.hasAdvancedEncoding },
                state.number(NumberField.SegmentKeep, Icons.Outlined.HourglassBottom, R.string.transcoding_segment_keep, actions).takeIf {
                    profile.hasAdvancedEncoding
                },
            ),
    )
    ItemGroup(
        title = stringResource(R.string.transcoding_subtitles),
        rows =
            listOfNotNull(
                state.switch(
                    ENCODING_SUBTITLE_EXTRACTION,
                    Icons.Outlined.Subtitles,
                    R.string.transcoding_extraction,
                    actions,
                    R.string.transcoding_extraction_detail,
                ),
                state.switch(ENCODING_FALLBACK_FONT, Icons.Outlined.FontDownload, R.string.transcoding_fallback_font, actions),
                ListItem(
                    icon = Icons.Outlined.FontDownload,
                    label = stringResource(R.string.transcoding_fallback_font_path),
                    detail =
                        state.encoding
                            .string(
                                ENCODING_FALLBACK_FONT_PATH,
                            )?.ifBlank { null } ?: stringResource(R.string.transcoding_not_set),
                    onClick = { actions.onSheet(TranscodingSheet.Text(TextField.FallbackFont)) },
                ).takeIf { state.encoding.sent(ENCODING_FALLBACK_FONT_PATH) },
            ),
    )
    if (state.encoding.sent(ENCODING_TEMP_PATH)) {
        ItemGroup(
            title = stringResource(R.string.transcoding_paths),
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.Folder,
                        label = stringResource(R.string.transcoding_temp_path),
                        detail =
                            state.encoding
                                .string(
                                    ENCODING_TEMP_PATH,
                                )?.ifBlank { null } ?: stringResource(R.string.transcoding_path_default),
                        onClick = { actions.onSheet(TranscodingSheet.TempFolder) },
                    ),
                ),
        )
    }
    ItemGroup(
        title = stringResource(R.string.transcoding_resume),
        rows =
            listOfNotNull(
                state.number(NumberField.MinResumePercent, Icons.Outlined.History, R.string.transcoding_min_resume, actions),
                state.number(NumberField.MaxResumePercent, Icons.Outlined.History, R.string.transcoding_max_resume, actions),
                state.number(NumberField.MinResumeSeconds, Icons.Outlined.HourglassBottom, R.string.transcoding_min_duration, actions),
                state.number(NumberField.MinAudiobookResume, Icons.Outlined.AudioFile, R.string.transcoding_min_audiobook, actions),
                state.number(NumberField.MaxAudiobookResume, Icons.Outlined.AudioFile, R.string.transcoding_max_audiobook, actions),
            ),
    )
    ItemGroup(
        title = stringResource(R.string.transcoding_streaming),
        rows = listOfNotNull(state.number(NumberField.BitrateLimit, Icons.Outlined.Speed, R.string.transcoding_bitrate, actions)),
    )
}
