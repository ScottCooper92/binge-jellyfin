package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.HomeLayout
import com.cooper.binge.companion.jellyfin.server.UserView

private val previewViews =
    listOf(
        UserView("movies", "Movies", "movies"),
        UserView("shows", "Shows", "tvshows"),
        UserView("music", "Music", "music"),
        UserView("home", "Home videos and photos from the family archive"),
    )

private val previewHome =
    HomeLayoutUiState.Ready(
        previewViews,
        HomeLayout(order = listOf("shows"), myMediaExcludes = setOf("home"), latestExcludes = setOf("music", "home")),
        groupable = listOf(previewViews.last()),
    )

/** The home screen page: the libraries and switches across the matrix, a library's page, then each other state. */
class HomeLayoutScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun libraries() = HomeLayoutScreen(previewHome, HomeLayoutActions())

    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun library() = HomeLayoutScreen(previewHome.copy(openId = "home"), HomeLayoutActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun firstLibrary() = HomeLayoutScreen(previewHome.copy(openId = "shows"), HomeLayoutActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun noLibraries() = HomeLayoutScreen(HomeLayoutUiState.Ready(emptyList(), HomeLayout()), HomeLayoutActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun saving() = HomeLayoutScreen(previewHome.copy(save = SaveProgress(SaveStatus.Saving)), HomeLayoutActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun saved() = HomeLayoutScreen(previewHome.copy(save = SaveProgress(SaveStatus.Saved)), HomeLayoutActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = HomeLayoutScreen(HomeLayoutUiState.Loading, HomeLayoutActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = HomeLayoutScreen(HomeLayoutUiState.Failed(ConsoleFailure.Unreachable), HomeLayoutActions())
}
