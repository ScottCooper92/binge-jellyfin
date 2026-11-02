package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * A backup made or refused is a message over the page, not a row in it (#248): the list is where it was, the message says what
 * it said as a row, and dismissing it clears the creation. Snackbars do not show in a frame, so this is where it is pinned.
 */
@RunWith(RobolectricTestRunner::class)
class BackupsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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
    fun `a refusal with no reason says the free-space hint, in the message`() {
        show(ready.copy(creation = BackupCreation.Failed(JellyfinHttpException(500, "Backup/Create"))))

        compose.onNode(hasText("The backup wasn't made.", substring = true) and hasText("5 GiB free", substring = true)).assertExists()
        compose.onNodeWithText("Yesterday").assertExists()
    }

    @Test
    fun `a refusal that carries the server's reason says that reason`() {
        show(ready.copy(creation = BackupCreation.Failed(JellyfinHttpException(500, "Backup/Create", "The volume is full"))))

        compose.onNodeWithText("The backup wasn't made. The volume is full").assertExists()
    }

    @Test
    fun `a 401 never shows the server's words, only the free-space hint`() {
        show(ready.copy(creation = BackupCreation.Failed(JellyfinHttpException(401, "Backup/Create", "Invalid token"))))

        compose.onNode(hasText("5 GiB free", substring = true)).assertExists()
        compose.onNode(hasText("Invalid token", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `dismissing the message clears the creation`() {
        show(ready.copy(creation = BackupCreation.Failed(JellyfinHttpException(500, "Backup/Create"))))
        assertEquals(0, dismissed)

        compose.onNodeWithContentDescription("Dismiss").performClickWithIdle()

        assertTrue("the creation was cleared: $dismissed", dismissed >= 1)
    }

    private fun SemanticsNodeInteraction.performClickWithIdle() {
        performClick()
        compose.waitForIdle()
    }
}
