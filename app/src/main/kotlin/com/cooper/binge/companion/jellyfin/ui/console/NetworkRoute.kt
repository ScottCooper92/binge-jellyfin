package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile

/** The networking page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun NetworkRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    onOpenServer: () -> Unit,
    onBack: () -> Unit,
) {
    val network: NetworkViewModel = viewModel(factory = viewModelFactory { initializer { NetworkViewModel(graph.connection, profile) } })
    val state by network.state.collectAsStateWithLifecycle()
    // The address this app is signed in to, which the confirm names.
    var appUrl by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        appUrl =
            graph.connection
                .signedIn()
                ?.first
                ?.baseUrl
                .orEmpty()
    }
    NetworkScreen(
        state,
        NetworkActions(
            onSheet = network::sheet,
            onNumber = network::saveNumber,
            onText = network::saveText,
            onItems = network::saveItems,
            onFormSwitch = { key, on -> network.setSwitch(key, on, form = true) },
            onSwitch = { key, on -> network.setSwitch(key, on, form = false) },
            onSave = network::save,
            onConfirm = network::confirm,
            onDismissConfirm = network::dismissConfirm,
            onOpenServer = onOpenServer,
            onRetry = network::load,
            onRetrySave = network::retrySave,
            onBack = onBack,
        ),
        appUrl,
    )
}
