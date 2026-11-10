package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeSheetHeader
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.formSaveStatus
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.Branding
import com.binge.designsystem.R as DesR

/** What the server settings page can ask of its view model, and of the console. */
data class ServerSettingsActions(
    val onEdit: (ServerText?) -> Unit = {},
    val onSaveText: (ServerText, String) -> Unit = { _, _ -> },
    val onMetadataLanguage: (String) -> Unit = {},
    val onMetadataCountry: (String) -> Unit = {},
    val onQuickConnect: (Boolean) -> Unit = {},
    val onSplashscreen: (Boolean) -> Unit = {},
    val onOpenSplash: (Boolean) -> Unit = {},
    val onChooseSplash: () -> Unit = {},
    val onAskRemoveSplash: (Boolean) -> Unit = {},
    val onRemoveSplash: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetrySave: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Lines a switch's detail may wrap to, so it reads in full at large fonts. */
private const val SETTINGS_DETAIL_LINES = 3

/** Lines the custom CSS field shows before it scrolls. */
private const val CSS_LINES = 8
private const val MULTILINE_MIN = 3

/**
 * The server's general settings (its name, the language and country metadata is fetched for, Quick
 * Connect) and the login branding. Each change saves as it's made; a text saves when its dialog does.
 * The splash screen switch shows only on a server that has one ([splashscreenAvailable], 10.9). Whether the image is the user's own is
 * read only where the server says ([splashImageKnown], 10.11). Before that, the image row is left out and a note says that saving here
 * can reset an image set in another app: those servers drop it on any branding save.
 */
@Composable
fun ServerSettingsScreen(
    state: ServerSettingsUiState,
    actions: ServerSettingsActions,
    splashscreenAvailable: Boolean = true,
    splashImageKnown: Boolean = true,
    splash: SplashUiState = SplashUiState(),
) {
    var picker by rememberSaveable { mutableStateOf<ServerPicker?>(null) }
    val host = remember { SnackbarHostState() }
    val ready = state as? ServerSettingsUiState.Ready
    FormScreen(
        title = stringResource(R.string.server_settings_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        saveStatus = ready?.save?.let { formSaveStatus(it.status, it.failed, actions.onRetrySave) },
        notReady =
            when (state) {
                ServerSettingsUiState.Loading -> { padding ->
                    ConsoleLoading(padding, LOADING_ROWS, intro = stringResource(R.string.server_settings_body))
                }
                is ServerSettingsUiState.Failed -> { padding ->
                    ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(padding))
                }
                is ServerSettingsUiState.Ready -> null
            },
    ) { _ ->
        if (ready != null) {
            ServerSettingsBody(ready, actions, splashscreenAvailable, splashImageKnown) { picker = it }
            // A refusal is said once, in a snackbar; a server that couldn't be reached is the save status's Retry.
            RefusedMessage(ready.refused, host)
            picker?.let { ServerPickerSheet(it, ready, actions) { picker = null } }
            ready.editing?.let { TextSheet(it, ready, actions) }
            if (splash.open && splashscreenAvailable && splashImageKnown) {
                SplashSheet(ready.branding.customSplashscreen, splash, actions)
            }
        }
    }
}

@Composable
private fun ServerSettingsBody(
    state: ServerSettingsUiState.Ready,
    actions: ServerSettingsActions,
    splashscreenAvailable: Boolean,
    splashImageKnown: Boolean,
    onPick: (ServerPicker) -> Unit,
) {
    val settings = state.settings
    val branding = state.branding
    val notSet = stringResource(R.string.server_settings_not_set)
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        PageIntro(stringResource(R.string.server_settings_body))
        ItemGroup(
            title = stringResource(R.string.server_settings_general),
            modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
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
                        supportingMaxLines = SETTINGS_DETAIL_LINES,
                        toggled = settings.quickConnect,
                        onClick = { actions.onQuickConnect(!settings.quickConnect) },
                    ),
                ),
        )
        LoginScreenGroup(branding, actions, splashscreenAvailable, splashImageKnown)
    }
}

/** The login screen's branding: the disclaimer, custom CSS, and, where the server has them (10.9), the splash screen and its image. */
@Composable
private fun LoginScreenGroup(
    branding: Branding,
    actions: ServerSettingsActions,
    splashscreenAvailable: Boolean,
    splashImageKnown: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        LoginScreenRows(branding, actions, splashscreenAvailable, splashImageKnown)
        // Before 10.11 the server saves the whole branding on every write and drops the splash image's location, so an image set in
        // another app is lost on any save here. The image row is left out, since the app can neither read nor put it back.
        if (splashscreenAvailable && !splashImageKnown) {
            Text(
                text = stringResource(R.string.server_settings_branding_reset_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_s)),
            )
        }
    }
}

@Composable
private fun LoginScreenRows(
    branding: Branding,
    actions: ServerSettingsActions,
    splashscreenAvailable: Boolean,
    splashImageKnown: Boolean,
) {
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
                    supportingMaxLines = SETTINGS_DETAIL_LINES,
                    toggled = branding.splashscreen,
                    onClick = { actions.onSplashscreen(!branding.splashscreen) },
                ).takeIf { splashscreenAvailable },
                ListItem(
                    icon = Icons.Outlined.Image,
                    label = stringResource(R.string.server_settings_splash_image),
                    detail =
                        if (branding.customSplashscreen) {
                            stringResource(R.string.server_settings_splash_custom)
                        } else {
                            stringResource(R.string.server_settings_splash_default)
                        },
                    onClick = { actions.onOpenSplash(true) },
                ).takeIf { splashscreenAvailable && splashImageKnown },
            ),
    )
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
            val locales = LocalConfiguration.current.locales
            val suggested =
                remember(choices, locales) {
                    val device =
                        (0 until locales.size()).mapNotNull { i ->
                            choices.firstOrNull { it.value.equals(locales[i].language, true) }?.value
                        }
                    val popular = POPULAR_LANGUAGES.mapNotNull { code -> choices.firstOrNull { it.value.equals(code, true) }?.value }
                    suggestions(device, popular, choices.map { it.value }.toSet())
                }
            BingeChoiceSheet(
                title = stringResource(R.string.server_settings_metadata_language),
                choices = BingeChoiceList.Ready(choices),
                selected = choices.firstOrNull { it.value.equals(state.settings.metadataLanguage, true) }?.value,
                onSelect = actions.onMetadataLanguage,
                onDismissRequest = onDismiss,
                suggested = suggested,
            )
        }
        ServerPicker.MetadataCountry -> {
            val choices = remember(state.countries) { state.countries.map { BingeChoice(it.code, it.displayName, mark = it.code) } }
            val locales = LocalConfiguration.current.locales
            val suggested =
                remember(choices, locales) {
                    val device =
                        (0 until locales.size()).mapNotNull { i ->
                            choices.firstOrNull { it.value.equals(locales[i].country, true) }?.value
                        }
                    val popular = POPULAR_REGIONS.mapNotNull { code -> choices.firstOrNull { it.value.equals(code, true) }?.value }
                    suggestions(device, popular, choices.map { it.value }.toSet())
                }
            BingeChoiceSheet(
                title = stringResource(R.string.server_settings_metadata_country),
                choices = BingeChoiceList.Ready(choices),
                selected = choices.firstOrNull { it.value.equals(state.settings.metadataCountry, true) }?.value,
                onSelect = actions.onMetadataCountry,
                onDismissRequest = onDismiss,
                suggested = suggested,
            )
        }
    }
}

/**
 * A text setting's sheet: Save keeps what's typed, Cancel leaves the setting as it was. [inWindow]
 * false draws the form for a frame.
 */
@Composable
internal fun TextSheet(
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
    val error = state.sheetFailed?.let { stringResource(R.string.settings_not_saved, it.text()) }
    val (title, body) =
        when (text) {
            ServerText.Name -> R.string.server_settings_name to R.string.server_settings_name_body
            ServerText.LoginDisclaimer -> R.string.server_settings_disclaimer to R.string.server_settings_disclaimer_body
            ServerText.CustomCss -> R.string.server_settings_css to R.string.server_settings_css_body
        }
    val single = text == ServerText.Name
    ConsoleSheet(onDismissRequest = { actions.onEdit(null) }, inWindow = inWindow) {
        ConsoleTextEntry(
            title = stringResource(title),
            body = stringResource(body),
            value = value,
            onValueChange = { value = it },
            submitLabel = stringResource(R.string.server_settings_save),
            onSubmit = { actions.onSaveText(text, value) },
            onCancel = { actions.onEdit(null) },
            isSubmitting = state.submitting == text,
            error = error,
            singleLine = single,
            minLines = if (text == ServerText.CustomCss) CSS_LINES else MULTILINE_MIN,
            maxLines = if (single) 1 else CSS_LINES,
        )
    }
}

private const val LOADING_ROWS = 8

/** The thumbnail's height in the splash image sheet. */
private val SPLASH_THUMB_HEIGHT = 180.dp

/**
 * The login splash image: what the server serves now, Choose an image (the system photo picker, so no storage permission), and,
 * only where a custom image is set ([custom]), Remove, last and red, behind a confirm. A sheet, since it offers a choice.
 * [inWindow] false draws its body for a frame.
 */
@Composable
internal fun SplashSheet(
    custom: Boolean,
    state: SplashUiState,
    actions: ServerSettingsActions,
    inWindow: Boolean = true,
) {
    ConsoleSheet(onDismissRequest = { actions.onOpenSplash(false) }, inWindow = inWindow) {
        Column(
            modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            BingeSheetHeader(
                title = stringResource(R.string.server_settings_splash_image),
                subtitle = stringResource(R.string.server_settings_splash_body),
            )
            SplashPreview(state)
            ItemGroup(
                title = null,
                rows =
                    listOf(
                        ListItem(
                            icon = Icons.Outlined.AddPhotoAlternate,
                            label = stringResource(R.string.server_settings_splash_choose),
                            destination = ListItemDestination.Action,
                            loading = state.busy,
                            onClick = actions.onChooseSplash,
                        ),
                    ),
            )
            val problem =
                when {
                    state.unreadable -> stringResource(R.string.profile_picture_unreadable)
                    state.refused != null -> stringResource(R.string.server_settings_splash_not_changed, state.refused.text())
                    else -> null
                }
            problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            if (custom) {
                BingeOutlinedButton(
                    label = stringResource(R.string.server_settings_splash_remove),
                    onClick = { actions.onAskRemoveSplash(true) },
                    destructive = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (state.confirmingRemove) RemoveSplashDialog(actions, inWindow)
}

/** The image the server serves, or a line saying there is none to show. */
@Composable
private fun SplashPreview(state: SplashUiState) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(SPLASH_THUMB_HEIGHT)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.picture != null ->
                Image(
                    bitmap = state.picture.asImageBitmap(),
                    contentDescription = stringResource(R.string.server_settings_splash_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            state.loading -> Unit
            else ->
                Text(
                    stringResource(R.string.server_settings_splash_no_preview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
                )
        }
    }
}

/** Removing the custom splash image, as a confirm that opens on Cancel. [inWindow] false draws its body for a frame. */
@Composable
internal fun RemoveSplashDialog(
    actions: ServerSettingsActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.server_settings_splash_remove_title),
        message = stringResource(R.string.server_settings_splash_remove_body),
        confirmLabel = stringResource(R.string.server_settings_splash_remove),
        onConfirm = actions.onRemoveSplash,
        onDismissRequest = { actions.onAskRemoveSplash(false) },
        destructive = true,
        inWindow = inWindow,
    )
}
