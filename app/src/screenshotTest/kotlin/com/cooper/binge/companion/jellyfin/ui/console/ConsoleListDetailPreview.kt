package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.JellyfinRoot
import com.cooper.binge.companion.jellyfin.ui.previewSignedInAdmin

/** The real navigation host, so the page's two panes have the same widths and insets as beside the console home. */
@Composable
internal fun ConsoleListDetailPreview(
    page: ConsolePage,
    content: @Composable () -> Unit,
) {
    Surface {
        ConsoleNavHost(
            stack = ConsoleBackStack().openFromHome(page),
            signedIn = true,
            onBack = {},
            home = { JellyfinRoot(previewSignedInAdmin, Actions(selectedPage = page)) },
            defaultPage = {},
            page = { content() },
        )
    }
}
