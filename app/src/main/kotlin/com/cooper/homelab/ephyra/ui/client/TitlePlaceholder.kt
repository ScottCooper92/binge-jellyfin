package com.cooper.homelab.ephyra.ui.client

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenBody
import com.cooper.homelab.ephyra.R

/** A title's page until its screen lands: the route names which kind it is, and the screen that fills it takes the item's id. */
@Composable
internal fun TitlePlaceholder(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BingeScreenScaffold(title = stringResource(R.string.title_placeholder_title), onBack = onBack, modifier = modifier) { padding ->
        ScreenBody(padding) { _ ->
            EmptyScreen(stringResource(R.string.shell_placeholder_message), Modifier.fillMaxSize())
        }
    }
}
