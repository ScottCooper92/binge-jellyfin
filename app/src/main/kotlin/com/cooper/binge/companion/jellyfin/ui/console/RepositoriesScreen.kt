package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.component.TextEntrySurface
import com.binge.designsystem.template.EmptyScreen
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

/**
 * The repositories the server fetches its catalog from, with a red Remove on each. Its Add sheet and Remove confirm are
 * [RepositoriesOverlays], drawn by the screen outside the pager, so what is typed in the sheet is saved with the screen.
 */
@Composable
internal fun RepositoriesTab(
    state: RepositoriesUiState,
    actions: RepositoriesActions,
    inner: PaddingValues,
    listState: LazyListState,
) {
    when (state) {
        RepositoriesUiState.Loading -> PluginsLoading(inner)
        is RepositoriesUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry)
        is RepositoriesUiState.Ready -> {
            if (state.repositories.isEmpty()) {
                EmptyScreen(
                    message = stringResource(R.string.repositories_empty),
                    title = stringResource(R.string.repositories_empty_title),
                )
            } else {
                RepositoryList(state, actions, inner, listState)
            }
        }
    }
}

/** The Add repository sheet and the Remove confirm, when [state] asks for them. */
@Composable
internal fun RepositoriesOverlays(
    state: RepositoriesUiState,
    actions: RepositoriesActions,
) {
    if (state !is RepositoriesUiState.Ready) return
    if (state.adding) AddRepositorySheet(state.saving, actions)
    state.removing?.let { RemoveRepositoryDialog(it, actions) }
}

@Composable
private fun RepositoryList(
    state: RepositoriesUiState.Ready,
    actions: RepositoriesActions,
    inner: PaddingValues,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        item(key = "intro") {
            PageIntro(stringResource(R.string.repositories_body))
        }
        item(key = "repositories") { ItemGroup(title = null, rows = state.repositories.map { it.row(state, actions) }) }
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
    // Saved out here, not in the form: the sheet is a window, and what a window's content saves is not restored with it.
    var step by rememberSaveable(key = "AddRepositorySheet.step") { mutableIntStateOf(0) }
    var name by rememberSaveable(key = "AddRepositorySheet.name") { mutableStateOf("") }
    var url by rememberSaveable(key = "AddRepositorySheet.url") { mutableStateOf("") }
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = { actions.onAskAdd(false) }) {
        AddRepositoryFields(
            step = step,
            name = name,
            url = url,
            saving = saving,
            actions = actions,
            onStep = { step = it },
            onName = { name = it },
            onUrl = { url = it },
        )
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
    var step by rememberSaveable(key = "AddRepositoryForm.step") { mutableIntStateOf(startStep) }
    var name by rememberSaveable(key = "AddRepositoryForm.name") { mutableStateOf("") }
    var url by rememberSaveable(key = "AddRepositoryForm.url") { mutableStateOf("") }
    AddRepositoryFields(
        step = step,
        name = name,
        url = url,
        saving = saving,
        actions = actions,
        onStep = { step = it },
        onName = { name = it },
        onUrl = { url = it },
    )
}

@Composable
private fun AddRepositoryFields(
    step: Int,
    name: String,
    url: String,
    saving: Boolean,
    actions: RepositoriesActions,
    onStep: (Int) -> Unit,
    onName: (String) -> Unit,
    onUrl: (String) -> Unit,
) {
    val valid = repositoryUrl(url) != null
    if (step == 0) {
        TextEntrySurface(
            title = stringResource(R.string.repositories_name_title),
            value = name,
            onValueChange = onName,
            onSubmit = { onStep(1) },
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
            onValueChange = onUrl,
            onSubmit = { actions.onAdd(name, url) },
            onCancel = { onStep(0) },
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
