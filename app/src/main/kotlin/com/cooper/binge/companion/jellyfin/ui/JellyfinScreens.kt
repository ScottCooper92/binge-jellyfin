package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.binge.designsystem.R as DesR

/** What the screens can ask of the [JellyfinViewModel]. */
data class Actions(
    val onAddress: (String) -> Unit = {},
    val onConnect: () -> Unit = {},
    val onUsername: (String) -> Unit = {},
    val onPassword: (String) -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onQuickConnect: () -> Unit = {},
    val onCancelQuickConnect: () -> Unit = {},
    val onChangeServer: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onAskSignOut: () -> Unit = {},
    val onCancelSignOut: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

@Composable
fun JellyfinRoot(
    state: UiState,
    actions: Actions,
) {
    BingeScreenScaffold(title = stringResource(R.string.app_label), bar = ScreenBar.Small) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
                is UiState.EnterServer -> EnterServer(state, actions, inner)
                is UiState.SignIn -> SignIn(state, actions, inner)
                is UiState.SignedIn -> SignedIn(state, actions, inner)
            }
        }
    }
}

@Composable
private fun SetupColumn(
    inner: PaddingValues,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .imePadding(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        content()
    }
}

@Composable
private fun EnterServer(
    state: UiState.EnterServer,
    actions: Actions,
    inner: PaddingValues,
) {
    SetupColumn(inner) {
        Text(stringResource(R.string.setup_server_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.setup_server_body), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.address,
            onValueChange = actions.onAddress,
            label = { Text(stringResource(R.string.setup_server_field)) },
            placeholder = { Text("192.168.1.10:8096") },
            singleLine = true,
            isError = state.error != null,
            supportingText = state.error?.let { { Text(errorText(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { actions.onConnect() }),
            modifier = Modifier.fillMaxWidth(),
        )
        BingeFilledButton(
            label = stringResource(R.string.setup_connect),
            onClick = actions.onConnect,
            loading = state.checking,
            enabled = state.address.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SignIn(
    state: UiState.SignIn,
    actions: Actions,
    inner: PaddingValues,
) {
    SetupColumn(inner) {
        ItemGroup(
            title = null,
            rows =
                listOf(
                    ListItem(
                        icon = if (state.cleartext) Icons.Outlined.LockOpen else Icons.Outlined.Dns,
                        label = state.server.serverName.ifBlank { state.baseUrl },
                        detail =
                            if (state.cleartext) {
                                stringResource(R.string.server_detail_cleartext, state.baseUrl)
                            } else {
                                state.baseUrl
                            },
                        trailingContent = {
                            BingeTextButton(
                                label = stringResource(R.string.setup_change),
                                onClick = actions.onChangeServer,
                            )
                        },
                        clickable = false,
                    ),
                ),
        )
        val code = state.quickConnectCode
        if (code != null) {
            QuickConnectCode(code, actions)
            return@SetupColumn
        }
        Text(stringResource(R.string.setup_sign_in_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.username,
            onValueChange = actions.onUsername,
            label = { Text(stringResource(R.string.setup_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = actions.onPassword,
            label = { Text(stringResource(R.string.setup_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = state.error != null,
            supportingText = state.error?.let { { Text(errorText(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { actions.onSignIn() }),
            modifier = Modifier.fillMaxWidth(),
        )
        BingeFilledButton(
            label = stringResource(R.string.setup_sign_in),
            onClick = actions.onSignIn,
            loading = state.busy,
            enabled = state.username.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        BingeOutlinedButton(
            label = stringResource(R.string.setup_quick_connect),
            onClick = actions.onQuickConnect,
            leadingIcon = Icons.Outlined.QrCode2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuickConnectCode(
    code: String,
    actions: Actions,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(stringResource(R.string.quick_connect_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            code,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.quick_connect_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) { BingeLoadingIndicator() }
        BingeTextButton(label = stringResource(R.string.quick_connect_cancel), onClick = actions.onCancelQuickConnect)
    }
}

@Composable
private fun SignedIn(
    state: UiState.SignedIn,
    actions: Actions,
    inner: PaddingValues,
) {
    val session = state.session
    LazyColumn(
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        item {
            ItemGroup(
                title = stringResource(R.string.settings_server),
                rows =
                    listOf(
                        ListItem(
                            icon = if (state.cleartext) Icons.Outlined.LockOpen else Icons.Outlined.Dns,
                            label = session.serverName.ifBlank { session.baseUrl },
                            detail =
                                if (state.cleartext) {
                                    stringResource(R.string.server_detail_cleartext, session.baseUrl)
                                } else {
                                    stringResource(R.string.server_detail, session.baseUrl, session.serverVersion)
                                },
                            clickable = false,
                        ),
                        ListItem(
                            icon = Icons.Outlined.Person,
                            label = session.userName,
                            detail = stringResource(R.string.settings_signed_in_as),
                            clickable = false,
                        ),
                    ),
            )
        }
        item {
            ItemGroup(
                title = stringResource(R.string.settings_library),
                rows =
                    listOf(
                        ListItem(
                            icon = Icons.Outlined.VideoLibrary,
                            label = stringResource(R.string.settings_titles),
                            detail =
                                when {
                                    state.refreshFailed -> stringResource(R.string.settings_titles_failed)
                                    state.titles == null -> stringResource(R.string.settings_titles_checking)
                                    else -> null
                                },
                            trailingContent =
                                state.titles?.let { count -> { Text(count.toString(), style = MaterialTheme.typography.bodyLarge) } },
                            clickable = false,
                        ),
                        ListItem(
                            icon = Icons.Outlined.Refresh,
                            label = stringResource(R.string.settings_refresh),
                            trailingContent = if (state.refreshing) ({ BingeLoadingIndicator() }) else null,
                            onClick = actions.onRefresh,
                        ),
                    ),
            )
        }
        item {
            BingeOutlinedButton(
                label = stringResource(R.string.settings_sign_out),
                onClick = actions.onAskSignOut,
                leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (state.confirmingSignOut) {
        BingeConfirmDialog(
            title = stringResource(R.string.sign_out_title),
            message = stringResource(R.string.sign_out_body),
            confirmLabel = stringResource(R.string.settings_sign_out),
            onConfirm = actions.onSignOut,
            onDismissRequest = actions.onCancelSignOut,
            destructive = true,
        )
    }
}

@Composable
private fun errorText(error: SetupError): String =
    stringResource(
        when (error) {
            SetupError.InvalidAddress -> R.string.error_invalid_address
            SetupError.Unreachable -> R.string.error_unreachable
            SetupError.NotJellyfin -> R.string.error_not_jellyfin
            SetupError.WrongCredentials -> R.string.error_wrong_credentials
            SetupError.QuickConnectOff -> R.string.error_quick_connect_off
            SetupError.QuickConnectExpired -> R.string.error_quick_connect_expired
            SetupError.Generic -> R.string.error_generic
        },
    )
