package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.PlaylistAdd
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
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
import com.binge.designsystem.R as DesR

/** The guide form, or the choice of what to add, over the Live TV page, in a window. */
@Composable
internal fun GuideSheet(
    state: GuideUiState,
    actions: LiveTvGuideActions,
) {
    if (state.choosing) {
        ConsoleSheet(onDismissRequest = actions.onDismissChoice) { ChooseGuideContent(actions) }
        return
    }
    val form = state.form ?: return
    ConsoleSheet(onDismissRequest = actions.onDismissForm) { GuideFormContent(form, state.saving, actions) }
}

/** What to add: an XMLTV guide, or a Schedules Direct account. Only offered where the user may read lineups. */
@Composable
internal fun ChooseGuideContent(actions: LiveTvGuideActions) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Column(Modifier.padding(dimensionResource(DesR.dimen.padding_m))) {
            Text(
                text = stringResource(R.string.live_tv_guide_add),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.live_tv_guide_choose_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ItemGroup(
            title = null,
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.Description,
                        label = stringResource(R.string.live_tv_type_xmltv),
                        detail = stringResource(R.string.live_tv_guide_add_detail),
                        supportingMaxLines = CHOICE_LINES,
                        onClick = actions.onAddXmlTv,
                    ),
                    ListItem(
                        icon = Icons.Outlined.Dns,
                        label = stringResource(R.string.live_tv_type_schedules_direct),
                        detail = stringResource(R.string.live_tv_guide_sd_detail),
                        supportingMaxLines = CHOICE_LINES,
                        onClick = actions.onAddSchedulesDirect,
                    ),
                ),
        )
    }
}

private const val CHOICE_LINES = 3

/** The guide form's body, which draws without a window for a frame. */
@Composable
internal fun GuideFormContent(
    form: GuideForm,
    saving: Boolean,
    actions: LiveTvGuideActions,
) {
    var path by rememberSaveable(form) { mutableStateOf(form.path) }
    ConsoleFieldsForm(
        title = stringResource(if (form.id == null) R.string.live_tv_guide_form_add else R.string.live_tv_guide_form_edit),
        body =
            stringResource(
                when {
                    form.isXmlTv -> R.string.live_tv_guide_path_detail
                    form.lineupPending -> R.string.live_tv_guide_pending_detail
                    else -> R.string.live_tv_guide_other_detail
                },
            ),
        submitLabel =
            stringResource(
                when {
                    !form.isXmlTv -> R.string.live_tv_guide_close
                    form.id == null -> R.string.live_tv_guide_add_submit
                    else -> R.string.library_save
                },
            ),
        onSubmit = { if (form.isXmlTv) actions.onSave(form.copy(path = path)) else actions.onDismissForm() },
        onCancel = actions.onDismissForm,
        submitting = saving,
    ) {
        if (form.isXmlTv) {
            ConsoleTextField(
                value = path,
                onValueChange = { path = it },
                label = stringResource(R.string.live_tv_guide_path),
                enabled = !saving,
                keyboardType = KeyboardType.Uri,
            )
        }
        if (form.id != null) {
            ItemGroup(
                title = null,
                rows =
                    listOfNotNull(
                        ListItem(
                            icon = Icons.Outlined.PlaylistAdd,
                            label = stringResource(R.string.live_tv_guide_finish),
                            onClick = { actions.onFinishSetup(form.id) },
                        ).takeIf { form.lineupPending },
                        ListItem(
                            icon = Icons.Outlined.Delete,
                            iconTint = MaterialTheme.colorScheme.error,
                            label = stringResource(R.string.live_tv_guide_remove),
                            onClick = { actions.onAskRemove(form) },
                        ),
                    ),
            )
        }
    }
}

/** What a guide change says: the remove confirm, a dialog for a provider the server would not keep, and the queued refresh. */
@Composable
internal fun GuideMessages(
    state: GuideUiState,
    actions: LiveTvGuideActions,
    host: SnackbarHostState,
) {
    SnackbarMessage(
        when {
            state.problem == GuideProblem.NotChanged -> stringResource(R.string.live_tv_guide_not_changed)
            state.queued -> stringResource(R.string.live_tv_guide_queued)
            else -> null
        },
        host,
    )
    if (state.problem == GuideProblem.NotSaved) {
        ConsoleMessage(
            title = stringResource(R.string.live_tv_guide_not_saved_title),
            message = stringResource(R.string.live_tv_guide_not_saved_body),
            onDismiss = actions.onDismissProblem,
        )
    }
    state.removing?.let { guide ->
        ConsoleConfirm(
            title = stringResource(R.string.live_tv_guide_remove_title),
            message = stringResource(R.string.live_tv_guide_remove_message, guide.name),
            confirmLabel = stringResource(R.string.live_tv_guide_remove_confirm),
            onConfirm = actions.onRemove,
            onDismissRequest = actions.onCancelRemove,
            destructive = true,
        )
    }
}
