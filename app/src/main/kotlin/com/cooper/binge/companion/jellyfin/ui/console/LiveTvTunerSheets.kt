package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
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
    ConsoleSheet(onDismissRequest = { actions.onSheet(null) }) { TunerSheetContent(sheet, state.saving, actions) }
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
        TunerSheet.Discovering -> Discovering()
        is TunerSheet.Found -> FoundTuners(sheet.devices, actions)
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
        ItemGroup(
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

@Composable
private fun Discovering() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        SheetHeader(stringResource(R.string.live_tv_finding_title), stringResource(R.string.live_tv_finding_body))
        CircularProgressIndicator(Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)))
    }
}

@Composable
private fun FoundTuners(
    devices: List<TunerDraft>,
    actions: LiveTvActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        if (devices.isEmpty()) {
            SheetHeader(stringResource(R.string.live_tv_found_title), stringResource(R.string.live_tv_found_none))
        } else {
            Text(
                text = stringResource(R.string.live_tv_found_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
            )
            ItemGroup(
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
}

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
    val edited =
        draft.copy(
            url = url,
            name = name,
            userAgent = agent,
            streamLimit = limit,
            favoritesOnly = favorites,
            hardwareTranscoding = hardware,
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
        TunerSwitches(draft, favorites, { favorites = it }, hardware, { hardware = it })
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
            TunerProblem.DiscoverFailed -> stringResource(R.string.live_tv_discover_failed)
            else -> null
        },
        host,
    )
    if (state.problem == TunerProblem.NotAdded) {
        ConsoleMessage(
            title = stringResource(R.string.live_tv_not_added_title),
            message = stringResource(R.string.live_tv_not_added_body),
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
