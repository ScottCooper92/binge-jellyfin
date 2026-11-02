package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.template.ScreenFooter

/**
 * A page's create action, as the footer's one primary button: always in the same place, within thumb reach, and not
 * scrolled away with the list. It is part of the page's structure, so it doesn't pop in: [visible] says whether the page
 * has one at all (not on a failure that replaces the page, and not on a sub-page), [enabled] whether it can be used yet
 * (not while the list is still loading), and [loading] that the create it starts is in flight.
 */
@Composable
internal fun CreateFooter(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    visible: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    if (!visible) return
    ScreenFooter {
        BingeFilledButton(
            label = label,
            onClick = onClick,
            leadingIcon = icon,
            enabled = enabled,
            loading = loading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
