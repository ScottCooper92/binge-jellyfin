package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.BackupCreation
import com.cooper.binge.companion.jellyfin.server.BackupOptions
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import com.cooper.binge.companion.jellyfin.server.ServerBackup
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val NOW = 1_793_534_400_000L
private const val DAY = 86_400_000L

/**
 * A backup made is a snackbar message over the page, not a row in it (#248); one the server refused, or a restore it did not come
 * back from, is a message dialog, since it is a cause and a next step (#260). Neither shows in a frame, so this is where it is pinned.
 */
@RunWith(RobolectricTestRunner::class)
class BackupsScreenTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val made = ServerBackup("jellyfin-backup-20261030090000.zip", NOW - DAY, "10.11.6.0", BackupOptions())
    private val ready = BackupsUiState.Ready(listOf(made))
    private var dismissed = 0

    private fun show(state: BackupsUiState.Ready) {
        compose.setContent {
            JellyfinTheme {
                BackupsScreen(state, BackupsActions(onDismissCreation = { dismissed++ }), JellyfinVersion(10, 11, 6), NOW)
            }
        }
    }

    @Test
    fun `a backup made is said in a message, with the list still there, and no row for it`() {
        show(ready.copy(creation = BackupCreation.Created(made)))

        compose.onNodeWithText("Backup made. It is first in the list below.").assertExists()
        compose.onNodeWithText("Yesterday").assertExists()
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

    @Test
    fun `a refusal with no reason is a dialog with what happened, the free-space hint and OK`() {
        show(ready.copy(creation = BackupCreation.Failed(JellyfinHttpException(500, "Backup/Create"))))

        compose.onNodeWithText("The backup wasn't made").assertExists()
        compose.onNode(hasText("5 GiB free", substring = true)).assertExists()
        compose.onNodeWithText("OK").assertExists()
        compose.onNodeWithText("Yesterday").assertExists()
    }

    @Test
    fun `a refusal that carries the server's reason says that reason in the dialog`() {
        show(ready.copy(creation = BackupCreation.Failed(JellyfinHttpException(500, "Backup/Create", "The volume is full"))))

        compose.onNodeWithText("The backup wasn't made").assertExists()
        compose.onNodeWithText("The volume is full").assertExists()
    }

    @Test
    fun `a 401 never shows the server's words, only the free-space hint`() {
        show(ready.copy(creation = BackupCreation.Failed(JellyfinHttpException(401, "Backup/Create", "Invalid token"))))

        compose.onNode(hasText("5 GiB free", substring = true)).assertExists()
        compose.onNode(hasText("Invalid token", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `OK on the dialog clears the creation`() {
        show(ready.copy(creation = BackupCreation.Failed(JellyfinHttpException(500, "Backup/Create"))))
        assertEquals(0, dismissed)

        compose.onNodeWithText("OK").performClickWithIdle()

        assertEquals(1, dismissed)
    }

    @Test
    fun `a backup made still says so in a snackbar message that clears the creation`() {
        show(ready.copy(creation = BackupCreation.Created(made)))

        compose.onNodeWithText("Backup made. It is first in the list below.").assertExists()
        compose.onNodeWithText("OK").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dismiss").performClickWithIdle()

        assertTrue("the creation was cleared: $dismissed", dismissed >= 1)
    }

    @Test
    fun `a restore the server did not come back from is a dialog with a next step, and OK dismisses it`() {
        var restoreDismissed = 0
        compose.setContent {
            JellyfinTheme {
                BackupsScreen(
                    ready.copy(restore = Restore.NotBack),
                    BackupsActions(onDismissRestore = { restoreDismissed++ }),
                    JellyfinVersion(10, 11, 6),
                    NOW,
                )
            }
        }

        compose.onNodeWithText("The server didn't come back in time").assertExists()
        compose.onNodeWithText("Check it, then try again.").assertExists()
        compose.onNodeWithText("OK").performClickWithIdle()

        assertEquals(1, restoreDismissed)
    }

    @Test
    fun `a restore that failed outright stays a short snackbar message, with no dialog`() {
        show(ready.copy(restore = Restore.Failed))

        compose.onNodeWithText("The restore didn't go through.").assertExists()
        compose.onNodeWithText("OK").assertDoesNotExist()
    }

    private fun SemanticsNodeInteraction.performClickWithIdle() {
        performClick()
        compose.waitForIdle()
    }
}
