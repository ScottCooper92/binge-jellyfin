package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.template.FormAction
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.formSaveStatus
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.NETWORK_AUTO_DISCOVERY
import com.cooper.binge.companion.jellyfin.server.NETWORK_BASE_URL
import com.cooper.binge.companion.jellyfin.server.NETWORK_BIND_ADDRESSES
import com.cooper.binge.companion.jellyfin.server.NETWORK_CERTIFICATE_PASSWORD
import com.cooper.binge.companion.jellyfin.server.NETWORK_CERTIFICATE_PATH
import com.cooper.binge.companion.jellyfin.server.NETWORK_ENABLE_HTTPS
import com.cooper.binge.companion.jellyfin.server.NETWORK_IGNORE_VIRTUAL
import com.cooper.binge.companion.jellyfin.server.NETWORK_IP_FILTER_BLACKLIST
import com.cooper.binge.companion.jellyfin.server.NETWORK_PUBLISHED_BY_REQUEST
import com.cooper.binge.companion.jellyfin.server.NETWORK_REMOTE_ACCESS
import com.cooper.binge.companion.jellyfin.server.NETWORK_REMOTE_IP_FILTER
import com.cooper.binge.companion.jellyfin.server.NETWORK_REQUIRE_HTTPS
import com.cooper.binge.companion.jellyfin.server.NETWORK_UPNP
import com.binge.designsystem.R as DesR

/** What the networking page can ask of its view model, and of the console. */
data class NetworkActions(
    val onSheet: (NetworkSheet?) -> Unit = {},
    val onNumber: (NetworkNumber, String) -> Unit = { _, _ -> },
    val onText: (NetworkText, String) -> Unit = { _, _ -> },
    val onItems: (NetworkList, String) -> Unit = { _, _ -> },
    val onFormSwitch: (key: String, on: Boolean) -> Unit = { _, _ -> },
    val onSwitch: (key: String, on: Boolean) -> Unit = { _, _ -> },
    val onSave: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
    val onOpenServer: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetrySave: () -> Unit = {},
    val onBack: () -> Unit = {},
)

private const val LOADING_ROWS = 8

/** Lines a row's detail may wrap to, so it reads in full at large fonts. */
internal const val NETWORK_DETAIL_LINES = 5

/**
 * The server's network options. The ones that decide who can reach it (ports, addresses, HTTPS, remote access) are edited on the
 * page and written together by Save changes, which asks first when the change reaches the address this app is signed in to. The
 * rest save as they're made. After a save the page says the server needs a restart, and opens the Server page for it; it never
 * restarts the server itself.
 */
@Composable
fun NetworkScreen(
    state: NetworkUiState,
    actions: NetworkActions,
    appUrl: String = "",
    inWindow: Boolean = true,
) {
    val host = remember { SnackbarHostState() }
    val ready = state as? NetworkUiState.Ready
    FormScreen(
        title = stringResource(R.string.network_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        saveStatus = ready?.save?.let { formSaveStatus(it.status, it.failed, actions.onRetrySave) },
        primaryAction =
            ready?.let {
                FormAction(stringResource(R.string.network_save), actions.onSave, enabled = it.draft.isNotEmpty(), busy = it.committing)
            },
        notReady =
            when (state) {
                NetworkUiState.Loading -> { padding -> ConsoleLoading(padding, LOADING_ROWS) }
                is NetworkUiState.Failed -> { padding -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(padding)) }
                is NetworkUiState.Ready -> null
            },
    ) { _ ->
        if (ready != null) {
            NetworkBody(ready, actions)
            ready.sheet?.let { NetworkSheetFor(it, ready, actions, inWindow) }
            ready.confirming?.let { NetworkConfirm(it, appUrl, actions, inWindow) }
            RefusedMessage(ready.refused, host)
            RefusedMessage(ready.formFailed, host, R.string.network_not_saved)
        }
    }
}

@Composable
private fun NetworkBody(
    state: NetworkUiState.Ready,
    actions: NetworkActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        PageIntro(stringResource(R.string.network_body))
        listOf(
            R.string.network_group_address to addressRows(state, actions),
            R.string.network_group_secure to secureRows(state, actions),
            R.string.network_group_access to accessRows(state, actions),
            R.string.network_group_local to localRows(state, actions),
        ).forEach { (title, rows) -> if (rows.isNotEmpty()) ItemGroup(title = stringResource(title), rows = rows) }
        if (state.restartNeeded) {
            ItemGroup(
                title = null,
                rows =
                    listOf(
                        ListItem(
                            icon = Icons.Outlined.RestartAlt,
                            label = stringResource(R.string.network_restart),
                            detail =
                                stringResource(
                                    if (state.reconnect) R.string.network_restart_reconnect_detail else R.string.network_restart_detail,
                                ),
                            supportingMaxLines = NETWORK_DETAIL_LINES,
                            onClick = actions.onOpenServer,
                        ),
                    ),
            )
        }
    }
}

/** A row of the page's form: its value, and a note that it isn't saved yet while it waits for Save changes. */
@Composable
private fun NetworkUiState.Ready.formRow(
    key: String,
    icon: ImageVector,
    label: Int,
    detail: String,
    onClick: () -> Unit,
): ListItem? =
    ListItem(
        icon = icon,
        label = stringResource(label),
        detail = detail,
        supportingMaxLines = NETWORK_DETAIL_LINES,
        note = if (key in draft) ListItemNote(stringResource(R.string.network_unsaved), icon = Icons.Outlined.Info) else null,
        onClick = onClick,
    ).takeIf { settings.has(key) }

@Composable
private fun NetworkUiState.Ready.formSwitch(
    key: String,
    icon: ImageVector,
    label: Int,
    detail: Int?,
    on: Boolean,
    actions: NetworkActions,
): ListItem? =
    ListItem(
        icon = icon,
        label = stringResource(label),
        detail = detail?.let { stringResource(it) },
        supportingMaxLines = NETWORK_DETAIL_LINES,
        note = if (key in draft) ListItemNote(stringResource(R.string.network_unsaved), icon = Icons.Outlined.Info) else null,
        toggled = on,
        onClick = { actions.onFormSwitch(key, !on) },
    ).takeIf { settings.has(key) }

@Composable
private fun NetworkUiState.Ready.madeSwitch(
    key: String,
    icon: ImageVector,
    label: Int,
    on: Boolean,
    actions: NetworkActions,
): ListItem? =
    ListItem(
        icon = icon,
        label = stringResource(label),
        toggled = on,
        onClick = { actions.onSwitch(key, !on) },
    ).takeIf { settings.has(key) }

@Composable
private fun NetworkUiState.Ready.portRow(
    field: NetworkNumber,
    icon: ImageVector,
    label: Int,
    value: Int,
    actions: NetworkActions,
): ListItem? = formRow(field.key(keys), icon, label, value.toString()) { actions.onSheet(NetworkSheet.Number(field)) }

@Composable
private fun NetworkUiState.Ready.listRow(
    field: NetworkList,
    label: Int,
    value: List<String>,
    actions: NetworkActions,
): ListItem? = formRow(field.key, Icons.Outlined.Router, label, entries(value)) { actions.onSheet(NetworkSheet.Items(field)) }

@Composable
private fun entries(list: List<String>): String =
    if (list.isEmpty()) stringResource(R.string.network_none) else pluralStringResource(R.plurals.network_entries, list.size, list.size)

@Composable
private fun addressRows(
    state: NetworkUiState.Ready,
    actions: NetworkActions,
): List<ListItem> {
    val s = state.settings
    return listOfNotNull(
        state.portRow(NetworkNumber.HttpPort, Icons.Outlined.Dns, R.string.network_http_port, s.httpPort, actions),
        state.portRow(NetworkNumber.HttpsPort, Icons.Outlined.Lock, R.string.network_https_port, s.httpsPort, actions),
        state.portRow(NetworkNumber.PublicHttpPort, Icons.Outlined.Public, R.string.network_public_http_port, s.publicHttpPort, actions),
        state.portRow(NetworkNumber.PublicHttpsPort, Icons.Outlined.Public, R.string.network_public_https_port, s.publicHttpsPort, actions),
        state.formRow(
            NETWORK_BASE_URL,
            Icons.Outlined.Language,
            R.string.network_base_url,
            s.baseUrl.ifEmpty { stringResource(R.string.network_none) },
        ) { actions.onSheet(NetworkSheet.Text(NetworkText.BaseUrl)) },
        state.formRow(
            NETWORK_BIND_ADDRESSES,
            Icons.Outlined.Router,
            R.string.network_bind,
            if (s.bindAddresses.isEmpty()) stringResource(R.string.network_all_addresses) else s.bindAddresses.joinToString(", "),
        ) { actions.onSheet(NetworkSheet.Items(NetworkList.BindAddresses)) },
    )
}

@Composable
private fun secureRows(
    state: NetworkUiState.Ready,
    actions: NetworkActions,
): List<ListItem> {
    val s = state.settings
    return listOfNotNull(
        state.formSwitch(NETWORK_ENABLE_HTTPS, Icons.Outlined.Lock, R.string.network_enable_https, null, s.enableHttps, actions),
        state.formSwitch(
            NETWORK_REQUIRE_HTTPS,
            Icons.Outlined.Shield,
            R.string.network_require_https,
            R.string.network_require_https_detail,
            s.requireHttps,
            actions,
        ),
        state.formRow(
            NETWORK_CERTIFICATE_PATH,
            Icons.Outlined.Shield,
            R.string.network_cert_path,
            s.certificatePath.ifEmpty { stringResource(R.string.network_none) },
        ) { actions.onSheet(NetworkSheet.Text(NetworkText.CertificatePath)) },
        // Never the stored password: only whether a new one is waiting to be saved.
        state.formRow(
            NETWORK_CERTIFICATE_PASSWORD,
            Icons.Outlined.Key,
            R.string.network_cert_password,
            stringResource(
                if (NETWORK_CERTIFICATE_PASSWORD in
                    state.draft
                ) {
                    R.string.network_cert_password_new
                } else {
                    R.string.network_cert_password_kept
                },
            ),
        ) { actions.onSheet(NetworkSheet.Text(NetworkText.CertificatePassword)) },
    )
}

@Composable
private fun accessRows(
    state: NetworkUiState.Ready,
    actions: NetworkActions,
): List<ListItem> {
    val s = state.settings
    val k = state.keys
    return listOfNotNull(
        state.formSwitch(
            NETWORK_REMOTE_ACCESS,
            Icons.Outlined.Public,
            R.string.network_remote_access,
            R.string.network_remote_access_detail,
            s.remoteAccess,
            actions,
        ),
        state.formRow(NETWORK_REMOTE_IP_FILTER, Icons.Outlined.VpnLock, R.string.network_ip_filter, entries(s.remoteIpFilter)) {
            actions.onSheet(NetworkSheet.Items(NetworkList.RemoteIpFilter))
        },
        state.formSwitch(
            NETWORK_IP_FILTER_BLACKLIST,
            Icons.Outlined.VpnLock,
            R.string.network_ip_filter_blacklist,
            null,
            s.filterIsBlacklist,
            actions,
        ),
        state.formSwitch(k.ipv4, Icons.Outlined.Wifi, R.string.network_ipv4, null, s.ipv4, actions),
        state.formSwitch(k.ipv6, Icons.Outlined.Wifi, R.string.network_ipv6, null, s.ipv6, actions),
    )
}

@Composable
private fun localRows(
    state: NetworkUiState.Ready,
    actions: NetworkActions,
): List<ListItem> {
    val s = state.settings
    return listOfNotNull(
        state.listRow(NetworkList.KnownProxies, R.string.network_proxies, s.knownProxies, actions),
        state.listRow(NetworkList.LocalSubnets, R.string.network_subnets, s.localSubnets, actions),
        state.madeSwitch(NETWORK_AUTO_DISCOVERY, Icons.Outlined.Sync, R.string.network_discovery, s.autoDiscovery, actions),
        state.madeSwitch(NETWORK_UPNP, Icons.Outlined.Router, R.string.network_upnp, s.upnp, actions).takeIf { state.keys.upnp },
        state.madeSwitch(
            NETWORK_PUBLISHED_BY_REQUEST,
            Icons.Outlined.Language,
            R.string.network_published_request,
            s.publishedByRequest,
            actions,
        ),
        state.listRow(NetworkList.PublishedBySubnet, R.string.network_published_subnet, s.publishedBySubnet, actions),
        state.madeSwitch(NETWORK_IGNORE_VIRTUAL, Icons.Outlined.Wifi, R.string.network_ignore_virtual, s.ignoreVirtual, actions),
        state.listRow(NetworkList.VirtualNames, R.string.network_virtual_names, s.virtualNames, actions),
    )
}
