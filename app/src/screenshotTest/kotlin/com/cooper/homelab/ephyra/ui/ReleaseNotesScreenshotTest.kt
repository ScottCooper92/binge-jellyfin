package com.cooper.homelab.ephyra.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.ItemGroup
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews

/** The release notes rows as the home's server group shows them: a preview has no browser, so these are drawn with one assumed. */
class ReleaseNotesScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun releaseNotes() = Rows(version = "10.11.6")

    /** The server card's Update available pill as a tappable one, the worst case of the card's top row: a preview has no browser, so one is assumed. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun updatePill() =
        Surface {
            Column(Modifier.padding(16.dp)) { ServerHomeCard(previewSignedInAdminAlerts, canOpen = { true }, open = {}) }
        }
}

@Composable
private fun Rows(version: String) =
    Surface {
        Column(Modifier.padding(16.dp)) {
            ItemGroup(title = null, rows = releaseNotesItems(version, canOpen = { true }, open = {}))
        }
    }
