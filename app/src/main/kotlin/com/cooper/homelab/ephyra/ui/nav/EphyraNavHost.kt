package com.cooper.homelab.ephyra.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.binge.designsystem.motion.rememberBingeNavMotion

/**
 * The one [NavDisplay]. The sign-in steps are [HomeRoute], the signed-in console is [HubRoute]; [settleHome] swaps the root
 * as [signedIn] changes. The sign-in state machine stays in its view model. The console keeps its own stack inside
 * [HubRoute] until its pages and items move onto this one.
 *
 * No entry decorator but the saved-state one: view models stay where they were, in the Activity's store and the console's own.
 */
@Composable
fun EphyraNavHost(
    backStack: NavBackStack<NavKey>,
    signedIn: Boolean,
    onBack: () -> Unit,
    signedOut: @Composable () -> Unit,
    console: @Composable () -> Unit,
) {
    LaunchedEffect(signedIn) { backStack.settleHome(signedIn) }
    // The entries are built once for their key, so what changes later is read inside their content.
    val signedOutNow by rememberUpdatedState(signedOut)
    val consoleNow by rememberUpdatedState(console)
    val motion = rememberBingeNavMotion()
    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        transitionSpec = { motion.detailForward },
        popTransitionSpec = { motion.detailPop },
        predictivePopTransitionSpec = { motion.detailPredictivePop },
        entryProvider =
            entryProvider {
                entry<HomeRoute> { signedOutNow() }
                entry<HubRoute> { consoleNow() }
            },
    )
}
