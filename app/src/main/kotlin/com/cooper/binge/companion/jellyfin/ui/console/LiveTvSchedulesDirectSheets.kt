package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.SdCountry
import com.cooper.binge.companion.jellyfin.server.SdLineup
import com.binge.designsystem.R as DesR

/** What the Schedules Direct setup can ask of its view model. */
data class SdActions(
    val onSignIn: (username: String, password: String) -> Unit = { _, _ -> },
    val onChooseCountry: (Boolean) -> Unit = {},
    val onPickCountry: (SdCountry) -> Unit = {},
    val onPostalCode: (String) -> Unit = {},
    val onFind: (postalCode: String) -> Unit = {},
    val onBackToLocation: () -> Unit = {},
    val onBackToChoice: () -> Unit = {},
    val onAddLineup: (SdLineup) -> Unit = {},
    val onClose: () -> Unit = {},
    val onDismissProblem: () -> Unit = {},
)

/** The tallest a country or lineup list grows before it scrolls inside the sheet. */
private val LIST_MAX_HEIGHT = 360.dp
private const val LINEUP_LOADING_ROWS = 4

/** The Schedules Direct setup over the Live TV page, in a window. */
@Composable
internal fun SdSheet(
    state: SdUiState,
    actions: SdActions,
) {
    val step = state.step ?: return
    ConsoleSheet(onDismissRequest = actions.onClose) {
        SdStepBack(step, state.saving, actions)
        SdStepContent(step, state.saving, actions)
    }
}

/**
 * System Back steps back through the setup rather than closing the sheet: the account returns to the choice it was opened from,
 * the country list to the location, and the lineups to the location. The location itself has nothing before it (the account is
 * already saved, and finishing it later is on the guide's row), so Back closes the sheet there; so it does while a call is in flight.
 * While the keyboard is up the sheet's own handler closes it first.
 */
@Composable
private fun SdStepBack(
    step: SdStep,
    saving: Boolean,
    actions: SdActions,
) {
    StepBack(
        when {
            saving -> null
            step is SdStep.Account -> actions.onBackToChoice
            step is SdStep.Location && step.choosing -> ({ actions.onChooseCountry(false) })
            step is SdStep.Lineups -> actions.onBackToLocation
            else -> null
        },
    )
}

/** [step]'s body, which draws without a window for a frame. */
@Composable
internal fun SdStepContent(
    step: SdStep,
    saving: Boolean,
    actions: SdActions,
) {
    when (step) {
        SdStep.Account -> AccountStep(saving, actions)
        is SdStep.Location -> if (step.choosing) CountryList(step, actions) else LocationStep(step, actions)
        is SdStep.Lineups -> LineupsStep(step, saving, actions)
    }
}

@Composable
private fun AccountStep(
    saving: Boolean,
    actions: SdActions,
) {
    var username by rememberSaveable { mutableStateOf("") }
    // Not saveable: the password is held in this field while the sheet is open, and in no state that outlives it.
    var password by remember { mutableStateOf("") }
    ConsoleFieldsForm(
        title = stringResource(R.string.live_tv_type_schedules_direct),
        body = stringResource(R.string.live_tv_sd_account_body),
        submitLabel = stringResource(R.string.live_tv_sd_next),
        onSubmit = { actions.onSignIn(username, password) },
        onCancel = actions.onClose,
        submitting = saving,
    ) {
        ConsoleTextField(
            value = username,
            onValueChange = { username = it },
            label = stringResource(R.string.live_tv_sd_username),
            enabled = !saving,
            imeAction = ImeAction.Next,
        )
        ConsoleTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.live_tv_sd_password),
            secret = true,
            enabled = !saving,
        )
    }
}

@Composable
private fun LocationStep(
    step: SdStep.Location,
    actions: SdActions,
) {
    ConsoleFieldsForm(
        title = stringResource(R.string.live_tv_sd_location_title),
        body = stringResource(R.string.live_tv_sd_location_body),
        submitLabel = stringResource(R.string.live_tv_sd_find),
        onSubmit = { if (step.country != null) actions.onFind(step.postalCode) },
        onCancel = actions.onClose,
        submitEnabled = step.country != null && step.postalCode.isNotBlank(),
    ) {
        ItemGroup(
            title = null,
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.Language,
                        label = stringResource(R.string.live_tv_sd_country),
                        detail =
                            when {
                                step.country != null -> step.country.name
                                step.countries == null -> stringResource(R.string.live_tv_sd_countries_loading)
                                step.countries.isEmpty() -> stringResource(R.string.live_tv_sd_no_countries)
                                else -> stringResource(R.string.live_tv_sd_choose_country)
                            },
                        enabled = !step.countries.isNullOrEmpty(),
                        onClick = { actions.onChooseCountry(true) },
                    ),
                ),
        )
        ConsoleTextField(
            value = step.postalCode,
            onValueChange = actions.onPostalCode,
            label = stringResource(R.string.live_tv_sd_postal_code),
            supporting = stringResource(R.string.live_tv_sd_postal_code_detail),
        )
    }
}

@Composable
private fun CountryList(
    step: SdStep.Location,
    actions: SdActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Text(
            text = stringResource(R.string.live_tv_sd_country),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
        )
        Column(Modifier.heightIn(max = LIST_MAX_HEIGHT).verticalScroll(rememberScrollState())) {
            ConsoleSheetGroup(
                title = null,
                rows =
                    step.countries.orEmpty().map { country ->
                        ListItem(
                            icon = Icons.Outlined.Language,
                            label = country.name,
                            detail = country.region,
                            selected = country == step.country,
                            onClick = { actions.onPickCountry(country) },
                        )
                    },
            )
        }
        BingeOutlinedButton(
            label = stringResource(R.string.library_back),
            onClick = { actions.onChooseCountry(false) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        )
    }
}

@Composable
private fun LineupsStep(
    step: SdStep.Lineups,
    saving: Boolean,
    actions: SdActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Column(Modifier.padding(dimensionResource(DesR.dimen.padding_m))) {
            Text(text = stringResource(R.string.live_tv_sd_lineups_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.live_tv_sd_lineups_body, step.country.name, step.postalCode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            step.lineups == null ->
                ConsoleLoading(PaddingValues(), LINEUP_LOADING_ROWS, Modifier.heightIn(max = LIST_MAX_HEIGHT))
            saving -> BingeLoadingIndicator(Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)))
            step.lineups.isEmpty() ->
                Text(
                    text = stringResource(R.string.live_tv_sd_no_lineups),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
                )
            else ->
                Column(Modifier.heightIn(max = LIST_MAX_HEIGHT).verticalScroll(rememberScrollState())) {
                    ConsoleSheetGroup(
                        title = null,
                        rows =
                            step.lineups.map { lineup ->
                                ListItem(
                                    icon = Icons.Outlined.Subscriptions,
                                    label = lineup.name,
                                    supportingMaxLines = 2,
                                    onClick = { actions.onAddLineup(lineup) },
                                )
                            },
                    )
                }
        }
        BingeOutlinedButton(
            label = stringResource(R.string.library_back),
            onClick = actions.onBackToLocation,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        )
    }
}

/** What a Schedules Direct problem says: a dialog for the server's own refusal, with its cause and next step, and a snackbar for a call that never got there. */
@Composable
internal fun SdMessages(
    state: SdUiState,
    actions: SdActions,
    host: SnackbarHostState,
) {
    RefusedMessage(state.failed, host, R.string.live_tv_sd_not_done)
    val (title, body) =
        when (state.problem) {
            SdProblem.AccountNotSaved -> R.string.live_tv_sd_account_not_saved_title to R.string.live_tv_sd_account_not_saved_body
            SdProblem.LineupNotAdded -> R.string.live_tv_sd_lineup_not_added_title to R.string.live_tv_sd_lineup_not_added_body
            null -> return
        }
    ConsoleMessage(title = stringResource(title), message = stringResource(body), onDismiss = actions.onDismissProblem)
}
