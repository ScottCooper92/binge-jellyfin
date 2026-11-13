package com.cooper.binge.companion.jellyfin.ui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Asks for a Play review when the user comes back to the root after leaving it, so never mid-task and never over a
 * screen of their own making. Whether to show anything is the prompt's to decide.
 */
@Composable
internal fun ReviewPromptOnReturnToRoot(
    atRoot: Boolean,
    onReturnedToRoot: (Activity) -> Unit,
) {
    val activity = LocalActivity.current
    var hasLeftRoot by rememberSaveable { mutableStateOf(false) }
    val onReturned by rememberUpdatedState(onReturnedToRoot)
    LaunchedEffect(atRoot) {
        if (!atRoot) {
            hasLeftRoot = true
            return@LaunchedEffect
        }
        if (!hasLeftRoot) return@LaunchedEffect
        hasLeftRoot = false
        activity?.let(onReturned)
    }
}
