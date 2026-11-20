package com.cooper.homelab.ephyra.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheet
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.FormSaveFailureSnackbar
import com.binge.designsystem.template.formSaveStatus
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.playback.STREAMING_QUALITY_STEPS_MBPS
import com.cooper.homelab.ephyra.playback.StreamingNetwork
import com.cooper.homelab.ephyra.ui.console.LanguagePicker
import com.cooper.homelab.ephyra.ui.console.LanguageSheet
import com.cooper.homelab.ephyra.ui.console.PlaybackActions
import com.cooper.homelab.ephyra.ui.console.PlaybackUiState
import com.cooper.homelab.ephyra.ui.console.RefusedMessage
import com.cooper.homelab.ephyra.ui.console.bitrateLabel
import com.cooper.homelab.ephyra.ui.console.bitrateText
import com.cooper.homelab.ephyra.ui.console.languageItems

/**
 * The Playback group on the This app page: the user's audio language, subtitle language and subtitle mode. They are the server's own
 * user preferences, so the rows, the pickers and the saves are the playback page's (one owner), and each change saves as it is made.
 * Those rows wait for the read, and a read that fails leaves them out. The two quality rows, the most a play may stream on Wi-Fi and
 * on mobile data, are the device's own, so they show whenever [quality] is read.
 */
@Composable
internal fun PlaybackSettingsGroup(
    state: PlaybackUiState?,
    quality: StreamingQualitySettings?,
    onQuality: (StreamingNetwork, Int) -> Unit,
    actions: PlaybackActions,
    host: SnackbarHostState,
) {
    val ready = state as? PlaybackUiState.Ready
    var open by rememberSaveable { mutableStateOf<LanguagePicker?>(null) }
    var openQuality by rememberSaveable { mutableStateOf<StreamingNetwork?>(null) }
    val languages = ready?.let { languageItems(it) { picker -> open = picker } }
    val rows =
        listOfNotNull(languages?.audio, languages?.subtitles, languages?.subtitleMode) +
            quality?.let { qualityItems(it) { network -> openQuality = network } }.orEmpty()
    if (rows.isNotEmpty()) ItemGroup(title = stringResource(R.string.playback_title), rows = rows)
    if (ready != null) {
        open?.let { LanguageSheet(it, ready, actions) { open = null } }
        FormSaveFailureSnackbar(formSaveStatus(ready.save.status, ready.save.failed, actions.onRetrySave), host)
        RefusedMessage(ready.refused, host)
    }
    if (quality != null) openQuality?.let { QualitySheet(it, quality, onQuality) { openQuality = null } }
}

/** The two rows for the most a play may stream: each shows its value, and a tap opens the picker. */
@Composable
private fun qualityItems(
    quality: StreamingQualitySettings,
    onOpen: (StreamingNetwork) -> Unit,
): List<ListItem> =
    listOf(
        ListItem(
            icon = Icons.Outlined.Wifi,
            label = stringResource(R.string.playback_quality_wifi),
            detail = qualityLabel(quality.wifiMbps),
            onClick = { onOpen(StreamingNetwork.Wifi) },
        ),
        ListItem(
            icon = Icons.Outlined.SignalCellularAlt,
            label = stringResource(R.string.playback_quality_mobile),
            detail = qualityLabel(quality.mobileMbps),
            onClick = { onOpen(StreamingNetwork.Mobile) },
        ),
    )

@Composable
private fun QualitySheet(
    network: StreamingNetwork,
    quality: StreamingQualitySettings,
    onSelect: (StreamingNetwork, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val auto = stringResource(R.string.playback_quality_auto)
    val resources = LocalContext.current.resources
    val choices =
        remember(auto) {
            STREAMING_QUALITY_STEPS_MBPS.map {
                BingeChoice(
                    it,
                    if (it ==
                        0
                    ) {
                        auto
                    } else {
                        bitrateText(resources, it * BITS_PER_MEGABIT)
                    },
                )
            }
        }
    BingeChoiceSheet(
        title = stringResource(if (network == StreamingNetwork.Wifi) R.string.playback_quality_wifi else R.string.playback_quality_mobile),
        choices = BingeChoiceList.Ready(choices),
        selected = if (network == StreamingNetwork.Wifi) quality.wifiMbps else quality.mobileMbps,
        onSelect = { onSelect(network, it) },
        onDismissRequest = onDismiss,
    )
}

/** A step as the picker and the row say it: Auto, or the megabits. */
@Composable
private fun qualityLabel(mbps: Int): String =
    if (mbps ==
        0
    ) {
        stringResource(R.string.playback_quality_auto)
    } else {
        bitrateLabel(mbps * BITS_PER_MEGABIT)
    }

private const val BITS_PER_MEGABIT = 1_000_000
