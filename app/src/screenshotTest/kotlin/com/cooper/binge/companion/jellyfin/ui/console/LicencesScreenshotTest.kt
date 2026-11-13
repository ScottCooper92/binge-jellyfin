package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews

private val previewLibraries =
    listOf(
        LicenceEntry("okhttp", "OkHttp", "5.0.0", listOf("Apache License 2.0"), "https://square.github.io/okhttp/"),
        LicenceEntry("coil", "Coil", "3.3.0", listOf("Apache License 2.0"), "https://coil-kt.github.io/coil/"),
        LicenceEntry(
            "kotlinx-coroutines",
            "Kotlin Coroutines",
            "1.11.0",
            listOf("Apache License 2.0"),
            "https://github.com/Kotlin/kotlinx.coroutines",
        ),
        LicenceEntry(
            "some-lib",
            "A library with a much longer name that has to wrap",
            null,
            listOf("MIT License", "Eclipse Public License 2.0"),
            null,
        ),
        LicenceEntry("no-licence", "No licence given", "0.1", emptyList(), "https://example.com"),
    )

/** The licences page: the list across the matrix, then loading, an empty list and one that could not be read. */
class LicencesScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun list() = LicencesScreen(LicencesUiState.Ready(previewLibraries), onBack = {}, onOpenWebsite = {})

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = LicencesScreen(LicencesUiState.Loading, onBack = {}, onOpenWebsite = {})

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun empty() = LicencesScreen(LicencesUiState.Ready(emptyList()), onBack = {}, onOpenWebsite = {})

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun unreadable() = LicencesScreen(LicencesUiState.Unreadable, onBack = {}, onOpenWebsite = {})
}
