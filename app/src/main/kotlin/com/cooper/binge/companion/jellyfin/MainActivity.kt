package com.cooper.binge.companion.jellyfin

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.JellyfinRoot
import com.cooper.binge.companion.jellyfin.ui.JellyfinViewModel
import com.cooper.binge.companion.jellyfin.ui.UiState
import com.cooper.binge.companion.jellyfin.ui.console.ApproveActions
import com.cooper.binge.companion.jellyfin.ui.console.ApproveCodeScreen
import com.cooper.binge.companion.jellyfin.ui.console.ApproveCodeViewModel
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.binge.companion.jellyfin.ui.console.LanguagesActions
import com.cooper.binge.companion.jellyfin.ui.console.LanguagesScreen
import com.cooper.binge.companion.jellyfin.ui.console.LanguagesViewModel

/** The app's own UI: connect to a server, sign in, and the signed-in settings. */
class MainActivity : ComponentActivity() {
    /** What runs once the user has answered the local-network permission, whatever they chose. */
    private var afterLocalNetwork: (() -> Unit)? = null

    private val localNetwork =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            afterLocalNetwork?.invoke()
            afterLocalNetwork = null
        }

    /**
     * Runs [then] with the local-network permission asked for where the platform has it (Android 17).
     * Refused, [then] still runs: the search finds nothing and the typed address is the way in.
     */
    private fun withLocalNetwork(then: () -> Unit) {
        val granted =
            Build.VERSION.SDK_INT < LOCAL_NETWORK_SDK ||
                checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            then()
        } else {
            afterLocalNetwork = then
            localNetwork.launch(ACCESS_LOCAL_NETWORK)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as JellyfinApp).graph
        val factory =
            viewModelFactory {
                initializer {
                    JellyfinViewModel(
                        graph.sessions,
                        graph.connection,
                        graph.index,
                        discover = graph.discovery::find,
                    )
                }
            }
        setContent {
            val viewModel: JellyfinViewModel = viewModel(factory = factory)
            val state by viewModel.state.collectAsStateWithLifecycle()
            // The console page open over its home, if any. Back closes it; leaving SignedIn closes it too.
            var page by rememberSaveable { mutableStateOf<ConsolePage?>(null) }
            // Counts openings, so each one gets a fresh page view model.
            var opened by rememberSaveable { mutableIntStateOf(0) }
            val signedIn = state is UiState.SignedIn
            LaunchedEffect(signedIn) { if (!signedIn) page = null }
            val openPage = page.takeIf { signedIn }
            BackHandler(enabled = openPage != null) { page = null }
            val actions =
                Actions(
                    onAddress = viewModel::onAddress,
                    onConnect = viewModel::connect,
                    onUsername = viewModel::onUsername,
                    onPassword = viewModel::onPassword,
                    onSignIn = viewModel::signInWithPassword,
                    onQuickConnect = viewModel::startQuickConnect,
                    onCancelQuickConnect = viewModel::cancelQuickConnect,
                    onChangeServer = viewModel::changeServer,
                    onRefresh = viewModel::refresh,
                    onAskSignOut = { viewModel.confirmSignOut(true) },
                    onCancelSignOut = { viewModel.confirmSignOut(false) },
                    onSignOut = viewModel::signOut,
                    onDiscover = { withLocalNetwork(viewModel::discoverServers) },
                    onChooseServer = { server ->
                        viewModel.onAddress(server.address)
                        viewModel.connect()
                    },
                    onOpenPage = {
                        opened++
                        page = it
                    },
                )
            JellyfinTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (openPage) {
                        null -> JellyfinRoot(state, actions)
                        ConsolePage.ApproveCode -> ApproveCodeRoute(graph, opened, onBack = { page = null })
                        ConsolePage.Languages -> LanguagesRoute(graph, opened, onBack = { page = null })
                    }
                }
            }
        }
    }

    /** The approval page with its own view model, kept while the page is open and new for each opening. */
    @Composable
    private fun ApproveCodeRoute(
        graph: AppGraph,
        opening: Int,
        onBack: () -> Unit,
    ) {
        val approve: ApproveCodeViewModel =
            viewModel(
                key = "approve-code-$opening",
                factory = viewModelFactory { initializer { ApproveCodeViewModel(graph.connection) } },
            )
        val state by approve.state.collectAsStateWithLifecycle()
        ApproveCodeScreen(state, ApproveActions(onCode = approve::onCode, onApprove = approve::approve, onBack = onBack))
    }

    /** The languages page with its own view model, which reads the user's configuration when it opens. */
    @Composable
    private fun LanguagesRoute(
        graph: AppGraph,
        opening: Int,
        onBack: () -> Unit,
    ) {
        val languages: LanguagesViewModel =
            viewModel(
                key = "languages-$opening",
                factory = viewModelFactory { initializer { LanguagesViewModel(graph.connection) } },
            )
        val state by languages.state.collectAsStateWithLifecycle()
        LanguagesScreen(
            state,
            LanguagesActions(
                onAudio = languages::setAudio,
                onSubtitles = languages::setSubtitles,
                onSubtitleMode = languages::setSubtitleMode,
                onRetry = languages::load,
                onBack = onBack,
            ),
        )
    }

    private companion object {
        /** Android 17, where reaching the local network became a permission of its own. */
        const val LOCAL_NETWORK_SDK = 37
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
