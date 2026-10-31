package com.cooper.binge.companion.jellyfin

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.JellyfinRoot
import com.cooper.binge.companion.jellyfin.ui.JellyfinViewModel

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
                )
            JellyfinTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    JellyfinRoot(state, actions)
                }
            }
        }
    }

    private companion object {
        /** Android 17, where reaching the local network became a permission of its own. */
        const val LOCAL_NETWORK_SDK = 37
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
