package com.cooper.homelab.ephyra.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.component.TextEntrySurface
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.formSaveStatus
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.server.NFO_EXTRA_THUMBS
import com.cooper.homelab.ephyra.server.NFO_PATH_SUBSTITUTION
import com.cooper.homelab.ephyra.server.NFO_RELEASE_DATE_FORMAT
import com.cooper.homelab.ephyra.server.NFO_SAVE_IMAGE_PATHS
import com.cooper.homelab.ephyra.server.bool
import com.cooper.homelab.ephyra.server.string
import com.cooper.homelab.ephyra.ui.search.SettingRows
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.binge.designsystem.R as DesR

/** What the NFO page can ask of its view model, and of the console. */
data class NfoActions(
    val onSet: (key: String, value: JsonElement) -> Unit = { _, _ -> },
    val onUser: (String?) -> Unit = {},
    val onDateFormat: (String) -> Unit = {},
    val onSheet: (NfoSheet?) -> Unit = {},
    val onLoadUsers: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetrySave: () -> Unit = {},
    val onBack: () -> Unit = {},
)

private const val LOADING_ROWS = 5

/** Lines a switch's detail may wrap to, so it reads in full at large fonts. */
private const val DETAIL_LINES = 4

/** The choice key for "no user", which no user id can be. */
private const val NO_USER = ""

/**
 * The server-wide half of NFO (Kodi) metadata: whose watch state goes into the files, how a release date is written, and what else
 * is saved in them. Each change saves as it's made. A setting the server did not send is not shown.
 */
@Composable
fun NfoScreen(
    state: NfoUiState,
    actions: NfoActions,
) {
    val host = remember { SnackbarHostState() }
    val ready = state as? NfoUiState.Ready
    FormScreen(
        title = stringResource(R.string.nfo_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        saveStatus = ready?.save?.let { formSaveStatus(it.status, it.failed, actions.onRetrySave) },
        notReady =
            when (state) {
                NfoUiState.Loading -> { padding -> ConsoleLoading(padding, LOADING_ROWS, intro = stringResource(R.string.nfo_body)) }
                is NfoUiState.Failed -> { padding -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(padding)) }
                is NfoUiState.Ready -> null
            },
    ) { _ ->
        if (ready != null) {
            NfoBody(ready, actions)
            NfoSheets(ready, actions)
            RefusedMessage(ready.refused, host)
        }
    }
}

@Composable
private fun NfoBody(
    state: NfoUiState.Ready,
    actions: NfoActions,
) {
    val config = state.config
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        PageIntro(stringResource(R.string.nfo_body))
        ItemGroup(
            title = null,
            modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
            rows =
                listOfNotNull(
                    // Always shown: a real server leaves `UserId` out when no user is set, and the row is how one is chosen.
                    userRow(state, actions),
                    config.string(NFO_RELEASE_DATE_FORMAT)?.let { format ->
                        ListItem(
                            icon = Icons.Outlined.CalendarMonth,
                            label = stringResource(R.string.nfo_date_format),
                            detail = format,
                            onClick = { actions.onSheet(NfoSheet.DateFormat) },
                        )
                    },
                    switchRow(
                        config,
                        actions,
                        NFO_SAVE_IMAGE_PATHS,
                        Icons.Outlined.Image,
                        R.string.nfo_image_paths,
                        R.string.nfo_image_paths_detail,
                    ),
                    switchRow(
                        config,
                        actions,
                        NFO_PATH_SUBSTITUTION,
                        Icons.Outlined.SwapHoriz,
                        R.string.nfo_path_substitution,
                        R.string.nfo_path_substitution_detail,
                    ),
                    switchRow(
                        config,
                        actions,
                        NFO_EXTRA_THUMBS,
                        Icons.Outlined.ContentCopy,
                        R.string.nfo_extra_thumbs,
                        R.string.nfo_extra_thumbs_detail,
                    ),
                ),
        )
    }
}

/** The user row: their name, "None", or "Another user" when the id is one the server's list does not hold (or could not be read). */
@Composable
private fun userRow(
    state: NfoUiState.Ready,
    actions: NfoActions,
): ListItem {
    val id = state.config.nfoUserId()
    val name = id?.let { wanted -> state.users?.firstOrNull { it.id.equals(wanted, ignoreCase = true) }?.name }
    return ListItem(
        icon = Icons.Outlined.Person,
        label = stringResource(R.string.nfo_user),
        detail = name ?: stringResource(if (id == null) R.string.nfo_user_none else R.string.nfo_user_other),
        onClick = { actions.onSheet(NfoSheet.User) },
    )
}

@Composable
private fun switchRow(
    config: JsonObject,
    actions: NfoActions,
    key: String,
    icon: ImageVector,
    label: Int,
    detail: Int,
): ListItem? {
    if (key !in config) return null
    val on = config.bool(key) ?: false
    return ListItem(
        icon = icon,
        label = stringResource(label),
        detail = stringResource(detail),
        supportingMaxLines = DETAIL_LINES,
        toggled = on,
        key = SettingRows.nfo(key),
        onClick = { actions.onSet(key, JsonPrimitive(!on)) },
    )
}

/** Whichever sheet is open over the page. */
@Composable
private fun NfoSheets(
    state: NfoUiState.Ready,
    actions: NfoActions,
) {
    when (state.sheet) {
        NfoSheet.User -> NfoUserSheet(state, actions)
        NfoSheet.DateFormat -> NfoDateFormatSheet(state.config.string(NFO_RELEASE_DATE_FORMAT).orEmpty(), actions)
        null -> Unit
    }
}

/** None, then each of the server's users. A list that could not be read says so, and still lets the user choose None. */
@Composable
private fun NfoUserSheet(
    state: NfoUiState.Ready,
    actions: NfoActions,
) {
    val none = BingeChoice(NO_USER, stringResource(R.string.nfo_user_none), icon = Icons.Outlined.Person)
    val users = state.users
    val choices: BingeChoiceList<String> =
        if (users == null) {
            if (state.usersLoading) {
                BingeChoiceList.Loading
            } else {
                BingeChoiceList.Failed(
                    message = stringResource(R.string.nfo_users_failed),
                    retryLabel = stringResource(R.string.console_retry),
                    onRetry = actions.onLoadUsers,
                )
            }
        } else {
            BingeChoiceList.Ready(listOf(none) + users.map { BingeChoice(it.id, it.name, icon = Icons.Outlined.Person) })
        }
    BingeChoiceSheet(
        title = stringResource(R.string.nfo_user),
        choices = choices,
        selected = state.config.nfoUserId() ?: NO_USER,
        onSelect = { key -> actions.onUser(key.takeIf { it != NO_USER }) },
        onDismissRequest = { actions.onSheet(null) },
    )
}

/** The release date format, typed. It saves with the sheet's button, and a blank format can't be saved. */
@Composable
private fun NfoDateFormatSheet(
    current: String,
    actions: NfoActions,
) {
    var format by rememberSaveable { mutableStateOf(current) }
    BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = { actions.onSheet(null) }) {
        TextEntrySurface(
            title = stringResource(R.string.nfo_date_format),
            value = format,
            onValueChange = { format = it },
            onSubmit = { actions.onDateFormat(format) },
            onCancel = { actions.onSheet(null) },
            cancelLabel = stringResource(R.string.library_back),
            submitLabel = stringResource(R.string.library_save),
            hint = stringResource(R.string.nfo_date_format_hint),
            submitEnabled = format.isNotBlank(),
            minLines = 1,
            maxLines = 1,
        )
    }
}
