package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.PlaybackPreferences
import com.cooper.binge.companion.jellyfin.server.SubtitleMode

private val previewCultures =
    listOf(
        Culture("English", "en", listOf("eng")),
        Culture("French", "fr", listOf("fre", "fra")),
        Culture("Japanese", "ja", listOf("jpn")),
    )

private val previewReady =
    PlaybackUiState.Ready(PlaybackPreferences(audio = "jpn", subtitles = "eng", subtitleMode = SubtitleMode.Smart), previewCultures)

/** The playback page: chosen across the matrix, then each other state on the phone cell. */
class PlaybackScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun chosen() = PlaybackScreen(previewReady, PlaybackActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun anyLanguage() =
        PlaybackScreen(PlaybackUiState.Ready(PlaybackPreferences("", "", SubtitleMode.Default), previewCultures), PlaybackActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun switchesOff() =
        PlaybackScreen(
            PlaybackUiState.Ready(
                PlaybackPreferences(
                    audio = "jpn",
                    subtitles = "eng",
                    subtitleMode = SubtitleMode.Always,
                    playDefaultAudioTrack = false,
                    rememberAudio = false,
                    rememberSubtitles = false,
                    autoplayNextEpisode = false,
                ),
                previewCultures,
            ),
            PlaybackActions(),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun saving() = PlaybackScreen(previewReady.copy(save = SaveProgress(SaveStatus.Saving)), PlaybackActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun saved() = PlaybackScreen(previewReady.copy(save = SaveProgress(SaveStatus.Saved)), PlaybackActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun notSaved() = PlaybackScreen(previewReady.copy(save = SaveProgress(failed = true)), PlaybackActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun refused() = PlaybackScreen(previewReady.copy(refused = ConsoleFailure.Refused), PlaybackActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = PlaybackScreen(PlaybackUiState.Loading, PlaybackActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun failed() = PlaybackScreen(PlaybackUiState.Failed(ConsoleFailure.Unreachable), PlaybackActions())
}
