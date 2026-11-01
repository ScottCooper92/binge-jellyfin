package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.LiveChannel
import com.cooper.binge.companion.jellyfin.server.ParentalRating
import com.cooper.binge.companion.jellyfin.server.ServerDevice
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.server.ServerUser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private val newest = JellyfinServerProfile.from("10.11.6")

private val policy =
    Json
        .parseToJsonElement(
            """{"IsAdministrator":false,"IsDisabled":false,"EnableAllFolders":true,"EnabledFolders":[],"MaxActiveSessions":2,
            "EnableRemoteAccess":true,"IsHidden":false,"EnableUserPreferenceAccess":true,"LoginAttemptsBeforeLockout":5,
            "EnableMediaPlayback":true,"EnableAudioPlaybackTranscoding":true,"EnableVideoPlaybackTranscoding":false,
            "EnablePlaybackRemuxing":true,"ForceRemoteSourceTranscoding":false,"RemoteClientBitrateLimit":8000000,
            "EnableContentDownloading":true,"EnableContentDeletion":false,"EnableContentDeletionFromFolders":["m"],
            "EnableMediaConversion":false,"EnablePublicSharing":false,"EnableCollectionManagement":false,
            "EnableSubtitleManagement":true,"EnableLyricManagement":false,"EnableRemoteControlOfOtherUsers":false,
            "EnableSharedDeviceControl":true,"SyncPlayAccess":"JoinGroups","EnableLiveTvAccess":true,"EnableLiveTvManagement":false,
            "EnableAllChannels":false,"EnabledChannels":["c1"],"EnableAllDevices":false,"EnabledDevices":["d1"],
            "MaxParentalRating":5,"MaxParentalSubRating":null,"BlockUnratedItems":["Movie","Series"],"BlockedTags":["gore","horror"],
            "AllowedTags":[],"AccessSchedules":[{"DayOfWeek":"Weekday","StartHour":8,"EndHour":17.5}]}""",
        ).jsonObject

private val libraries = listOf(ServerLibrary("m", "Movies", "movies", emptyList()), ServerLibrary("s", "Shows", "tvshows", emptyList()))
private val user = ServerUser("sam", "Sam", true, 1_793_534_400_000L, policy)

private fun ready(section: UserSection?) = UsersUiState.Ready(listOf(user), libraries, me = "me", open = "sam", section = section)

private val choices =
    UserChoices(
        ratings = Loaded.Ready(listOf(ParentalRating("G", 1, 0), ParentalRating("PG", 5, 2), ParentalRating("R", 9))),
        channels = Loaded.Ready(listOf(LiveChannel("c1", "News"), LiveChannel("c2", "Sport"))),
        devices =
            Loaded.Ready(
                listOf(
                    ServerDevice("d1", "Pixel 9", null, "Binge", "Sam", null),
                    ServerDevice("d2", "Living room TV", null, "Binge TV", "Sam", null),
                ),
            ),
    )

/** A user's settings: their page with the sections, then each section, the older releases, the loading and failed lists, and the forms. */
class UserPolicyScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun sections() = UsersScreen(ready(null), UsersActions(), choices = choices, profile = newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun access() = UsersScreen(ready(UserSection.Access), UsersActions(), choices = choices, profile = newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @Composable
    fun playback() = UsersScreen(ready(UserSection.Playback), UsersActions(), choices = choices, profile = newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun content() = UsersScreen(ready(UserSection.Content), UsersActions(), choices = choices, profile = newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun contentBefore109() =
        UsersScreen(ready(UserSection.Content), UsersActions(), choices = choices, profile = JellyfinServerProfile.from("10.8.13"))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun control() = UsersScreen(ready(UserSection.Control), UsersActions(), choices = choices, profile = newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun liveTv() = UsersScreen(ready(UserSection.LiveTv), UsersActions(), choices = choices, profile = newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun liveTvLoading() =
        UsersScreen(ready(UserSection.LiveTv), UsersActions(), choices = UserChoices(channels = Loaded.Loading), profile = newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun liveTvFailed() =
        UsersScreen(
            ready(UserSection.LiveTv),
            UsersActions(),
            choices = UserChoices(channels = Loaded.Failed(ConsoleFailure.Unreachable)),
            profile = newest,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun devices() = UsersScreen(ready(UserSection.Devices), UsersActions(), choices = choices, profile = newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun parental() = UsersScreen(ready(UserSection.Parental), UsersActions(), choices = choices, profile = newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun parentalBefore1011() =
        UsersScreen(ready(UserSection.Parental), UsersActions(), choices = choices, profile = JellyfinServerProfile.from("10.10.7"))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun parentalFailed() =
        UsersScreen(
            ready(UserSection.Parental).copy(actionFailed = ConsoleFailure.Refused),
            UsersActions(),
            choices = UserChoices(ratings = Loaded.Failed(ConsoleFailure.Unreachable)),
            profile = newest,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun newTag() = Surface { TagForm("BlockedTags", UsersActions(), {}) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun scheduleDay() = Surface { ScheduleForm(UsersActions(), {}) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun scheduleStart() = Surface { ScheduleForm(UsersActions(), {}, startStep = 1, startDay = "Weekday") }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun scheduleEnd() = Surface { ScheduleForm(UsersActions(), {}, startStep = 2, startDay = "Weekday", startHour = 8) }
}
