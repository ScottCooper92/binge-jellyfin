package com.cooper.binge.companion.jellyfin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as JellyfinApp).graph
        val factory = viewModelFactory { initializer { JellyfinViewModel(graph.sessions, graph.connection, graph.index) } }
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
                    onAskSignOut = viewModel::askSignOut,
                    onCancelSignOut = viewModel::cancelSignOut,
                    onSignOut = viewModel::signOut,
                )
            JellyfinTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    JellyfinRoot(state, actions)
                }
            }
        }
    }
}
