package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.TUNER_FMP4
import com.cooper.binge.companion.jellyfin.server.TUNER_IGNORE_DTS
import com.cooper.binge.companion.jellyfin.server.TUNER_STREAM_LOOPING
import com.cooper.binge.companion.jellyfin.server.TUNER_STREAM_OPTIONS
import com.cooper.binge.companion.jellyfin.server.TUNER_STREAM_SHARING
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_HDHOMERUN
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_M3U
import com.cooper.binge.companion.jellyfin.server.withoutSecrets
import com.binge.designsystem.R as DesR

/** Whichever sheet is open over the Live TV page. */
@Composable
internal fun TunerSheets(
    state: LiveTvUiState.Ready,
    actions: LiveTvActions,
) {
    val sheet = state.sheet ?: return
    ConsoleSheet(onDismissRequest = { actions.onSheet(null) }) {
        // Back steps back to the sheet this one was opened from (the choice, or the search's results); from the first it closes the sheet.
        StepBack(sheet.previous()?.takeUnless { state.saving }?.let { previous -> { actions.onSheet(previous) } })
        TunerSheetContent(sheet, state.saving, actions)
    }
}

/** [sheet]'s body, which draws without a window for a frame. */
@Composable
internal fun TunerSheetContent(
    sheet: TunerSheet,
    saving: Boolean,
    actions: LiveTvActions,
) {
    when (sheet) {
        TunerSheet.Choose -> ChooseTuner(actions)
        TunerSheet.Discovering, is TunerSheet.Found, is TunerSheet.Failed -> TunerDiscovery(sheet, actions)
        is TunerSheet.Form -> TunerForm(sheet.draft, saving, actions)
    }
}

@Composable
private fun SheetHeader(
    title: String,
    body: String,
) {
    Column(Modifier.padding(dimensionResource(DesR.dimen.padding_m))) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChooseTuner(actions: LiveTvActions) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        SheetHeader(stringResource(R.string.live_tv_add_title), stringResource(R.string.live_tv_add_body))
        ConsoleSheetGroup(
            title = null,
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.LiveTv,
                        label = stringResource(R.string.live_tv_type_hdhomerun),
                        detail = stringResource(R.string.live_tv_pick_hdhomerun_detail),
                        onClick = { actions.onForm(TunerDraft(TUNER_TYPE_HDHOMERUN)) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.Dns,
                        label = stringResource(R.string.live_tv_type_m3u),
                        detail = stringResource(R.string.live_tv_pick_m3u_detail),
                        supportingMaxLines = DETAIL_LINES,
                        onClick = { actions.onForm(TunerDraft(TUNER_TYPE_M3U)) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.Search,
                        label = stringResource(R.string.live_tv_find),
                        detail = stringResource(R.string.live_tv_find_detail),
                        supportingMaxLines = DETAIL_LINES,
                        onClick = actions.onDiscover,
                    ),
                ),
        )
    }
}

/** All discovery outcomes share their header, body slot and always-present actions. */
@Composable
private fun TunerDiscovery(
    sheet: TunerSheet,
    actions: LiveTvActions,
) {
    val searching = sheet == TunerSheet.Discovering
    val devices = (sheet as? TunerSheet.Found)?.devices.orEmpty()
    val failure = (sheet as? TunerSheet.Failed)?.failure
    val title =
        when {
            searching -> R.string.live_tv_finding_title
            failure != null -> R.string.live_tv_discover_failed
            devices.isEmpty() -> R.string.live_tv_found_none_title
            else -> R.string.live_tv_found_title
        }
    val subtitle =
        when {
            failure != null -> failure.text()
            !searching && devices.isEmpty() -> stringResource(R.string.live_tv_found_none)
            else -> stringResource(R.string.live_tv_finding_body)
        }
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        SheetHeader(stringResource(title), subtitle)
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = DISCOVERY_BODY_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            if (searching) CircularProgressIndicator()
            if (devices.isNotEmpty()) {
                ConsoleSheetGroup(
                    title = null,
                    rows =
                        devices.map { device ->
                            ListItem(
                                icon = Icons.Outlined.LiveTv,
                                label = device.name.ifBlank { device.url.withoutSecrets() },
                                detail = device.url.withoutSecrets().takeIf { device.name.isNotBlank() },
                                supportingMaxLines = DETAIL_LINES,
                                onClick = { actions.onForm(device) },
                            )
                        },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_m)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            BingeOutlinedButton(
                label = stringResource(R.string.live_tv_discover_retry),
                onClick = actions.onDiscover,
                enabled = !searching,
                modifier = Modifier.weight(1f),
            )
            BingeFilledButton(
                label = stringResource(R.string.live_tv_add_by_address),
                onClick = { actions.onForm(TunerDraft(TUNER_TYPE_HDHOMERUN)) },
                enabled = !searching,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val DISCOVERY_BODY_HEIGHT = 96.dp

/** A tuner's form: its address and what that kind of tuner takes, with Remove last, behind a confirm, for one that exists. */
@Composable
internal fun TunerForm(
    draft: TunerDraft,
    saving: Boolean,
    actions: LiveTvActions,
) {
    var url by rememberSaveable(draft) { mutableStateOf(draft.url) }
    var name by rememberSaveable(draft) { mutableStateOf(draft.name) }
    var agent by rememberSaveable(draft) { mutableStateOf(draft.userAgent) }
    var limit by rememberSaveable(draft) { mutableStateOf(draft.streamLimit) }
    var favorites by rememberSaveable(draft) { mutableStateOf(draft.favoritesOnly) }
    var hardware by rememberSaveable(draft) { mutableStateOf(draft.hardwareTranscoding) }
    // Only the keys the server sent, and the ones on, as a list a saved state can hold.
    var fallback by rememberSaveable(draft) { mutableStateOf(draft.fallbackMbps) }
    var optionsOn by rememberSaveable(draft) {
        mutableStateOf(
            draft.options
                .filterValues { it }
                .keys
                .toList(),
        )
    }
    val edited =
        draft.copy(
            url = url,
            name = name,
            userAgent = agent,
            streamLimit = limit,
            favoritesOnly = favorites,
            hardwareTranscoding = hardware,
            options = draft.options.mapValues { (key, _) -> key in optionsOn },
            fallbackMbps = fallback,
        )
    ConsoleFieldsForm(
        title = stringResource(formTitle(draft)),
        body = stringResource(if (draft.isM3u) R.string.live_tv_field_playlist_detail else R.string.live_tv_field_address_detail),
        submitLabel = stringResource(if (draft.id == null) R.string.live_tv_form_add else R.string.library_save),
        onSubmit = { if (edited.valid) actions.onSave(edited) },
        onCancel = { actions.onSheet(null) },
        submitting = saving,
    ) {
        ConsoleTextField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(if (draft.isM3u) R.string.live_tv_field_playlist else R.string.live_tv_field_address),
            enabled = !saving,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
        )
        if (draft.isM3u) {
            M3uFields(
                name,
                { name = it },
                agent,
                { agent = it },
                limit,
                { limit = it },
                !saving,
                edited.streamLimitValue == null,
            )
        }
        FallbackBitrateField(fallback, edited.fallbackInvalid, !saving) { fallback = it }
        TunerSwitches(draft, favorites, { favorites = it }, hardware, { hardware = it })
        if (draft.isM3u) StreamOptions(draft, optionsOn) { key, on -> optionsOn = if (on) optionsOn + key else optionsOn - key }
        if (draft.id != null) RemoveRow(draft.id, actions)
    }
}

private fun formTitle(draft: TunerDraft): Int =
    when {
        draft.id != null -> R.string.live_tv_form_edit
        draft.isM3u -> R.string.live_tv_form_add_m3u
        else -> R.string.live_tv_form_add_hdhomerun
    }

@Composable
private fun M3uFields(
    name: String,
    onName: (String) -> Unit,
    agent: String,
    onAgent: (String) -> Unit,
    limit: String,
    onLimit: (String) -> Unit,
    enabled: Boolean,
    limitInvalid: Boolean,
) {
    ConsoleTextField(
        value = name,
        onValueChange = onName,
        label = stringResource(R.string.live_tv_field_name),
        supporting = stringResource(R.string.live_tv_field_name_detail),
        enabled = enabled,
        imeAction = ImeAction.Next,
    )
    ConsoleTextField(
        value = agent,
        onValueChange = onAgent,
        label = stringResource(R.string.live_tv_field_user_agent),
        supporting = stringResource(R.string.live_tv_field_user_agent_detail),
        enabled = enabled,
        imeAction = ImeAction.Next,
    )
    ConsoleTextField(
        value = limit,
        onValueChange = onLimit,
        label = stringResource(R.string.live_tv_field_limit),
        supporting = stringResource(R.string.live_tv_field_limit_detail),
        isError = limitInvalid,
        enabled = enabled,
        keyboardType = KeyboardType.Number,
    )
}

/** The bitrate the server assumes for a source it cannot probe, in Mbps, where the server sent the key (10.10 on). */
@Composable
private fun FallbackBitrateField(
    value: String?,
    invalid: Boolean,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    if (value == null) return
    ConsoleTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(R.string.live_tv_field_fallback),
        supporting = stringResource(R.string.live_tv_field_fallback_detail),
        isError = invalid,
        enabled = enabled,
        keyboardType = KeyboardType.Decimal,
    )
}

@Composable
private fun TunerSwitches(
    draft: TunerDraft,
    favorites: Boolean,
    onFavorites: (Boolean) -> Unit,
    hardware: Boolean,
    onHardware: (Boolean) -> Unit,
) {
    ItemGroup(
        title = null,
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Outlined.Star,
                    label = stringResource(R.string.live_tv_favorites_only),
                    toggled = favorites,
                    onClick = { onFavorites(!favorites) },
                ).takeIf { draft.isHdHomeRun },
                ListItem(
                    icon = Icons.Outlined.Memory,
                    label = stringResource(R.string.live_tv_hw_transcoding),
                    toggled = hardware,
                    onClick = { onHardware(!hardware) },
                ),
            ),
    )
}

/** An M3U host's stream options, each shown where the server sent its key and saved with the rest of the form. */
@Composable
internal fun StreamOptions(
    draft: TunerDraft,
    on: List<String>,
    onChange: (key: String, on: Boolean) -> Unit,
) {
    val rows =
        TUNER_STREAM_OPTIONS.filter { it in draft.options }.map { key ->
            val (label, detail, icon) = streamOption(key)
            ListItem(
                icon = icon,
                label = stringResource(label),
                detail = stringResource(detail),
                supportingMaxLines = OPTION_LINES,
                toggled = key in on,
                onClick = { onChange(key, key !in on) },
            )
        }
    if (rows.isNotEmpty()) ItemGroup(title = stringResource(R.string.live_tv_stream_options), rows = rows)
}

private fun streamOption(key: String): Triple<Int, Int, ImageVector> =
    when (key) {
        TUNER_FMP4 -> Triple(R.string.live_tv_opt_fmp4, R.string.live_tv_opt_fmp4_detail, Icons.Outlined.VideoFile)
        TUNER_STREAM_SHARING -> Triple(R.string.live_tv_opt_sharing, R.string.live_tv_opt_sharing_detail, Icons.Outlined.Share)
        TUNER_STREAM_LOOPING -> Triple(R.string.live_tv_opt_looping, R.string.live_tv_opt_looping_detail, Icons.Outlined.Repeat)
        TUNER_IGNORE_DTS -> Triple(R.string.live_tv_opt_dts, R.string.live_tv_opt_dts_detail, Icons.Outlined.Timer)
        else -> Triple(R.string.live_tv_opt_framerate, R.string.live_tv_opt_framerate_detail, Icons.Outlined.Speed)
    }

@Composable
private fun RemoveRow(
    id: String,
    actions: LiveTvActions,
) {
    ItemGroup(
        title = null,
        rows =
            listOf(
                ListItem(
                    icon = Icons.Outlined.Delete,
                    iconTint = MaterialTheme.colorScheme.error,
                    label = stringResource(R.string.live_tv_remove_tuner),
                    onClick = { actions.onAskRemove(id) },
                ),
            ),
    )
}

private const val DETAIL_LINES = 3

/** A stream option's explanation, which the user may need whole to decide: it wraps rather than being cut at the usual three lines. */
private const val OPTION_LINES = 6

/** What a tuner change that failed says: a dialog where it has a cause and a next step, else a snackbar, and the remove confirm. */
@Composable
internal fun TunerMessages(
    state: LiveTvUiState.Ready,
    actions: LiveTvActions,
    host: SnackbarHostState,
) {
    SnackbarMessage(
        when (state.problem) {
            TunerProblem.NotChanged -> stringResource(R.string.live_tv_not_changed)
            TunerProblem.ServerUnreachable -> stringResource(R.string.live_tv_not_changed_unreachable)
            else -> null
        },
        host,
    )
    if (state.problem == TunerProblem.NotSaved) {
        ConsoleMessage(
            title = stringResource(R.string.live_tv_not_saved_title),
            message = stringResource(R.string.live_tv_not_saved_body),
            onDismiss = actions.onDismissProblem,
        )
    }
    state.removing?.let { tuner ->
        ConsoleConfirm(
            title = stringResource(R.string.live_tv_remove_title),
            message = stringResource(R.string.live_tv_remove_message, tuner.name),
            confirmLabel = stringResource(R.string.live_tv_remove_confirm),
            onConfirm = actions.onRemove,
            onDismissRequest = actions.onCancelRemove,
            destructive = true,
        )
    }
}
