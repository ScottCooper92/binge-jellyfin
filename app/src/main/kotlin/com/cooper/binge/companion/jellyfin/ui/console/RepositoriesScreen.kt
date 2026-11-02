package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.TextEntrySurface
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenAction
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.PluginRepository
import com.binge.designsystem.R as DesR

/** What the repositories tab can ask of its view model. */
data class RepositoriesActions(
    val onAskAdd: (Boolean) -> Unit = {},
    val onAdd: (name: String, url: String) -> Unit = { _, _ -> },
    val onAskRemove: (PluginRepository?) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** The repositories the server fetches its catalog from, with Add repository and a red Remove on each. */
@Composable
internal fun RepositoriesTab(
    state: RepositoriesUiState,
    actions: RepositoriesActions,
    inner: PaddingValues,
) {
    when (state) {
        RepositoriesUiState.Loading -> PluginsLoading(inner)
        is RepositoriesUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry)
        is RepositoriesUiState.Ready -> {
            if (state.repositories.isEmpty()) {
                EmptyScreen(
                    message = stringResource(R.string.repositories_empty),
                    title = stringResource(R.string.repositories_empty_title),
                    action = ScreenAction(stringResource(R.string.repositories_add), { actions.onAskAdd(true) }, Icons.Outlined.Add),
                )
            } else {
                RepositoryList(state, actions, inner)
            }
            if (state.adding) AddRepositorySheet(state.saving, actions)
            state.removing?.let { RemoveRepositoryDialog(it, actions) }
        }
    }
}

@Composable
private fun RepositoryList(
    state: RepositoriesUiState.Ready,
    actions: RepositoriesActions,
    inner: PaddingValues,
) {
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
                Text(stringResource(R.string.repositories_body), style = MaterialTheme.typography.bodyMedium)
                BingeOutlinedButton(
                    label = stringResource(R.string.repositories_add),
                    onClick = { actions.onAskAdd(true) },
                    leadingIcon = Icons.Outlined.Add,
                    loading = state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { ItemGroup(title = null, rows = state.repositories.map { it.row(state, actions) }) }
        item { FailureLine(state.actionFailed) }
    }
}

@Composable
private fun PluginRepository.row(
    state: RepositoriesUiState.Ready,
    actions: RepositoriesActions,
): ListItem =
    ListItem(
        icon = Icons.Outlined.CloudQueue,
        label = name.ifBlank { url },
        detail = url.takeIf { name.isNotBlank() },
        clickable = false,
        trailingContent =
            if (state.saving) {
                null
            } else {
                {
                    ExpressiveIconButton(
                        onClick = { actions.onAskRemove(this) },
                        icon = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.repositories_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
    )

/** Removing a repository, after its name, as a confirm that opens on Cancel. [inWindow] false draws its body for a frame. */
@Composable
internal fun RemoveRepositoryDialog(
    repository: PluginRepository,
    actions: RepositoriesActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.repositories_remove_title, repository.name.ifBlank { repository.url }),
        message = stringResource(R.string.repositories_remove_body),
        confirmLabel = stringResource(R.string.repositories_remove),
        onConfirm = actions.onRemove,
        onDismissRequest = { actions.onAskRemove(null) },
        destructive = true,
        inWindow = inWindow,
    )
}

/** The new repository sheet: a task sheet over the page, closed by its own Add or by dismissing it. */
@Composable
internal fun AddRepositorySheet(
    saving: Boolean,
    actions: RepositoriesActions,
) {
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = { actions.onAskAdd(false) }) {
        AddRepositoryForm(saving, actions)
    }
}

/**
 * A new repository in two steps, each the design system's text-entry surface: its name, then its address,
 * with a warning that it can serve code the server runs. The address must be a web address before Add sends it.
 * It is the content of [AddRepositorySheet], and draws without a window for a frame.
 */
@Composable
internal fun AddRepositoryForm(
    saving: Boolean,
    actions: RepositoriesActions,
    startStep: Int = 0,
) {
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val valid = repositoryUrl(url) != null
    if (step == 0) {
        TextEntrySurface(
            title = stringResource(R.string.repositories_name_title),
            value = name,
            onValueChange = { name = it },
            onSubmit = { step = 1 },
            onCancel = { actions.onAskAdd(false) },
            submitLabel = stringResource(R.string.repositories_next),
            hint = stringResource(R.string.repositories_name_hint),
            minLines = 1,
            maxLines = 1,
            maxLength = NAME_MAX,
        )
    } else {
        TextEntrySurface(
            title = stringResource(R.string.repositories_url_title),
            value = url,
            onValueChange = { url = it },
            onSubmit = { actions.onAdd(name, url) },
            onCancel = { step = 0 },
            cancelLabel = stringResource(R.string.repositories_back),
            submitLabel = stringResource(R.string.repositories_add_submit),
            hint = stringResource(R.string.repositories_url_hint),
            submitEnabled = valid,
            isSubmitting = saving,
            error = stringResource(R.string.repositories_url_invalid).takeIf { url.isNotBlank() && !valid },
            minLines = 1,
            maxLines = 2,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            header = {
                Text(
                    stringResource(R.string.repositories_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            },
        )
    }
}

private const val NAME_MAX = 60
