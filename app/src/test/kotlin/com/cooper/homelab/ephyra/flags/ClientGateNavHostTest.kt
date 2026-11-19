package com.cooper.homelab.ephyra.flags

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.core.flags.FixedFeatureFlags
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.ephyra.ui.nav.EphyraNavHost
import com.cooper.homelab.ephyra.ui.nav.HomeRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The gate, driven through the real navigation host: a probe on the home reads [LocalClientEnabled] the way a client surface will,
 * and the remote flag and the user's own switch each change it. It uses the host's public entry points only, so it holds while the
 * host's insides change.
 */
@RunWith(RobolectricTestRunner::class)
class ClientGateNavHostTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun hostWith(
        flag: Boolean,
        prefs: ClientPrefs,
    ): PreferenceGate {
        val gate =
            FlagPreferenceGate(FixedFeatureFlags(Flags.of(ClientEnabled to flag)), ClientEnabled, prefs.enabled, prefs::setEnabled, scope)
        compose.setContent {
            val resolved by gate.resolved.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalClientEnabled provides (resolved ?: gate.fallback)) {
                val probe: @Composable () -> Unit = { Text(if (LocalClientEnabled.current) "client on" else "client off") }
                EphyraNavHost(
                    backStack = rememberNavBackStack(HomeRoute),
                    signedIn = false,
                    onBack = {},
                    signedOut = probe,
                    home = probe,
                    defaultPage = {},
                    page = {},
                )
            }
        }
        return gate
    }

    private fun prefs() = ClientPrefs(PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("client.preferences_pb") })

    @Test
    fun `the client is off while the flag is off, whatever the user chose`() {
        hostWith(flag = false, prefs())
        compose.onNodeWithText("client off").assertIsDisplayed()
    }

    @Test
    fun `the flag on shows the client, and the user's switch takes it away and brings it back`() {
        val gate = hostWith(flag = true, prefs())
        compose.waitForIdle()
        compose.onNodeWithText("client on").assertIsDisplayed()

        runBlocking { gate.setPreference(false) }
        compose.waitForIdle()
        compose.onNodeWithText("client off").assertIsDisplayed()

        runBlocking { gate.setPreference(true) }
        compose.waitForIdle()
        compose.onNodeWithText("client on").assertIsDisplayed()
    }
}
