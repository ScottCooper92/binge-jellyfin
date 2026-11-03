package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.GuideProviderSummary
import com.cooper.binge.companion.jellyfin.server.LiveTvSummary
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_HDHOMERUN
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_M3U
import com.cooper.binge.companion.jellyfin.server.TunerHostSummary

private const val NOW = 1_793_534_400_000L // 31 Oct 2026, 12:00 UTC
private const val DAY = 24 * 60 * 60 * 1000L

private val previewSummary =
    LiveTvSummary(
        tuners =
            listOf(
                TunerHostSummary("t1", "Living room HDHomeRun", "hdhomerun", "http://192.168.1.50"),
                TunerHostSummary("t2", "IPTV", "m3u", "https://iptv.example:8443"),
                TunerHostSummary("t3", "Cable box", "somePlugin", "http://cable.lan"),
            ),
        providers =
            listOf(
                GuideProviderSummary("g1", "SchedulesDirect", "alice"),
                GuideProviderSummary("g2", "xmltv", "/config/guide.xml"),
            ),
        guideEndMillis = NOW + 7 * DAY,
    )

private val withTasks = LiveTvActions(onOpenTasks = {})

/** Live TV: the page across the matrix, then what is empty, and what a user who cannot open Scheduled tasks sees. */
class LiveTvScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun liveTv() = LiveTvScreen(LiveTvUiState.Ready(previewSummary), withTasks, now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun empty() = LiveTvScreen(LiveTvUiState.Ready(LiveTvSummary()), withTasks, now = NOW)

    /** A user who manages Live TV but cannot open Scheduled tasks: no Refresh guide row, and no guide end where they cannot ask. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun withoutTasks() = LiveTvScreen(LiveTvUiState.Ready(previewSummary.copy(guideEndMillis = null)), LiveTvActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = LiveTvScreen(LiveTvUiState.Loading, LiveTvActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = LiveTvScreen(LiveTvUiState.Failed(ConsoleFailure.Unreachable), LiveTvActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun addChoice() = Surface { TunerSheetContent(TunerSheet.Choose, false, LiveTvActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun discovering() = Surface { TunerSheetContent(TunerSheet.Discovering, false, LiveTvActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun found() =
        Surface {
            TunerSheetContent(
                TunerSheet.Found(
                    listOf(
                        TunerDraft(TUNER_TYPE_HDHOMERUN, name = "HDHomeRun PRIME", url = "http://192.168.1.77"),
                        TunerDraft(TUNER_TYPE_HDHOMERUN, name = "", url = "http://192.168.1.78"),
                    ),
                ),
                false,
                LiveTvActions(),
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun foundNone() = Surface { TunerSheetContent(TunerSheet.Found(emptyList()), false, LiveTvActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun hdHomeRunForm() =
        Surface {
            TunerSheetContent(
                TunerSheet.Form(TunerDraft(TUNER_TYPE_HDHOMERUN, url = "http://192.168.1.77")),
                false,
                LiveTvActions(),
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun m3uForm() = Surface { TunerSheetContent(TunerSheet.Form(TunerDraft(TUNER_TYPE_M3U)), false, LiveTvActions()) }

    /** An existing tuner: its fields filled, and Remove last. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun editForm() =
        Surface {
            val draft =
                TunerDraft(
                    TUNER_TYPE_M3U,
                    id = "t2",
                    name = "IPTV",
                    url = "https://iptv.example:8443/list.m3u",
                    userAgent = "VLC",
                    streamLimit = "2",
                )
            TunerSheetContent(TunerSheet.Form(draft), false, LiveTvActions())
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun saving() =
        Surface {
            TunerSheetContent(TunerSheet.Form(TunerDraft(TUNER_TYPE_HDHOMERUN, url = "http://192.168.1.77")), true, LiveTvActions())
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun removeConfirm() =
        ConsoleConfirm(
            title = stringResource(R.string.live_tv_remove_title),
            message = stringResource(R.string.live_tv_remove_message, "IPTV"),
            confirmLabel = stringResource(R.string.live_tv_remove_confirm),
            onConfirm = {},
            onDismissRequest = {},
            destructive = true,
            inWindow = false,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun notAdded() =
        ConsoleMessage(
            title = stringResource(R.string.live_tv_not_saved_title),
            message = stringResource(R.string.live_tv_not_saved_body),
            onDismiss = {},
            inWindow = false,
        )
}
