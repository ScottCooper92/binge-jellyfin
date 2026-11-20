package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.ephyra.browse.TrackKind
import com.cooper.homelab.ephyra.client.title.Resolution
import com.cooper.homelab.ephyra.client.title.TrackUi
import com.cooper.homelab.ephyra.client.title.VersionSheetContent
import com.cooper.homelab.ephyra.client.title.VersionUi
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews

/**
 * The versions sheet (`:client`): a film with a named file, a file named only by its resolution and range, and a bare one that falls back
 * to "Version 3". The sheet's body is shown without its modal, which the design system draws.
 */
class ClientVersionSheetScreenshotTest {
    private fun audio(label: String) =
        TrackUi(1, TrackKind.Audio, label, isDefault = true, isForced = false, isExternal = false, isHearingImpaired = false)

    private fun subtitle(index: Int) = TrackUi(index, TrackKind.Subtitle, null, false, false, false, false)

    private val versions =
        listOf(
            VersionUi(
                "v1",
                "Director's Cut",
                "mkv",
                Resolution.FullHd,
                null,
                6_200_000_000L,
                listOf(audio("English · EAC3 5.1")),
                listOf(subtitle(2), subtitle(3)),
                1,
                null,
            ),
            VersionUi(
                "v2",
                null,
                "mp4",
                Resolution.Uhd,
                "HDR10",
                14_800_000_000L,
                listOf(audio("English · TRUEHD 7.1")),
                listOf(subtitle(2)),
                1,
                null,
            ),
            VersionUi("v3", null, null, null, null, null, emptyList(), emptyList(), null, null),
        )

    @Composable
    private fun Sheet() = VersionSheetContent(versions, selected = "v2", onSelect = {})

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun versions() = Sheet()
}
