package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinWidePreview

/**
 * The console on a window wide enough for two panes: the home beside the default page, then beside an open page. On a
 * landscape tablet a page that opens its items as entries is the middle pane, with its item, or the invitation to pick one, as the third.
 */
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
                item = { entry -> Text("${entry.page.name} ${entry.item}") },
            )
        }

    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun defaultPage() = Console(ConsoleBackStack())

    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun pageOpen() = Console(ConsoleBackStack().openFromHome(ConsolePage.Plugins))

    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun itemPageNothingOpen() = Console(ConsoleBackStack().openFromHome(ConsolePage.Tasks))

    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun itemOpen() = Console(ConsoleBackStack().openFromHome(ConsolePage.Tasks).openItem("scan"))
}
