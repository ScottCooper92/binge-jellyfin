package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.PlaybackLanguages
import com.cooper.binge.companion.jellyfin.server.SubtitleMode

private val previewCultures =
    listOf(
        Culture("English", "en", listOf("eng")),
        Culture("French", "fr", listOf("fre", "fra")),
        Culture("Japanese", "ja", listOf("jpn")),
    )

private val previewReady =
    LanguagesUiState.Ready(PlaybackLanguages(audio = "jpn", subtitles = "eng", subtitleMode = SubtitleMode.Smart), previewCultures)

/** The playback languages page: chosen across the matrix, then each other state on the phone cell. */
class LanguagesScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun chosen() = LanguagesScreen(previewReady, LanguagesActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun anyLanguage() =
        LanguagesScreen(LanguagesUiState.Ready(PlaybackLanguages("", "", SubtitleMode.Default), previewCultures), LanguagesActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun notSaved() = LanguagesScreen(previewReady.copy(saveFailed = ConsoleFailure.Unreachable), LanguagesActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = LanguagesScreen(LanguagesUiState.Loading, LanguagesActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun failed() = LanguagesScreen(LanguagesUiState.Failed(ConsoleFailure.Unreachable), LanguagesActions())
}
