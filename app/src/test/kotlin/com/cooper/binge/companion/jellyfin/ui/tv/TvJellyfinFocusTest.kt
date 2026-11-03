package com.cooper.binge.companion.jellyfin.ui.tv

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.testing.createTakeDownKeyboardAndroidComposeRule
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.flags.Flags
import com.cooper.binge.companion.jellyfin.flags.LocalFlags
import com.cooper.binge.companion.jellyfin.server.DiscoveredServer
import com.cooper.binge.companion.jellyfin.server.PublicServerInfo
import com.cooper.binge.companion.jellyfin.theme.JellyfinTvTheme
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.SetupError
import com.cooper.binge.companion.jellyfin.ui.UiState
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The television's screens under a real D-pad. Robolectric hosts Compose on the JVM, and the television qualifier is load-bearing:
 * a 2D focus search on a handset's screen behaves nothing like a panel's. The rule is the design system's keyboard one, so the
 * keys go through the focus system and no touch ever lands.
 *
 * Every walk starts from where the page lands the remote and moves one press at a time, so a failure names the control that
 * stopped answering rather than the last one that did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvJellyfinFocusTest {
    @get:Rule
    val compose = createTakeDownKeyboardAndroidComposeRule<ComponentActivity>()

    private var state: UiState by mutableStateOf(UiState.Loading)
    private var promoted = false
    private val calls = mutableListOf<String>()
    private val actions =
        Actions(
            onAddress = { calls += "address:$it" },
            onConnect = { calls += "connect" },
            onUsername = { calls += "username:$it" },
            onPassword = { calls += "password:$it" },
            onSignIn = { calls += "signIn" },
            onQuickConnect = { calls += "quickConnect" },
            onCancelQuickConnect = { calls += "cancelQuickConnect" },
            onChangeServer = { calls += "changeServer" },
            onRefresh = { calls += "refresh" },
            onAskSignOut = { calls += "askSignOut" },
            onCancelSignOut = { calls += "cancelSignOut" },
            onSignOut = { calls += "signOut" },
            onDiscover = { calls += "discover" },
            onChooseServer = { calls += "choose:${it.name}" },
        )

    private val lan = "http://192.168.1.20:8096"
    private val server = PublicServerInfo(serverName = "Living room", version = "10.11.6", serverId = "a1", baseUrl = lan.toHttpUrl())
    private val living = DiscoveredServer(id = "a1", name = "Living room", address = lan)
    private val cabin = DiscoveredServer(id = "b2", name = "Cabin", address = "https://jellyfin.example.com")
    private val signIn = UiState.SignIn(baseUrl = lan, server = server, username = "sam")
    private val session =
        JellyfinSession(lan, "Living room", "a1", "10.11.6", userId = "u1", userName = "sam", token = "not-a-token")

    private fun show(initial: UiState) {
        state = initial
        compose.setContent {
            CompositionLocalProvider(LocalFlags provides if (promoted) Flags.AllOn else Flags()) {
                JellyfinTvTheme { TvJellyfinRoot(state, actions) }
            }
        }
        compose.waitForIdle()
    }

    private fun text(id: Int) = compose.activity.getString(id)

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun down() = press(Key.DirectionDown)

    private fun up() = press(Key.DirectionUp)

    private fun ok() = press(Key.DirectionCenter)

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun on(id: Int) = compose.onNodeWithText(text(id))

    /**
     * A text field's frame, which the remote lands on and which names the field; the input inside edits only on OK and names the
     * field too, so the frame is the one that cannot take text.
     */
    private fun field(label: Int) = compose.onNode(hasContentDescription(text(label)) and isFocusable() and !hasSetTextAction())

    // The server step.

    @Test
    fun `the server step lands on the address field, and asks the network for servers once`() {
        show(UiState.EnterServer())
        field(R.string.setup_server_field).assertIsFocused()
        assertEquals(1, calls.count { it == "discover" })
    }

    @Test
    fun `Down from the address goes to Connect and then through the servers that answered, and Up comes back`() {
        show(UiState.EnterServer(discovered = listOf(living, cabin)))
        field(R.string.setup_server_field).assertIsFocused()
        down()
        on(R.string.setup_connect).assertIsFocused()
        down()
        compose.onNodeWithText("Living room").assertIsFocused()
        down()
        compose.onNodeWithText("Cabin").assertIsFocused()
        up()
        up()
        on(R.string.setup_connect).assertIsFocused()
    }

    @Test
    fun `OK on Connect connects, and OK on a found server picks it`() {
        show(UiState.EnterServer(address = "192.168.1.20:8096", discovered = listOf(living, cabin)))
        down()
        ok()
        assertEquals(listOf("discover", "connect"), calls)
        down()
        down()
        ok()
        assertEquals("choose:Cabin", calls.last())
    }

    @Test
    fun `servers that arrive while the remote is on Connect do not move it`() {
        show(UiState.EnterServer(discovering = true))
        down()
        on(R.string.setup_connect).assertIsFocused()
        state = UiState.EnterServer(discovered = listOf(living, cabin))
        compose.waitForIdle()
        compose.onNodeWithText("Living room").assertIsDisplayed()
        on(R.string.setup_connect).assertIsFocused()
    }

    @Test
    fun `an error under the address does not take the remote off the field`() {
        show(UiState.EnterServer(address = "192.168.1.99:8096"))
        field(R.string.setup_server_field).assertIsFocused()
        state = UiState.EnterServer(address = "192.168.1.99:8096", error = SetupError.Unreachable)
        compose.waitForIdle()
        field(R.string.setup_server_field).assertIsFocused()
    }

    // Sign-in: Quick Connect first.

    @Test
    fun `sign-in lands on Use password, and asks for a code when there is none`() {
        show(signIn)
        on(R.string.tv_use_password).assertIsFocused()
        assertEquals(1, calls.count { it == "quickConnect" })
    }

    @Test
    fun `with a code showing, the remote rests on Use password and Change server is one press below`() {
        show(signIn.copy(quickConnectCode = "482913"))
        compose.onNodeWithText("482913").assertIsDisplayed()
        on(R.string.tv_use_password).assertIsFocused()
        down()
        on(R.string.tv_change_server).assertIsFocused()
        ok()
        assertEquals("changeServer", calls.last())
        // A code that is showing is not asked for again.
        assertEquals(0, calls.count { it == "quickConnect" })
    }

    @Test
    fun `a Quick Connect failure moves the remote to Try again, and the code arriving moves it back`() {
        show(signIn.copy(quickConnectError = SetupError.QuickConnectExpired))
        on(R.string.tv_try_again).assertIsFocused()
        // A failure waits for Try again: it is not asked for in a loop.
        assertEquals(0, calls.count { it == "quickConnect" })
        down()
        on(R.string.tv_use_password).assertIsFocused()
        up()
        ok()
        assertEquals("quickConnect", calls.last())
        state = signIn.copy(quickConnectCode = "482913")
        compose.waitForIdle()
        compose.onAllNodesWithText(text(R.string.tv_try_again)).assertCountEquals(0)
        on(R.string.tv_use_password).assertIsFocused()
    }

    // Sign-in: the password.

    @Test
    fun `Use password cancels the code, lands on the user name, and walks the form to Quick Connect and Change server`() {
        show(signIn.copy(quickConnectCode = "482913"))
        ok()
        assertEquals("cancelQuickConnect", calls.last())
        field(R.string.setup_username).assertIsFocused()
        field(R.string.setup_password).assertIsNotFocused()
        down()
        field(R.string.setup_password).assertIsFocused()
        down()
        on(R.string.setup_sign_in).assertIsFocused()
        down()
        on(R.string.setup_quick_connect).assertIsFocused()
        down()
        on(R.string.tv_change_server).assertIsFocused()
        up()
        up()
        ok()
        // Sign in is never disabled on TV: an empty form answers with its error, so the press always reaches the action.
        assertEquals("signIn", calls.last())
    }

    @Test
    fun `Quick Connect from the password form returns to the code, on the control that stays`() {
        show(signIn.copy(quickConnectCode = "482913"))
        ok()
        down()
        down()
        down()
        on(R.string.setup_quick_connect).assertIsFocused()
        ok()
        on(R.string.tv_use_password).assertIsFocused()
    }

    @Test
    fun `a wrong password leaves the remote where it was`() {
        show(signIn.copy(username = "sam", password = "hunter2", quickConnectCode = "482913"))
        ok()
        down()
        down()
        on(R.string.setup_sign_in).assertIsFocused()
        state = signIn.copy(username = "sam", password = "hunter2", passwordError = SetupError.WrongCredentials)
        compose.waitForIdle()
        on(R.string.setup_sign_in).assertIsFocused()
    }

    // Signed in.

    @Test
    fun `signed in lands on Sign out, and OK asks first`() {
        show(UiState.SignedIn(session = session))
        on(R.string.settings_sign_out).assertIsFocused()
        ok()
        assertEquals("askSignOut", calls.last())
    }

    @Test
    fun `with Binge behind its flag on, the page lands on Refresh and Sign out is below it`() {
        promoted = true
        show(UiState.SignedIn(session = session, titles = 1_284))
        on(R.string.settings_refresh).assertIsFocused()
        ok()
        assertEquals("refresh", calls.last())
        down()
        on(R.string.settings_sign_out).assertIsFocused()
    }

    @Test
    fun `the sign-out confirm opens on Cancel, so a stray OK cancels rather than signs out`() {
        show(UiState.SignedIn(session = session, confirmingSignOut = true))
        compose.onNodeWithText(text(R.string.sign_out_title)).assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsFocused()
        compose.onNode(isDialog()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(listOf("cancelSignOut"), calls.filter { it.endsWith("SignOut") })
    }

    @Test
    fun `Back on the sign-out confirm closes it without signing out`() {
        show(UiState.SignedIn(session = session, confirmingSignOut = true))
        // Back goes to the dialog's own window, which the Activity's dispatcher never sees.
        compose.runOnUiThread {
            val dialog = checkNotNull(ShadowDialog.getLatestDialog()) { "no dialog is showing" }
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        compose.waitForIdle()
        assertEquals(listOf("cancelSignOut"), calls.filter { it.endsWith("SignOut") })
    }
}
