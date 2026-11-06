package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import com.cooper.binge.companion.jellyfin.preview.JellyfinWidePreview
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage

/** The console home as the list pane of a wide window, a third of it, marking the page that fills the pane beside it. */
class JellyfinHomeBesideScreenshotTest {
    @Composable
    private fun HomeBeside(selected: ConsolePage) =
        Surface {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    CompositionLocalProvider(LocalIsSinglePaneNav provides false) {
                        PaneContent(innerEdge = PaneEdge.End) { JellyfinRoot(previewSignedInAdmin, Actions(selectedPage = selected)) }
                    }
                }
                Box(Modifier.weight(2f))
            }
        }

    /** A page is open: its row is the marked one. */
    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun pageOpen() = HomeBeside(ConsolePage.Tasks)

    /** No page is open, so Now playing fills the detail pane and its row is marked. */
    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun noPageOpen() = HomeBeside(ConsolePage.NowPlaying)
}
