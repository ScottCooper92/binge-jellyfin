package com.cooper.homelab.ephyra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.ui.console.ConsoleFailureScreen
import com.cooper.homelab.ephyra.ui.console.ConsoleLoading
import com.cooper.homelab.ephyra.ui.console.ConsoleTextField

/** Which choice of the wizard's first step is open in its sheet. */
private enum class StartupPicker { UiLanguage, MetadataLanguage, MetadataCountry }

/**
 * The setup wizard of a server that has not finished its first-run setup. It loads as a skeleton, a failed read is an error screen
 * with Try again, and Back goes to the notice it was opened from.
 */
@Composable
internal fun StartupStep(
    state: UiState.Startup,
    actions: Actions,
    inner: PaddingValues,
) {
    val startup = actions.startup
    BackHandler(onBack = startup.onBack)
    when (val phase = state.phase) {
        StartupPhase.Loading ->
            ConsoleLoading(inner, rows = LOADING_ROWS, plainIntro = stringResource(R.string.startup_body), formInset = false)
        is StartupPhase.Failed -> ConsoleFailureScreen(phase.failure, startup.onRetry, Modifier.padding(inner))
        is StartupPhase.Configuring -> SetupColumn(inner) { ConfiguringStep(phase, startup) }
        is StartupPhase.Account -> SetupColumn(inner) { AccountStep(phase, startup) }
        is StartupPhase.Finish -> SetupColumn(inner) { FinishStep(phase, startup) }
    }
}

@Composable
private fun ConfiguringStep(
    step: StartupPhase.Configuring,
    startup: StartupActions,
) {
    var picker by rememberSaveable { mutableStateOf<StartupPicker?>(null) }
    val configuration = step.configuration
    Text(stringResource(R.string.startup_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.startup_body), style = MaterialTheme.typography.bodyMedium)
    // An older server may not send a name: it gets no field, and is sent back as it came.
    if (configuration.hasServerName) {
        ConsoleTextField(
            value = configuration.serverName,
            onValueChange = startup.onName,
            label = stringResource(R.string.server_settings_name),
            enabled = !step.busy,
        )
    }
    val languageName = step.uiLanguages.firstOrNull { it.value.equals(configuration.uiCulture, ignoreCase = true) }?.name
    val metadataName = step.cultures.firstOrNull { it.twoLetter.equals(configuration.metadataLanguage, ignoreCase = true) }?.displayName
    val countryName = step.countries.firstOrNull { it.code.equals(configuration.metadataCountry, ignoreCase = true) }?.displayName
    val none = stringResource(R.string.server_settings_none)
    ItemGroup(
        title = null,
        rows =
            listOf(
                ListItem(
                    icon = Icons.Outlined.Language,
                    label = stringResource(R.string.display_language),
                    detail = languageName ?: none,
                    enabled = !step.busy,
                    onClick = { picker = StartupPicker.UiLanguage },
                ),
                ListItem(
                    icon = Icons.Outlined.Translate,
                    label = stringResource(R.string.server_settings_metadata_language),
                    detail = metadataName ?: none,
                    enabled = !step.busy,
                    onClick = { picker = StartupPicker.MetadataLanguage },
                ),
                ListItem(
                    icon = Icons.Outlined.Public,
                    label = stringResource(R.string.server_settings_metadata_country),
                    detail = countryName ?: none,
                    enabled = !step.busy,
                    onClick = { picker = StartupPicker.MetadataCountry },
                ),
            ),
    )
    step.error?.let { Text(errorText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    BingeFilledButton(
        label = stringResource(R.string.startup_save),
        onClick = startup.onSave,
        loading = step.busy,
        modifier = Modifier.fillMaxWidth(),
    )
    StartupPickerSheet(picker, step, startup) { picker = null }
}

@Composable
private fun StartupPickerSheet(
    picker: StartupPicker?,
    step: StartupPhase.Configuring,
    startup: StartupActions,
    onDismiss: () -> Unit,
) {
    val configuration = step.configuration
    val filter = stringResource(R.string.startup_filter)
    val noLanguages = stringResource(R.string.startup_no_match_languages)
    val noCountries = stringResource(R.string.startup_no_match_countries)
    when (picker) {
        null -> Unit
        StartupPicker.UiLanguage ->
            BingeChoiceSheet(
                title = stringResource(R.string.display_language),
                choices = remember(step.uiLanguages) { BingeChoiceList.Ready(step.uiLanguages.map { BingeChoice(it.value, it.name) }) },
                selected = step.uiLanguages.firstOrNull { it.value.equals(configuration.uiCulture, true) }?.value,
                onSelect = startup.onUiLanguage,
                onDismissRequest = onDismiss,
                filterPlaceholder = filter,
                noMatchLabel = noLanguages,
            )
        StartupPicker.MetadataLanguage ->
            BingeChoiceSheet(
                title = stringResource(R.string.server_settings_metadata_language),
                choices =
                    remember(step.cultures) {
                        BingeChoiceList.Ready(
                            step.cultures
                                .filter { it.twoLetter.isNotBlank() }
                                .distinctBy { it.twoLetter.lowercase() }
                                .map { BingeChoice(it.twoLetter, it.displayName) },
                        )
                    },
                selected = step.cultures.firstOrNull { it.twoLetter.equals(configuration.metadataLanguage, true) }?.twoLetter,
                onSelect = startup.onMetadataLanguage,
                onDismissRequest = onDismiss,
                filterPlaceholder = filter,
                noMatchLabel = noLanguages,
            )
        StartupPicker.MetadataCountry ->
            BingeChoiceSheet(
                title = stringResource(R.string.server_settings_metadata_country),
                choices = remember(step.countries) { BingeChoiceList.Ready(step.countries.map { BingeChoice(it.code, it.displayName) }) },
                selected = step.countries.firstOrNull { it.code.equals(configuration.metadataCountry, true) }?.code,
                onSelect = startup.onMetadataCountry,
                onDismissRequest = onDismiss,
                filterPlaceholder = filter,
                noMatchLabel = noCountries,
            )
    }
}

/** The account step: who administers the server. The password is typed twice, and nothing is sent until the two agree. */
@Composable
private fun AccountStep(
    step: StartupPhase.Account,
    startup: StartupActions,
) {
    Text(stringResource(R.string.startup_account_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.startup_account_body), style = MaterialTheme.typography.bodyMedium)
    ConsoleTextField(
        value = step.name,
        onValueChange = startup.onAccountName,
        label = stringResource(R.string.setup_username),
        enabled = !step.busy,
        imeAction = ImeAction.Next,
    )
    ConsoleTextField(
        value = step.password,
        onValueChange = startup.onPassword,
        label = stringResource(R.string.setup_password),
        secret = true,
        enabled = !step.busy,
        imeAction = ImeAction.Next,
    )
    ConsoleTextField(
        value = step.confirmation,
        onValueChange = startup.onConfirmation,
        label = stringResource(R.string.startup_password_again),
        isError = step.mismatch,
        supporting = if (step.mismatch) stringResource(R.string.startup_password_mismatch) else null,
        secret = true,
        enabled = !step.busy,
        imeAction = ImeAction.Go,
        onDone = { if (step.canSave) startup.onSaveAccount() },
    )
    // The server's own words when it gave any ("Password must not be empty"), else the plain sentence.
    val failure = step.reason ?: step.error?.let { errorText(it) }
    failure?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    BingeFilledButton(
        label = stringResource(R.string.startup_account_save),
        onClick = startup.onSaveAccount,
        loading = step.busy,
        enabled = step.canSave || step.busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The last step: who may reach the server from outside the network. Finish setup completes the server and signs in. */
@Composable
private fun FinishStep(
    step: StartupPhase.Finish,
    startup: StartupActions,
) {
    Text(stringResource(R.string.startup_finish_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.startup_finish_body), style = MaterialTheme.typography.bodyMedium)
    ItemGroup(
        title = null,
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Outlined.Public,
                    label = stringResource(R.string.network_remote_access),
                    toggled = step.remoteAccess,
                    enabled = !step.busy,
                    onClick = { startup.onRemoteAccess(!step.remoteAccess) },
                ),
                ListItem(
                    icon = Icons.Outlined.Router,
                    label = stringResource(R.string.startup_port_mapping),
                    toggled = step.portMapping,
                    enabled = !step.busy,
                    onClick = { startup.onPortMapping(!step.portMapping) },
                ).takeIf { step.remoteAccess },
            ),
    )
    step.error?.let { Text(errorText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    BingeFilledButton(
        label = stringResource(R.string.startup_finish),
        onClick = startup.onFinish,
        loading = step.busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val LOADING_ROWS = 3
