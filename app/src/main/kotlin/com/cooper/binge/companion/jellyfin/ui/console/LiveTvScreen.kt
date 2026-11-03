package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.GuideProviderSummary
import com.cooper.binge.companion.jellyfin.server.LiveTvSummary
import com.cooper.binge.companion.jellyfin.server.TunerHostSummary
import com.binge.designsystem.R as DesR

/** What the Live TV page can ask of the console. [onOpenTasks] is null where the user cannot open the Scheduled tasks page. */
data class LiveTvActions(
    val onOpenTasks: (() -> Unit)? = null,
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The server's Live TV setup: its tuners, its guide data, when the guide ends, and a way to Scheduled tasks, where Refresh Guide is.
 * A guide provider's row opens its form, and Add guide data adds an XMLTV one. A web address shows as its host only, since a playlist's address often carries the
 * provider's account in it.
 */
@Composable
fun LiveTvScreen(
    state: LiveTvUiState,
    actions: LiveTvActions,
    now: Long = System.currentTimeMillis(),
    guide: GuideUiState = GuideUiState(),
    guideActions: LiveTvGuideActions = LiveTvGuideActions(),
    sd: SdUiState = SdUiState(),
    sdActions: SdActions = SdActions(),
) {
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = stringResource(R.string.live_tv_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                LiveTvUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                is LiveTvUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is LiveTvUiState.Ready -> {
                    LiveTvContent(state.summary, actions, guideActions, inner, now)
                    GuideSheet(guide, guideActions)
                    GuideMessages(guide, guideActions, host)
                    SdSheet(sd, sdActions)
                    SdMessages(sd, sdActions)
                }
            }
        }
    }
}

@Composable
private fun LiveTvContent(
    summary: LiveTvSummary,
    actions: LiveTvActions,
    guideActions: LiveTvGuideActions,
    inner: PaddingValues,
    now: Long,
) {
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        item(key = "intro") { PageIntro(stringResource(R.string.live_tv_body)) }
        item(key = "tuners") {
            ItemGroup(
                title = stringResource(R.string.live_tv_tuners),
                rows = summary.tuners.map { it.row() }.ifEmpty { listOf(emptyRow(R.string.live_tv_no_tuners)) },
            )
        }
        item(key = "guide") {
            ItemGroup(
                title = stringResource(R.string.live_tv_guide),
                rows =
                    summary.providers.map { it.row(guideActions) }.ifEmpty { listOf(emptyRow(R.string.live_tv_no_guide)) } +
                        guideRows(summary, actions, guideActions, now),
            )
        }
    }
}

@Composable
private fun guideRows(
    summary: LiveTvSummary,
    actions: LiveTvActions,
    guideActions: LiveTvGuideActions,
    now: Long,
): List<ListItem> =
    listOfNotNull(
        ListItem(
            icon = Icons.Outlined.Add,
            label = stringResource(R.string.live_tv_guide_add),
            detail = stringResource(R.string.live_tv_guide_add_detail),
            supportingMaxLines = ADDRESS_LINES,
            onClick = guideActions.onAdd,
        ),
        summary.guideEndMillis?.let { end ->
            ListItem(
                icon = Icons.Outlined.EventNote,
                label = stringResource(R.string.live_tv_guide_ends),
                detail = formatRelativeOrAbsolute(end, now),
                clickable = false,
            )
        },
        actions.onOpenTasks?.let { open ->
            ListItem(
                icon = Icons.Outlined.Sync,
                label = stringResource(R.string.live_tv_refresh_guide),
                detail = stringResource(R.string.live_tv_refresh_guide_detail),
                onClick = open,
            )
        },
    )

@Composable
private fun TunerHostSummary.row(): ListItem =
    ListItem(
        icon = Icons.Outlined.LiveTv,
        label = name,
        detail = listOf(tunerTypeLabel(type), address.takeIf { it != name }).filterNotNull().joinToString(" · "),
        supportingMaxLines = ADDRESS_LINES,
        clickable = false,
    )

@Composable
private fun GuideProviderSummary.row(guideActions: LiveTvGuideActions): ListItem =
    ListItem(
        icon = Icons.Outlined.Dns,
        label = label.ifBlank { guideTypeLabel(type) },
        detail =
            if (lineupPending) {
                stringResource(R.string.live_tv_guide_pending_row, guideTypeLabel(type))
            } else {
                guideTypeLabel(type)
            },
        onClick = { guideActions.onEdit(this) },
    )

@Composable
private fun emptyRow(message: Int): ListItem = ListItem(icon = Icons.Outlined.LiveTv, label = stringResource(message), clickable = false)

/** The server's tuner kinds by name, and a plugin's kind as the server names it. */
@Composable
private fun tunerTypeLabel(type: String): String =
    when (type.lowercase()) {
        "hdhomerun" -> stringResource(R.string.live_tv_type_hdhomerun)
        "m3u" -> stringResource(R.string.live_tv_type_m3u)
        else -> type
    }

@Composable
internal fun guideTypeLabel(type: String): String =
    when (type.lowercase()) {
        "xmltv" -> stringResource(R.string.live_tv_type_xmltv)
        "schedulesdirect" -> stringResource(R.string.live_tv_type_schedules_direct)
        else -> type
    }

private const val LOADING_ROWS = 4

/** An address is what tells two tuners apart, so a larger font wraps it instead of cutting it off. */
private const val ADDRESS_LINES = 3
