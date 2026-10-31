package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
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
    fun users() = UsersScreen(previewReady, UsersActions(), now = NOW)

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
    @Composable
    fun adding() = UsersScreen(previewReady.copy(dialog = UserDialog.Create), UsersActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun settingPassword() = UsersScreen(previewReady.copy(open = "ana", dialog = UserDialog.Password), UsersActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun deleting() = UsersScreen(previewReady.copy(open = "old", dialog = UserDialog.Delete), UsersActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun notChanged() = UsersScreen(previewReady.copy(open = "ana", actionFailed = ConsoleFailure.Refused), UsersActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = UsersScreen(UsersUiState.Loading, UsersActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = UsersScreen(UsersUiState.Failed(ConsoleFailure.Refused), UsersActions())
}
