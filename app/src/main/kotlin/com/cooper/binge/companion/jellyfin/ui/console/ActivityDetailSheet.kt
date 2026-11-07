package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ActivityEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.binge.designsystem.R as DesR

/** The rows of the list cut a long line at two; the sheet gives it whole. */
private const val DETAIL_LINES = 12

/** One activity entry in full: its name, when it happened to the second, its type, and every line the server wrote about it. */
@Composable
internal fun ActivityDetailSheet(
    entry: ActivityEntry,
    zone: ZoneId,
    inWindow: Boolean = true,
    onClose: () -> Unit,
) {
    ConsoleSheet(onDismissRequest = onClose, inWindow = inWindow) { ActivityDetailContent(entry, zone, onClose) }
}

@Composable
internal fun ActivityDetailContent(
    entry: ActivityEntry,
    zone: ZoneId,
    onClose: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    val moment = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone)
    val rows =
        listOfNotNull(
            entry.dateMillis?.let {
                ListItem(
                    icon = Icons.Outlined.Schedule,
                    label = stringResource(R.string.activity_detail_time),
                    detail = moment.format(Instant.ofEpochMilli(it)),
                    clickable = false,
                )
            },
            entry.type.takeIf { it.isNotBlank() }?.let {
                ListItem(icon = Icons.Outlined.Sell, label = stringResource(R.string.activity_detail_type), detail = it, clickable = false)
            },
            entry.overview?.let { detailRow(R.string.activity_detail_summary, it) },
            entry.longOverview?.let { detailRow(R.string.activity_detail_more, it) },
        )
    Column(
        modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            Text(text = entry.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            if (rows.isNotEmpty()) ItemGroup(title = null, rows = rows)
        }
        BingeFilledButton(label = stringResource(R.string.activity_detail_close), onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun detailRow(
    label: Int,
    text: String,
): ListItem =
    ListItem(
        icon = Icons.Outlined.Notes,
        label = stringResource(label),
        detail = text,
        supportingMaxLines = DETAIL_LINES,
        clickable = false,
    )
