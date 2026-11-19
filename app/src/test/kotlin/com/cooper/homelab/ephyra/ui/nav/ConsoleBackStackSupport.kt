package com.cooper.homelab.ephyra.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * The app's back stack as the console tests drive it: the signed-in home at the root, then what [seed] opens over it,
 * made once and saved across a rotation like the app's own.
 */
@Composable
fun rememberConsoleBackStack(seed: NavBackStack<NavKey>.() -> Unit = {}): NavBackStack<NavKey> {
    val keys = remember { NavBackStack<NavKey>(HubRoute).apply(seed).toList().toTypedArray() }
    return rememberNavBackStack(*keys)
}
