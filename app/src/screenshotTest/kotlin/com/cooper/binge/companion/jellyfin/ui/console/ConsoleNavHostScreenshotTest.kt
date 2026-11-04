package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinWidePreview

/** The console on a window wide enough for two panes: the home beside the default page, then beside an open page. */
class ConsoleNavHostScreenshotTest {
    @Composable
    private fun Console(stack: ConsoleBackStack) =
        Surface {
            ConsoleNavHost(
                stack = stack,
                signedIn = true,
                onBack = {},
                home = {
                    Column {
                        Text("Home")
                        Text("Logs")
                    }
                },
                defaultPage = { Text("Now playing") },
                page = { entry -> Text(entry.page.name) },
            )
        }

    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun defaultPage() = Console(ConsoleBackStack())

    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun pageOpen() = Console(ConsoleBackStack().openFromHome(ConsolePage.Logs))
}
