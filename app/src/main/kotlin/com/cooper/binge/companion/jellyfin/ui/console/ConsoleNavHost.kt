package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

/** A page changes with no animation, as it did before Navigation 3: the new page is simply there. */
private val Instant = ContentTransform(EnterTransition.None, ExitTransition.None)

/**
 * The console's pages as Navigation 3 entries over [stack]: the home at the bottom, then each open page. Back
 * pops the top page through [onBack], after any `BackHandler` a page has of its own (a list's open detail closes
 * first). Each entry keeps its own saved state, so the home's scroll position is still there when it comes back,
 * and its own view models, which live as long as the entry is on the stack, across a rotation, and go with it
 * when it leaves.
 */
@Composable
fun ConsoleNavHost(
    stack: ConsoleBackStack,
    onBack: () -> Unit,
    home: @Composable () -> Unit,
    page: @Composable (ConsoleEntry) -> Unit,
) {
    NavDisplay(
        backStack = stack.keys,
        onBack = onBack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        transitionSpec = { Instant },
        popTransitionSpec = { Instant },
        predictivePopTransitionSpec = { Instant },
        entryProvider = { key ->
            NavEntry(key) {
                when (key) {
                    ConsoleKey.Home -> home()
                    is ConsoleKey.Page -> page(ConsoleEntry(key.page, key.opening))
                }
            }
        },
    )
}
