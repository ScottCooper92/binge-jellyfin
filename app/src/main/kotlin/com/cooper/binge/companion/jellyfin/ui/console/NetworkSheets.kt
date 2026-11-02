package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.TextEntrySurface
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.NetworkImpact
import com.cooper.binge.companion.jellyfin.server.NetworkSettings

/** The sheet [sheet] names, or the confirm: each is drawn without its window when [inWindow] is false, for a frame. */
@Composable
internal fun NetworkSheetFor(
    sheet: NetworkSheet,
    state: NetworkUiState.Ready,
    actions: NetworkActions,
    inWindow: Boolean,
) {
    when (sheet) {
        is NetworkSheet.Number -> NetworkNumberSheet(sheet.field, state, actions, inWindow)
        is NetworkSheet.Text ->
            if (sheet.field == NetworkText.CertificatePassword) {
                NetworkPasswordSheet(actions, inWindow)
            } else {
                NetworkTextSheet(sheet.field, state.settings, actions, inWindow)
            }
        is NetworkSheet.Items -> NetworkItemsSheet(sheet.field, state.settings, actions, inWindow)
    }
}

private fun NetworkNumber.current(settings: NetworkSettings): Int =
    when (this) {
        NetworkNumber.HttpPort -> settings.httpPort
        NetworkNumber.HttpsPort -> settings.httpsPort
        NetworkNumber.PublicHttpPort -> settings.publicHttpPort
        NetworkNumber.PublicHttpsPort -> settings.publicHttpsPort
    }

private fun NetworkNumber.label(): Int =
    when (this) {
        NetworkNumber.HttpPort -> R.string.network_http_port
        NetworkNumber.HttpsPort -> R.string.network_https_port
        NetworkNumber.PublicHttpPort -> R.string.network_public_http_port
        NetworkNumber.PublicHttpsPort -> R.string.network_public_https_port
    }

/** A port's sheet: Keep holds it until Save changes, and says so if it isn't a port. */
@Composable
internal fun NetworkNumberSheet(
    field: NetworkNumber,
    state: NetworkUiState.Ready,
    actions: NetworkActions,
    inWindow: Boolean = true,
) {
    var value by rememberSaveable(field) { mutableStateOf(field.current(state.settings).toString()) }
    var tried by rememberSaveable(field) { mutableStateOf(false) }
    val valid = field.parse(value) != null
    ConsoleSheet(onDismissRequest = { actions.onSheet(null) }, inWindow = inWindow) {
        ConsoleTextEntry(
            title = stringResource(field.label()),
            body = stringResource(R.string.network_port_body),
            value = value,
            onValueChange = { value = it },
            submitLabel = stringResource(R.string.network_keep),
            onSubmit = {
                tried = true
                if (valid) actions.onNumber(field, value)
            },
            onCancel = { actions.onSheet(null) },
            error = if (tried && !valid) stringResource(R.string.network_port_invalid) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

/** The base URL's or the certificate file's sheet. */
@Composable
internal fun NetworkTextSheet(
    field: NetworkText,
    settings: NetworkSettings,
    actions: NetworkActions,
    inWindow: Boolean = true,
) {
    val current = if (field == NetworkText.BaseUrl) settings.baseUrl else settings.certificatePath
    var value by rememberSaveable(field) { mutableStateOf(current) }
    val (title, body) =
        if (field == NetworkText.BaseUrl) {
            R.string.network_base_url to R.string.network_base_url_body
        } else {
            R.string.network_cert_path to R.string.network_cert_path_body
        }
    ConsoleSheet(onDismissRequest = { actions.onSheet(null) }, inWindow = inWindow) {
        ConsoleTextEntry(
            title = stringResource(title),
            body = stringResource(body),
            value = value,
            onValueChange = { value = it },
            submitLabel = stringResource(R.string.network_keep),
            onSubmit = { actions.onText(field, value) },
            onCancel = { actions.onSheet(null) },
        )
    }
}

/**
 * A new certificate password, on the design system's secret entry. It starts empty and is not remembered across a restart of the
 * app: nothing about the stored password is ever shown, and the page keeps what is typed only until Save changes.
 */
@Composable
internal fun NetworkPasswordSheet(
    actions: NetworkActions,
    inWindow: Boolean = true,
) {
    var value by remember { mutableStateOf("") }
    ConsoleSheet(onDismissRequest = { actions.onSheet(null) }, inWindow = inWindow) {
        TextEntrySurface(
            title = stringResource(R.string.network_cert_password),
            supportingText = stringResource(R.string.network_cert_password_body),
            value = value,
            onValueChange = { value = it },
            onSubmit = { actions.onText(NetworkText.CertificatePassword, value) },
            onCancel = { actions.onSheet(null) },
            submitLabel = stringResource(R.string.network_keep),
            hint = stringResource(R.string.network_cert_password_hint),
            submitEnabled = true,
            singleLine = true,
            secret = true,
        )
    }
}

private fun NetworkList.current(settings: NetworkSettings): List<String> =
    when (this) {
        NetworkList.BindAddresses -> settings.bindAddresses
        NetworkList.RemoteIpFilter -> settings.remoteIpFilter
        NetworkList.KnownProxies -> settings.knownProxies
        NetworkList.LocalSubnets -> settings.localSubnets
        NetworkList.PublishedBySubnet -> settings.publishedBySubnet
        NetworkList.VirtualNames -> settings.virtualNames
    }

private fun NetworkList.title(): Int =
    when (this) {
        NetworkList.BindAddresses -> R.string.network_bind
        NetworkList.RemoteIpFilter -> R.string.network_ip_filter
        NetworkList.KnownProxies -> R.string.network_proxies
        NetworkList.LocalSubnets -> R.string.network_subnets
        NetworkList.PublishedBySubnet -> R.string.network_published_subnet
        NetworkList.VirtualNames -> R.string.network_virtual_names
    }

private fun NetworkList.body(): Int =
    when (this) {
        NetworkList.BindAddresses -> R.string.network_bind_body
        NetworkList.RemoteIpFilter -> R.string.network_ip_filter_body
        NetworkList.KnownProxies -> R.string.network_proxies_body
        NetworkList.LocalSubnets -> R.string.network_subnets_body
        NetworkList.PublishedBySubnet -> R.string.network_published_subnet_body
        NetworkList.VirtualNames -> R.string.network_virtual_names_body
    }

/** A list's sheet, one entry per line. A list in the page's form is held until Save changes; the others save at once. */
@Composable
internal fun NetworkItemsSheet(
    field: NetworkList,
    settings: NetworkSettings,
    actions: NetworkActions,
    inWindow: Boolean = true,
) {
    var value by rememberSaveable(field) { mutableStateOf(field.current(settings).joinToString("\n")) }
    ConsoleSheet(onDismissRequest = { actions.onSheet(null) }, inWindow = inWindow) {
        ConsoleTextEntry(
            title = stringResource(field.title()),
            body = stringResource(field.body()),
            value = value,
            onValueChange = { value = it },
            submitLabel = stringResource(if (field.form) R.string.network_keep else R.string.network_done),
            onSubmit = { actions.onItems(field, value) },
            onCancel = { actions.onSheet(null) },
            singleLine = false,
            minLines = LIST_LINES_MIN,
            maxLines = LIST_LINES_MAX,
        )
    }
}

private const val LIST_LINES_MIN = 3
private const val LIST_LINES_MAX = 8

private fun NetworkImpact.sentence(): Int =
    when (this) {
        NetworkImpact.Port -> R.string.network_impact_port
        NetworkImpact.HttpsOff -> R.string.network_impact_https_off
        NetworkImpact.RequireHttps -> R.string.network_impact_require_https
        NetworkImpact.BaseUrl -> R.string.network_impact_base_url
        NetworkImpact.BindAddress -> R.string.network_impact_bind
        NetworkImpact.Certificate -> R.string.network_impact_certificate
        NetworkImpact.RemoteAccess -> R.string.network_impact_remote
        NetworkImpact.IpFilter -> R.string.network_impact_filter
        NetworkImpact.Ip -> R.string.network_impact_ip
    }

/**
 * Saving changes that reach the address this app is signed in to ([appUrl]) asks first, naming how. It opens on Cancel, and says
 * when the change can leave every client, this app included, unable to connect until it's undone on the server itself.
 */
@Composable
internal fun NetworkConfirm(
    impacts: Set<NetworkImpact>,
    appUrl: String,
    actions: NetworkActions,
    inWindow: Boolean = true,
) {
    val severe = impacts.any { it.severe }
    val reasons = impacts.map { "• " + stringResource(it.sentence()) }.joinToString("\n")
    val lead = stringResource(if (severe) R.string.network_confirm_severe else R.string.network_confirm_soft)
    ConsoleConfirm(
        title = stringResource(R.string.network_confirm_title),
        message = listOf(lead, reasons, stringResource(R.string.network_confirm_address, appUrl)).joinToString("\n\n"),
        confirmLabel = stringResource(R.string.network_save),
        onConfirm = actions.onConfirm,
        onDismissRequest = actions.onDismissConfirm,
        destructive = true,
        inWindow = inWindow,
    )
}
