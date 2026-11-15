package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.ServerUser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val config: JsonObject =
    Json
        .parseToJsonElement(
            """{"UserId":null,"ReleaseDateFormat":"yyyy-MM-dd","SaveImagePathsInNfo":false,"EnablePathSubstitution":true,""" +
                """"EnableExtraThumbsDuplication":false}""",
        ).jsonObject

private val users = listOf(ServerUser("user-1", "Claude", true, null, JsonObject(emptyMap())))

private fun ready(shown: JsonObject = config) = NfoUiState.Ready(shown, users)

/** The NFO page across the matrix, with a user chosen, with the users unreadable, and while it loads or fails. */
class NfoScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun nfo() = NfoScreen(ready(), NfoActions())

    /** A real server sends no `UserId` when none is set: the row still shows, as None. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun noUserKey() = NfoScreen(ready(JsonObject(config - "UserId")), NfoActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun userChosen() = NfoScreen(ready(JsonObject(config + ("UserId" to JsonPrimitive("user-1")))), NfoActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun usersUnreadable() = NfoScreen(NfoUiState.Ready(JsonObject(config + ("UserId" to JsonPrimitive("user-9")))), NfoActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = NfoScreen(NfoUiState.Loading, NfoActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = NfoScreen(NfoUiState.Failed(ConsoleFailure.Unreachable), NfoActions())
}
