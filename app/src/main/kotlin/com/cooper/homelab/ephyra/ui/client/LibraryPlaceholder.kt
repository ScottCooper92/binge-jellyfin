package com.cooper.homelab.ephyra.ui.client

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenBody
import com.cooper.homelab.ephyra.R

/** A library's screen until the grid lands: its title is not known here (the route carries the id alone), so the page is the tab's. */
@Composable
internal fun LibraryPlaceholder(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BingeScreenScaffold(title = stringResource(R.string.shell_tab_libraries), onBack = onBack, modifier = modifier) { padding ->
        ScreenBody(padding) { _ ->
            EmptyScreen(stringResource(R.string.shell_placeholder_message), Modifier.fillMaxSize())
        }
    }
}
