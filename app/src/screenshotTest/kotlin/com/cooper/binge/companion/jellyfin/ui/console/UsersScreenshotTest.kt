package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.LocalIsSinglePaneNav
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStateLightPreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinWidePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinWideSpanishPreview
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.server.ServerUser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private const val NOW = 1_793_534_400_000L // 31 Oct 2026, 12:00 UTC
private const val HOUR = 60 * 60 * 1000L

private fun policy(
    admin: Boolean = false,
    disabled: Boolean = false,
    all: Boolean = true,
    folders: String = "",
) = Json.parseToJsonElement(
    """{"IsAdministrator":$admin,"IsDisabled":$disabled,"EnableAllFolders":$all,"EnabledFolders":[$folders]}""",
) as JsonObject

private val previewUsers =
    listOf(
        ServerUser("ana", "Ana", true, NOW - HOUR / 2, policy()),
        ServerUser("me", "Claude", true, NOW - HOUR / 60, policy(admin = true)),
        ServerUser("kids", "Kids", false, NOW - 26 * HOUR, policy(all = false, folders = "\"m\"")),
        ServerUser("old", "Lodger", true, NOW - 900 * HOUR, policy(disabled = true)),
    )

private val previewLibraries =
    listOf(
        ServerLibrary("m", "Movies", "movies", emptyList()),
        ServerLibrary("s", "Shows", "tvshows", emptyList()),
        ServerLibrary("h", "Family videos", "homevideos", emptyList()),
    )

private val previewReady = UsersUiState.Ready(previewUsers, previewLibraries, me = "me")

/** Users: the list across the matrix, a user's page and mine, the dialogs, and the read states. */
class UsersScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun users() =
        UsersScreen(
            previewReady,
            UsersActions(),
            now = NOW,
            avatar = { UserAvatar(it.name, profilePictureFixture.takeIf { _ -> it.id == "ana" }) },
        )

    /** A pull in progress: the list stays, with the pull spinner over it. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun refreshing() = UsersScreen(previewReady.copy(refreshing = true), UsersActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun userPage() = UsersScreen(previewReady.copy(open = "kids", done = UserDone.PasswordSet), UsersActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun myPage() = UsersScreen(previewReady.copy(open = "me"), UsersActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun adding() = Surface { CreateSheet(UsersActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun addingSubmitting() = Surface { CreateSheet(UsersActions(), submitting = true, inWindow = false) }

    /** The form stays open with what was typed, and says why in its own error slot. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun addingFailed() = Surface { CreateSheet(UsersActions(), error = "Not changed. Something went wrong. Try again.", inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun settingPasswordSubmitting() = Surface { PasswordSheet(previewUsers.first(), UsersActions(), submitting = true, inWindow = false) }

    /** The form stays open with what was typed, and says why in its own error slot. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun settingPasswordFailed() =
        Surface {
            PasswordSheet(previewUsers.first(), UsersActions(), error = "Not changed. Something went wrong. Try again.", inWindow = false)
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun settingPassword() = Surface { PasswordSheet(previewUsers.first(), UsersActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun deleting() = Surface { DeleteDialog(previewUsers.last(), UsersActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = UsersScreen(UsersUiState.Loading, UsersActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = UsersScreen(UsersUiState.Failed(ConsoleFailure.Refused), UsersActions())
}

/**
 * The users without the one last seen 900 hours ago, whose row shows a date: a date's format in Spanish differs between
 * JDKs, and a frame recorded on one would fail on another.
 */
private val splitReady = previewReady.copy(users = previewUsers.filterNot { it.id == "old" })

@Composable
private fun SplitUsers(state: UsersUiState.Ready) =
    CompositionLocalProvider(LocalPageSpansTwoPanes provides true, LocalIsSinglePaneNav provides false) {
        UsersScreen(state, UsersActions(), now = NOW)
    }

/** The users page on a window with room for two panes: the list beside the invitation to pick a user. A user open is in `UserPolicyScreenshotTest`, whose users have the policy that fills their page. */
class UsersSplitScreenshotTest {
    /** Nothing open: the list beside the invitation to pick a user. */
    @PreviewTest
    @JellyfinWidePreview
    @JellyfinWideSpanishPreview
    @Composable
    fun nothingOpen() = SplitUsers(splitReady)
}
