package com.cooper.binge.companion.jellyfin.notifications

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.auth.ServerIdentity
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.DiscardLogger
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.homelab.core.notifications.CheckResult
import com.cooper.homelab.core.notifications.FeedCursorKey
import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** One check of the activity log, against the fake server. */
@RunWith(RobolectricTestRunner::class)
class ActivityCheckerTest {
    private val jellyfin = FakeJellyfin().apply { administrator = true }
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Ephyra", "Test", "1.0", Dispatchers.IO, DiscardLogger)
    private val prefsStore = InMemoryDataStore()
    private val prefs = NotificationPrefs(prefsStore)
    private val cursors = testFeedCursors()
    private val serverIdentity = ServerIdentity(InMemoryDataStore())
    private val notifier = FakeNotifier()
    private val checker = ActivityChecker(connection, cursors, LegacyActivityCursor(prefs), serverIdentity, notifier, DiscardLogger)

    private suspend fun cursor(userId: String) = cursors.get(FeedCursorKey(serverIdentity.currentServerId(), userId, "activity"))

    @After
    fun close() {
        connection.close()
        jellyfin.close()
    }

    private fun signIn() = runBlocking { sessions.save(jellyfin.session()) }

    private fun logRequests() = jellyfin.requests.count { it.url.encodedPath == "/System/ActivityLog/Entries" }

    @Test
    fun `the first check seeds the cursor and says nothing about the backlog`() =
        runBlocking {
            signIn()
            jellyfin.activityEntries = listOf(activityJson(3, "AuthenticationFailed"), activityJson(2, "UserLockedOut"))
            assertEquals(CheckResult.Ok, checker.check())
            assertTrue(notifier.summaries.isEmpty())
            assertEquals("3", cursor("user-1"))
        }

    @Test
    fun `later checks group what is new into one notification`() =
        runBlocking {
            signIn()
            jellyfin.activityEntries = listOf(activityJson(2, "SessionStarted"))
            checker.check()
            jellyfin.activityEntries =
                listOf(
                    activityJson(9, "TaskFailed", "Error"),
                    activityJson(8, "PluginError", "Warning"),
                    activityJson(7, "UserLockedOut", "Warning"),
                    activityJson(6, "AuthenticationFailed", "Error"),
                    activityJson(5, "AuthenticationFailed"),
                    activityJson(4, "SessionStarted"),
                    activityJson(2, "AuthenticationFailed"),
                )
            assertEquals(CheckResult.Ok, checker.check())
            assertEquals(listOf(ActivitySummary(failedSignIns = 2, lockouts = 1, problems = 2)), notifier.summaries)
            assertEquals("9", cursor("user-1"))
            // Read again with nothing new: no second notification.
            checker.check()
            assertEquals(1, notifier.summaries.size)
        }

    @Test
    fun `a log with only ordinary entries posts nothing`() =
        runBlocking {
            signIn()
            jellyfin.activityEntries = listOf(activityJson(1, "SessionStarted"))
            checker.check()
            jellyfin.activityEntries = listOf(activityJson(2, "VideoPlayback"), activityJson(1, "SessionStarted"))
            checker.check()
            assertTrue(notifier.summaries.isEmpty())
        }

    @Test
    fun `another account starts from its own place in the log`() =
        runBlocking {
            signIn()
            jellyfin.activityEntries = listOf(activityJson(5, "SessionStarted"))
            checker.check()
            sessions.save(jellyfin.session().copy(userId = "user-2"))
            jellyfin.activityEntries = listOf(activityJson(6, "AuthenticationFailed"), activityJson(5, "SessionStarted"))
            checker.check()
            assertTrue(notifier.summaries.isEmpty())
            assertEquals("6", cursor("user-2"))
        }

    @Test
    fun `a user who is not an administrator is told so and the log is not read`() =
        runBlocking {
            jellyfin.administrator = false
            signIn()
            assertEquals(CheckResult.StopPolling, checker.check())
            assertEquals(0, logRequests())
        }

    @Test
    fun `a refused sign-in is an auth failure`() =
        runBlocking {
            signIn()
            jellyfin.status["/Users/Me"] = 401
            assertEquals(CheckResult.AuthFailure, checker.check())
        }

    @Test
    fun `a server error, or one that cannot be reached, is transient`() =
        runBlocking {
            signIn()
            jellyfin.status["/System/ActivityLog/Entries"] = 500
            assertEquals(CheckResult.TransientFailure, checker.check())
            jellyfin.status.clear()
            jellyfin.dropped += "/Users/Me"
            assertEquals(CheckResult.TransientFailure, checker.check())
        }

    @Test
    fun `while nothing could be shown it makes no request`() =
        runBlocking {
            signIn()
            notifier.mayPost = false
            assertEquals(CheckResult.Ok, checker.check())
            assertEquals(0, jellyfin.requests.size)
        }

    @Test
    fun `signed out, it makes no request`() =
        runBlocking {
            assertEquals(CheckResult.Ok, checker.check())
            assertEquals(0, jellyfin.requests.size)
        }

    @Test
    fun `the newest entries are read with no date, and the token stays in the header`() =
        runBlocking {
            signIn()
            jellyfin.activityEntries = listOf(activityJson(2, "SessionStarted", date = "2026-11-01T10:00:00Z"))
            checker.check()
            checker.check()
            val read = jellyfin.requests.last { it.url.encodedPath == "/System/ActivityLog/Entries" }
            assertEquals("100", read.url.queryParameter("limit"))
            assertFalse(read.url.toString().contains("token-1"))
            assertNotNull(read.headers["Authorization"])
        }

    @Test
    fun `the cursor the released version kept carries over and nothing is announced twice`() =
        runBlocking {
            signIn()
            prefsStore.writeReleasedCursor("server-1/user-1", id = 2, dateMillis = 1_000L)
            jellyfin.activityEntries = listOf(activityJson(3, "AuthenticationFailed"), activityJson(2, "SessionStarted"))

            assertEquals(CheckResult.Ok, checker.check())

            // Entry 3 is past the old cursor, so it is announced rather than seeded away.
            assertEquals(listOf(ActivitySummary(failedSignIns = 1)), notifier.summaries)
            assertEquals("3", cursor("user-1"))
            checker.check()
            assertEquals(1, notifier.summaries.size)
        }

    @Test
    fun `a released cursor of another account is not used`() =
        runBlocking {
            signIn()
            prefsStore.writeReleasedCursor("server-1/someone-else", id = 1, dateMillis = null)
            jellyfin.activityEntries = listOf(activityJson(3, "AuthenticationFailed"))

            checker.check()

            assertTrue(notifier.summaries.isEmpty())
            assertEquals("3", cursor("user-1"))
        }

    @Test
    fun `the released cursor never overwrites one the shared store already holds`() =
        runBlocking {
            signIn()
            jellyfin.activityEntries = listOf(activityJson(5, "SessionStarted"))
            checker.check()
            prefsStore.writeReleasedCursor("server-1/user-1", id = 1, dateMillis = null)
            jellyfin.activityEntries = listOf(activityJson(6, "VideoPlayback"), activityJson(5, "SessionStarted"))

            checker.check()

            assertEquals("6", cursor("user-1"))
        }
}
