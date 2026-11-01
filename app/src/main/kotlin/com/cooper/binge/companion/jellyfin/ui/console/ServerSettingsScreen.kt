package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.binge.designsystem.R as DesR

/** What the server settings page can ask of its view model, and of the console. */
data class ServerSettingsActions(
    val onEdit: (ServerText?) -> Unit = {},
    val onSaveText: (ServerText, String) -> Unit = { _, _ -> },
    val onMetadataLanguage: (String) -> Unit = {},
    val onMetadataCountry: (String) -> Unit = {},
    val onQuickConnect: (Boolean) -> Unit = {},
    val onSplashscreen: (Boolean) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Lines a failed save's message takes, so it never moves the rows. */
private const val SETTINGS_STATUS_LINES = 2

/** Lines the custom CSS field shows before it scrolls. */
private const val CSS_LINES = 8

/**
 * The server's general settings (its name, the language and country metadata is fetched for, Quick
 * Connect) and the login branding. Each change saves as it's made; a text saves when its dialog does.
 * The splash screen switch shows only on a server that has one ([splashscreenAvailable], 10.9).
 */
@Composable
fun ServerSettingsScreen(
    state: ServerSettingsUiState,
    actions: ServerSettingsActions,
    splashscreenAvailable: Boolean = true,
) {
    var picker by rememberSaveable { mutableStateOf<ServerPicker?>(null) }
    BingeScreenScaffold(title = stringResource(R.string.server_settings_title), bar = ScreenBar.Small, onBack = actions.onBack) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                ServerSettingsUiState.Loading ->
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { BingeLoadingIndicator() }
                is ServerSettingsUiState.Failed -> ConsoleFailed(state.failure, actions.onRetry, inner)
                is ServerSettingsUiState.Ready -> {
                    ServerSettingsBody(state, actions, splashscreenAvailable, inner) { picker = it }
                    picker?.let { ServerPickerSheet(it, state, actions) { picker = null } }
                    state.editing?.let { TextDialog(it, state, actions) }
                }
            }
        }
    }
}

@Composable
private fun ServerSettingsBody(
    state: ServerSettingsUiState.Ready,
    actions: ServerSettingsActions,
    splashscreenAvailable: Boolean,
    inner: PaddingValues,
    onPick: (ServerPicker) -> Unit,
) {
    val settings = state.settings
    val branding = state.branding
    val notSet = stringResource(R.string.server_settings_not_set)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        Text(stringResource(R.string.server_settings_body), style = MaterialTheme.typography.bodyMedium)
        ItemGroup(
            title = stringResource(R.string.server_settings_general),
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.Dns,
                        label = stringResource(R.string.server_settings_name),
                        detail = settings.name.ifBlank { notSet },
                        onClick = { actions.onEdit(ServerText.Name) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.Translate,
                        label = stringResource(R.string.server_settings_metadata_language),
                        detail =
                            state.cultures.firstOrNull { it.twoLetter.equals(settings.metadataLanguage, true) }?.displayName
                                ?: settings.metadataLanguage.ifBlank { notSet },
                        onClick = { onPick(ServerPicker.MetadataLanguage) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.Public,
                        label = stringResource(R.string.server_settings_metadata_country),
                        detail =
                            state.countries.firstOrNull { it.code.equals(settings.metadataCountry, true) }?.displayName
                                ?: settings.metadataCountry.ifBlank { notSet },
                        onClick = { onPick(ServerPicker.MetadataCountry) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.QrCode2,
                        label = stringResource(R.string.server_settings_quick_connect),
                        detail = stringResource(R.string.server_settings_quick_connect_detail),
                        toggled = settings.quickConnect,
                        onClick = { actions.onQuickConnect(!settings.quickConnect) },
                    ),
                ),
        )
        ItemGroup(
            title = stringResource(R.string.server_settings_branding),
            rows =
                listOfNotNull(
                    ListItem(
                        icon = Icons.Outlined.Gavel,
                        label = stringResource(R.string.server_settings_disclaimer),
                        detail = branding.loginDisclaimer.ifBlank { stringResource(R.string.server_settings_none) },
                        onClick = { actions.onEdit(ServerText.LoginDisclaimer) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.Code,
                        label = stringResource(R.string.server_settings_css),
                        detail =
                            branding.customCss.lines().count(String::isNotBlank).let { lines ->
                                if (lines ==
                                    0
                                ) {
                                    stringResource(R.string.server_settings_none)
                                } else {
                                    pluralStringResource(R.plurals.server_settings_css_lines, lines, lines)
                                }
                            },
                        onClick = { actions.onEdit(ServerText.CustomCss) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.Wallpaper,
                        label = stringResource(R.string.server_settings_splashscreen),
                        detail = stringResource(R.string.server_settings_splashscreen_detail),
                        toggled = branding.splashscreen,
                        onClick = { actions.onSplashscreen(!branding.splashscreen) },
                    ).takeIf { splashscreenAvailable },
                ),
        )
        Text(
            text = state.saveFailed?.let { stringResource(R.string.languages_not_saved, stringResource(it.message)) }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            minLines = SETTINGS_STATUS_LINES,
            maxLines = SETTINGS_STATUS_LINES,
        )
    }
}

@Composable
private fun ServerPickerSheet(
    picker: ServerPicker,
    state: ServerSettingsUiState.Ready,
    actions: ServerSettingsActions,
    onDismiss: () -> Unit,
) {
    when (picker) {
        ServerPicker.MetadataLanguage -> {
            val choices =
                remember(state.cultures) {
                    state.cultures
                        .filter { it.twoLetter.isNotBlank() }
                        .distinctBy { it.twoLetter.lowercase() }
                        .map { BingeChoice(it.twoLetter, it.displayName, mark = it.mark()) }
                }
            BingeChoiceSheet(
                title = stringResource(R.string.server_settings_metadata_language),
                choices = BingeChoiceList.Ready(choices),
                selected = choices.firstOrNull { it.value.equals(state.settings.metadataLanguage, true) }?.value,
                onSelect = actions.onMetadataLanguage,
                onDismissRequest = onDismiss,
            )
        }
        ServerPicker.MetadataCountry -> {
            val choices = remember(state.countries) { state.countries.map { BingeChoice(it.code, it.displayName, mark = it.code) } }
            BingeChoiceSheet(
                title = stringResource(R.string.server_settings_metadata_country),
                choices = BingeChoiceList.Ready(choices),
                selected = choices.firstOrNull { it.value.equals(state.settings.metadataCountry, true) }?.value,
                onSelect = actions.onMetadataCountry,
                onDismissRequest = onDismiss,
            )
        }
    }
}

/**
 * A text setting's dialog: Save keeps what's typed, Cancel leaves the setting as it was. [inWindow]
 * false draws its body for a frame.
 */
@Composable
internal fun TextDialog(
    text: ServerText,
    state: ServerSettingsUiState.Ready,
    actions: ServerSettingsActions,
    inWindow: Boolean = true,
) {
    val current =
        when (text) {
            ServerText.Name -> state.settings.name
            ServerText.LoginDisclaimer -> state.branding.loginDisclaimer
            ServerText.CustomCss -> state.branding.customCss
        }
    var value by rememberSaveable(text) { mutableStateOf(current) }
    val (title, body) =
        when (text) {
            ServerText.Name -> R.string.server_settings_name to R.string.server_settings_name_body
            ServerText.LoginDisclaimer -> R.string.server_settings_disclaimer to R.string.server_settings_disclaimer_body
            ServerText.CustomCss -> R.string.server_settings_css to R.string.server_settings_css_body
        }
    val single = text == ServerText.Name
    ConsoleConfirm(
        title = stringResource(title),
        message = stringResource(body),
        confirmLabel = stringResource(R.string.server_settings_save),
        onConfirm = { actions.onSaveText(text, value) },
        onDismissRequest = { actions.onEdit(null) },
        inWindow = inWindow,
        extraContent = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(title)) },
                singleLine = single,
                minLines = if (text == ServerText.CustomCss) CSS_LINES else 1,
                maxLines = if (single) 1 else CSS_LINES,
                keyboardOptions = KeyboardOptions(imeAction = if (single) ImeAction.Done else ImeAction.Default),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
