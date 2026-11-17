package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.ui.openInBrowser
import com.cooper.homelab.core.presentation.appsettings.LicencesViewModel
import com.cooper.homelab.core.uiandroid.appsettings.LicencesScreen

/** The open-source licences page: the shared screen over a view model per opening that reads the shipped list once. */
@Composable
internal fun LicencesRoute(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val licences = remember(graph) { LicencesViewModel(scope, graph.licences) }
    val state by licences.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LicencesScreen(state = state, onBack = onBack, onOpenWebsite = context::openInBrowser)
}
