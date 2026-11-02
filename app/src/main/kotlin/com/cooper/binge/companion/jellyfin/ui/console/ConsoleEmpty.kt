package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.designsystem.template.EmptyScreen

/** How much of the viewport an empty list takes, leaving the page's own text above it in view. */
private const val EMPTY_HEIGHT_FRACTION = 0.6f

/**
 * A console list with nothing in it: the design system's [EmptyScreen], over most of the viewport so it reads as the page's
 * state and the text or buttons above it stay where they are.
 */
@Composable
internal fun LazyItemScope.ConsoleEmptyScreen(
    message: String,
    icon: ImageVector,
) = EmptyScreen(message = message, icon = icon, modifier = Modifier.fillParentMaxHeight(EMPTY_HEIGHT_FRACTION))
