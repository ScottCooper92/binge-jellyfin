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
import com.cooper.binge.companion.jellyfin.server.SdCountry
import com.cooper.binge.companion.jellyfin.server.SdLineup
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

    /** An administrator who manages Live TV also sees the Recording row. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun withRecording() =
        LiveTvScreen(LiveTvUiState.Ready(previewSummary), LiveTvActions(onOpenTasks = {}, onOpenRecording = {}), now = NOW)

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
    fun xmltvForm() = Surface { GuideFormContent(GuideForm(), false, LiveTvGuideActions()) }

    /** An existing XMLTV guide: its path filled, and Remove last. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun xmltvEdit() =
        Surface {
            GuideFormContent(GuideForm("g1", "https://guide.example/xmltv.xml", name = "guide"), false, LiveTvGuideActions())
        }

    /** A Schedules Direct account with its lineup: no field, a way to choose the lineup again, and the way to remove it. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun schedulesDirectForm() =
        Surface { GuideFormContent(GuideForm("g2", type = "SchedulesDirect", name = "alice"), false, LiveTvGuideActions()) }

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
    fun discoveryFailed() = Surface { TunerSheetContent(TunerSheet.Failed(ConsoleFailure.Unreachable), false, LiveTvActions()) }

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
    fun guideSaving() = Surface { GuideFormContent(GuideForm("g1", "/config/guide.xml"), true, LiveTvGuideActions()) }

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

    /** An M3U tuner on a 10.11 server, which sent every stream option: some on, some off. Just the group, so the whole of each explanation shows. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun streamOptions() =
        Surface {
            val draft =
                TunerDraft(
                    TUNER_TYPE_M3U,
                    id = "t2",
                    options =
                        mapOf(
                            "AllowFmp4TranscodingContainer" to true,
                            "AllowStreamSharing" to true,
                            "EnableStreamLooping" to false,
                            "IgnoreDts" to true,
                            "ReadAtNativeFramerate" to false,
                        ),
                )
            StreamOptions(draft, listOf("AllowFmp4TranscodingContainer", "AllowStreamSharing", "IgnoreDts")) { _, _ -> }
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun guideRemoveConfirm() =
        ConsoleConfirm(
            title = stringResource(R.string.live_tv_guide_remove_title),
            message = stringResource(R.string.live_tv_guide_remove_message, "/config/guide.xml"),
            confirmLabel = stringResource(R.string.live_tv_guide_remove_confirm),
            onConfirm = {},
            onDismissRequest = {},
            destructive = true,
            inWindow = false,
        )

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
    fun guideNotSaved() =
        ConsoleMessage(
            title = stringResource(R.string.live_tv_guide_not_saved_title),
            message = stringResource(R.string.live_tv_guide_not_saved_body),
            onDismiss = {},
            inWindow = false,
        )

    /** The Add guide data choice, offered where the user may read lineups. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun guideChoice() = Surface { ChooseGuideContent(LiveTvGuideActions()) }

    /** An account saved with no lineup: its row says so, and its form offers to finish the setup. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun unfinishedAccount() =
        LiveTvScreen(
            LiveTvUiState.Ready(
                previewSummary.copy(providers = listOf(GuideProviderSummary("g3", "SchedulesDirect", "carol", lineupPending = true))),
            ),
            withTasks,
            now = NOW,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun unfinishedForm() =
        Surface {
            GuideFormContent(
                GuideForm("g3", type = "SchedulesDirect", name = "carol", lineupPending = true),
                false,
                LiveTvGuideActions(),
            )
        }

    /** A manager without Live TV access: the unfinished account can only be removed. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun unfinishedFormWithoutLiveTvAccess() =
        Surface {
            GuideFormContent(
                GuideForm("g3", type = "SchedulesDirect", name = "carol", lineupPending = true),
                false,
                LiveTvGuideActions(),
                canFinishSetup = false,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun sdAccount() = Surface { SdStepContent(SdStep.Account, false, SdActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sdAccountSaving() = Surface { SdStepContent(SdStep.Account, true, SdActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sdLocation() =
        Surface {
            SdStepContent(
                SdStep.Location("g3", sdCountries, country = sdCountries.first(), postalCode = "10001"),
                false,
                SdActions(),
            )
        }

    /** An account that has a lineup, choosing another: the step says the current one stays. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun sdLocationChanging() =
        Surface {
            SdStepContent(
                SdStep.Location("g3", sdCountries, country = sdCountries.first(), postalCode = "10001", changing = true),
                false,
                SdActions(),
            )
        }

    /** No postal code yet: Find lineups is disabled. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sdLocationEmpty() = Surface { SdStepContent(SdStep.Location("g3", sdCountries, country = sdCountries.first()), false, SdActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sdLocationLoading() = Surface { SdStepContent(SdStep.Location("g3", countries = null), false, SdActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sdCountryList() =
        Surface {
            SdStepContent(SdStep.Location("g3", sdCountries, country = sdCountries.first(), choosing = true), false, SdActions())
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun sdLineups() =
        Surface {
            SdStepContent(
                SdStep.Lineups(
                    "g3",
                    sdCountries,
                    sdCountries.first(),
                    "10001",
                    listOf(SdLineup("USA-NY12345-X", "Comcast Digital"), SdLineup("USA-DISH-X", "Dish Network")),
                ),
                false,
                SdActions(),
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sdLineupsLoading() =
        Surface {
            SdStepContent(SdStep.Lineups("g3", sdCountries, sdCountries.first(), "10001", null), false, SdActions())
        }

    /** A lineup being added: the list is replaced by the loading indicator until the server answers. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sdLineupSaving() =
        Surface {
            SdStepContent(
                SdStep.Lineups(
                    "g3",
                    sdCountries,
                    sdCountries.first(),
                    "10001",
                    listOf(SdLineup("USA-NY12345-X", "Comcast Digital")),
                ),
                true,
                SdActions(),
            )
        }

    /** No lineups, which the server also answers for a login that failed. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sdNoLineups() =
        Surface {
            SdStepContent(SdStep.Lineups("g3", sdCountries, sdCountries.first(), "99999", emptyList()), false, SdActions())
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sdLineupNotAdded() =
        ConsoleMessage(
            title = stringResource(R.string.live_tv_sd_lineup_not_added_title),
            message = stringResource(R.string.live_tv_sd_lineup_not_added_body),
            onDismiss = {},
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

private val sdCountries =
    listOf(SdCountry("USA", "United States", "North America"), SdCountry("GBR", "United Kingdom", "Europe"))
