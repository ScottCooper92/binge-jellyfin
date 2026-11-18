package com.cooper.homelab.ephyra.ui.console

import android.text.format.Formatter
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.server.ActivityEntry
import com.cooper.homelab.ephyra.server.ServerInfo
import com.cooper.homelab.ephyra.server.ServerPaths
import com.cooper.homelab.ephyra.server.ServerStorage
import com.cooper.homelab.ephyra.server.Severity
import com.cooper.homelab.ephyra.server.StorageFolder
import com.cooper.homelab.ephyra.server.StorageKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import com.binge.designsystem.R as DesR

/** What the server page can ask of its view model, and of the console. */
data class ServerActions(
    val onConfirm: (Boolean) -> Unit = {},
    val onRestart: () -> Unit = {},
    val onConfirmShutdown: (Boolean) -> Unit = {},
    val onShutdown: () -> Unit = {},
    val onDismissRestart: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The server itself: its name, version, address and system, marked when an update or a restart is
 * waiting. Restart and Shut down are last, red, and behind a confirm, and how each is going shows above
 * them. A server that can't restart itself says so instead.
 */
@Composable
fun ServerScreen(
    state: ServerUiState,
    actions: ServerActions,
) {
    // Held above the loading, failed and ready branches, so a reload does not drop where the user was.
    val scroll = rememberScrollState()
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = stringResource(R.string.server_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                ServerUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                is ServerUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is ServerUiState.Ready -> {
                    BingePullToRefresh(
                        isRefreshing = state.refreshing,
                        onRefresh = actions.onRefresh,
                        indicatorTopInset = padding.calculateTopPadding(),
                    ) { Server(state, actions, inner, scroll) }
                    RefusedMessage(state.refreshFailed, host, R.string.console_not_refreshed)
                    // A restart or shutdown the server didn't take is a short message; a restart that never came back is a dialog.
                    SnackbarMessage(state.refusal()?.let { stringResource(it) }, host)
                }
            }
        }
    }
    val ready = state as? ServerUiState.Ready
    if (ready?.confirming == true) RestartDialog(ready.info, actions)
    if (ready?.confirmingShutdown == true) ShutdownDialog(ready.info, actions)
    if (ready?.restart == Restart.NotBack) RestartNotBackDialog(actions)
}

/** A restart the server never came back from: what happened, and where to look. Not a snackbar: it is a cause and a next step. */
@Composable
internal fun RestartNotBackDialog(
    actions: ServerActions,
    inWindow: Boolean = true,
) {
    ConsoleMessage(
        title = stringResource(R.string.server_not_back_title),
        message = stringResource(R.string.server_not_back_body),
        onDismiss = actions.onDismissRestart,
        inWindow = inWindow,
    )
}

/** Shutting the server down, after its name, as a confirm. [inWindow] false draws its body for a frame. */
@Composable
internal fun ShutdownDialog(
    info: ServerInfo,
    actions: ServerActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.server_shutdown_title, info.name),
        message = stringResource(R.string.server_shutdown_body),
        confirmLabel = stringResource(R.string.server_shutdown),
        onConfirm = actions.onShutdown,
        onDismissRequest = { actions.onConfirmShutdown(false) },
        destructive = true,
        inWindow = inWindow,
    )
}

/** Restarting the server, after its name, as a confirm. [inWindow] false draws its body for a frame. */
@Composable
internal fun RestartDialog(
    info: ServerInfo,
    actions: ServerActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.server_restart_title, info.name),
        message = stringResource(R.string.server_restart_body),
        confirmLabel = stringResource(R.string.server_restart),
        onConfirm = actions.onRestart,
        onDismissRequest = { actions.onConfirm(false) },
        destructive = true,
        inWindow = inWindow,
    )
}

/** The message of a restart or shutdown the server didn't take; null when none did. */
private fun ServerUiState.Ready.refusal(): Int? =
    when {
        shutdown == Shutdown.Failed -> shutdown.message
        restart == Restart.Failed -> restart.message
        else -> null
    }

@Composable
private fun Server(
    state: ServerUiState.Ready,
    actions: ServerActions,
    inner: PaddingValues,
    scroll: ScrollState,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        ItemGroup(
            title = null,
            rows = state.info.rows(),
            modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
        )
        state.alerts?.let { AlertsGroup(it) }
        state.storage?.let { StorageGroups(it) }
        PathsGroup(state.info.paths)
        // Where a restart or shutdown has got to, while it is going on or done. A failure is a message instead.
        if (state.refusal() == null) {
            (state.shutdown?.message ?: state.restart?.takeIf { it != Restart.NotBack }?.message)?.let {
                Text(text = stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (state.info.canRestart) {
            BingeTextButton(
                label = stringResource(R.string.server_restart),
                onClick = { actions.onConfirm(true) },
                leadingIcon = Icons.Outlined.RestartAlt,
                loading = state.restart == Restart.Restarting,
                enabled = !state.down,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(stringResource(R.string.server_cant_restart), style = MaterialTheme.typography.bodyMedium)
        }
        BingeTextButton(
            label = stringResource(R.string.server_shutdown),
            onClick = { actions.onConfirmShutdown(true) },
            leadingIcon = Icons.Outlined.PowerSettingsNew,
            loading = state.shutdown == Shutdown.ShuttingDown,
            enabled = !state.down && state.restart != Restart.Restarting,
            destructive = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ServerInfo.rows(): List<ListItem> =
    listOfNotNull(
        ListItem(
            icon = Icons.Outlined.Dns,
            label = name.ifBlank { stringResource(R.string.server_unnamed) },
            detail = address,
            clickable = false,
        ),
        ListItem(
            icon = if (updateAvailable) Icons.Outlined.NewReleases else Icons.Outlined.Info,
            label = stringResource(R.string.server_version, version),
            note = if (updateAvailable) ListItemNote(stringResource(R.string.server_update)) else null,
            clickable = false,
        ),
        ListItem(
            icon = Icons.Outlined.Memory,
            label =
                listOf(operatingSystem, architecture)
                    .filter {
                        it.isNotBlank()
                    }.joinToString(" · ")
                    .ifBlank { stringResource(R.string.server_system_unknown) },
            clickable = false,
        ),
        if (restartPending) {
            ListItem(
                icon = Icons.Outlined.RestartAlt,
                label = stringResource(R.string.server_restart_pending),
                detail = stringResource(R.string.server_restart_pending_detail),
                clickable = false,
            )
        } else {
            null
        },
    )

private val Shutdown.message: Int
    get() =
        when (this) {
            Shutdown.ShuttingDown -> R.string.server_shutting_down
            Shutdown.ShutDown -> R.string.server_shut_down
            Shutdown.Failed -> R.string.server_shutdown_failed
        }

private val Restart.message: Int
    get() =
        when (this) {
            Restart.Restarting -> R.string.server_restarting
            Restart.Restarted -> R.string.server_restarted
            Restart.NotBack -> R.string.server_not_back_title
            Restart.Failed -> R.string.server_restart_failed
        }

private const val LOADING_ROWS = 5

/** The newest warnings and errors of the activity log, as the web dashboard's Alerts; read-only, and one calm row when there are none. */
@Composable
internal fun AlertsGroup(
    alerts: List<ActivityEntry>,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val time =
        DateTimeFormatter
            .ofLocalizedDateTime(
                FormatStyle.SHORT,
            ).withLocale(LocalConfiguration.current.locales[0] ?: Locale.ROOT)
            .withZone(zone)
    ItemGroup(
        title = stringResource(R.string.server_alerts),
        rows =
            if (alerts.isEmpty()) {
                listOf(ListItem(icon = Icons.Outlined.CheckCircle, label = stringResource(R.string.server_alerts_none), clickable = false))
            } else {
                alerts.map { entry ->
                    ListItem(
                        icon = if (entry.severity == Severity.Error) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber,
                        iconTint = entry.severity.tint(),
                        label = entry.name,
                        detail =
                            listOfNotNull(entry.dateMillis?.let { time.format(Instant.ofEpochMilli(it)) }, entry.overview)
                                .joinToString(" · ")
                                .ifEmpty { null },
                        supportingMaxLines = DETAIL_LINES,
                        clickable = false,
                    )
                }
            },
    )
}

/** A folder is nearly out of space from this fraction used, and its bar and a note say so. */
private const val NEARLY_FULL = 0.9f

private val BAR_WIDTH = 56.dp

/** The server's own folders, then a group for each library: the space used of the disk each is on. */
@Composable
private fun StorageGroups(storage: ServerStorage) {
    if (storage.system.isNotEmpty()) {
        ItemGroup(
            title = stringResource(R.string.server_storage),
            rows = storage.system.map { (kind, folder) -> folder.row(Icons.Outlined.Folder, stringResource(kind.label())) },
        )
    }
    storage.libraries.filter { it.folders.isNotEmpty() }.forEach { library ->
        ItemGroup(
            title = library.name,
            rows = library.folders.map { folder -> folder.row(Icons.Outlined.Storage, folder.path) },
        )
    }
}

/** A folder's row: its name, the space used of the disk and the file system, and a bar that is red when the disk is nearly full. */
@Composable
private fun StorageFolder.row(
    icon: ImageVector,
    label: String,
): ListItem {
    val context = LocalContext.current
    val fraction = usedFraction
    val full = fraction != null && fraction >= NEARLY_FULL
    val space =
        if (fraction == null) {
            path.takeIf { it != label }
        } else {
            stringResource(
                R.string.server_storage_used,
                Formatter.formatFileSize(context, usedBytes),
                Formatter.formatFileSize(context, totalBytes),
                (fraction * PERCENT).roundToInt(),
            )
        }
    return ListItem(
        icon = icon,
        label = label,
        detail = listOfNotNull(space, type?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null },
        supportingMaxLines = DETAIL_LINES,
        note = if (full) ListItemNote(stringResource(R.string.server_storage_full), warning = true) else null,
        clickable = false,
        trailingContent =
            fraction?.let {
                {
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier.width(BAR_WIDTH),
                        color = if (full) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            },
    )
}

private const val PERCENT = 100
private const val DETAIL_LINES = 3

@Composable
private fun StorageKind.label(): Int =
    when (this) {
        StorageKind.ProgramData -> R.string.server_path_program_data
        StorageKind.Web -> R.string.server_path_web
        StorageKind.ImageCache -> R.string.server_path_image_cache
        StorageKind.Cache -> R.string.server_path_cache
        StorageKind.Logs -> R.string.server_path_logs
        StorageKind.Metadata -> R.string.server_path_metadata
        StorageKind.Transcodes -> R.string.server_path_transcodes
    }

/** Where the server keeps its files, read-only. A path the server did not send leaves its row out, and none at all leaves the group out. */
@Composable
private fun PathsGroup(paths: ServerPaths) {
    val rows =
        listOf(
            R.string.server_path_program_data to paths.programData,
            R.string.server_path_cache to paths.cache,
            R.string.server_path_items_by_name to paths.itemsByName,
            R.string.server_path_metadata to paths.metadata,
            R.string.server_path_transcodes to paths.transcodes,
            R.string.server_path_logs to paths.logs,
            R.string.server_path_web to paths.web,
        ).filter { (_, path) -> path.isNotBlank() }
    if (rows.isEmpty()) return
    ItemGroup(
        title = stringResource(R.string.server_paths),
        rows =
            rows.map { (label, path) ->
                ListItem(
                    icon = Icons.Outlined.Folder,
                    label = stringResource(label),
                    detail = path,
                    supportingMaxLines = DETAIL_LINES,
                    clickable = false,
                )
            },
    )
}
