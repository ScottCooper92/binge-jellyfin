package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.Severity
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ActivityViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        jellyfin.close()
    }

    private fun ActivityViewModel.settled(check: (ActivityUiState) -> Boolean = { it !is ActivityUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun ActivityViewModel.ready(check: (ActivityUiState.Ready) -> Boolean = { true }) =
        settled { it is ActivityUiState.Ready && check(it) } as ActivityUiState.Ready

    @Test
    fun `reads the newest page, then the next, until the end`() {
        val vm = ActivityViewModel(connection, pageSize = 2)
        val first = vm.ready()
        assertEquals(listOf(5L, 4L), first.entries.map { it.id })
        assertEquals("IP address: 10.0.0.1", first.entries.first().overview)
        assertEquals(Severity.Information, first.entries.first().severity)
        vm.loadMore()
        assertEquals(listOf(5L, 4L, 3L, 2L), vm.ready { it.entries.size == 4 }.entries.map { it.id })
        vm.loadMore()
        val last = vm.ready { it.entries.size == 5 }
        assertFalse(last.hasMore)
        vm.loadMore()
        assertEquals(3, jellyfin.hits("/System/ActivityLog/Entries"))
    }

    @Test
    fun `entries that arrived meanwhile shift the pages, and the repeats are dropped`() {
        val vm = ActivityViewModel(connection, pageSize = 2)
        vm.ready()
        jellyfin.activityEntries = listOf("""{"Id":6,"Name":"New","Type":"UserCreated","Severity":"Warning"}""") + jellyfin.activityEntries
        vm.loadMore()
        // The second page now starts with 4 again; it isn't added twice.
        assertEquals(listOf(5L, 4L, 3L), vm.ready { it.entries.size == 3 }.entries.map { it.id })
    }

    @Test
    fun `a page that adds nothing new ends the list rather than asking for it again`() {
        val vm = ActivityViewModel(connection, pageSize = 2)
        vm.ready()
        jellyfin.activityEntries = jellyfin.activityEntries.take(4)
        vm.loadMore()
        assertFalse(vm.ready { !it.loadingMore }.hasMore)
    }

    @Test
    fun `pulling down reads the newest again, and a failure keeps what's showing`() {
        val vm = ActivityViewModel(connection, pageSize = 2)
        vm.ready()
        jellyfin.activityEntries = listOf("""{"Id":6,"Name":"New","Type":"UserCreated"}""") + jellyfin.activityEntries
        vm.refresh()
        assertEquals(listOf(6L, 5L), vm.ready { it.entries.first().id == 6L }.entries.map { it.id })
        jellyfin.status["/System/ActivityLog/Entries"] = 500
        vm.refresh()
        val failed = vm.ready { it.moreFailed != null }
        assertEquals(listOf(6L, 5L), failed.entries.map { it.id })
        assertFalse(failed.refreshing)
        // The next page can't come either, and says so.
        vm.loadMore()
        assertEquals(ConsoleFailure.Refused, vm.ready { !it.loadingMore }.moreFailed)
    }

    @Test
    fun `Try again after a failed refresh re-reads the newest when everything fit on one page`() {
        val vm = ActivityViewModel(connection)
        assertFalse(vm.ready().hasMore)
        jellyfin.status["/System/ActivityLog/Entries"] = 500
        vm.refresh()
        assertEquals(ConsoleFailure.Refused.javaClass, vm.ready { it.moreFailed != null && !it.refreshing }.moreFailed?.javaClass)
        jellyfin.status.remove("/System/ActivityLog/Entries")
        vm.loadMore()
        assertEquals(null, vm.ready { !it.refreshing && it.moreFailed == null }.moreFailed)
        assertEquals(3, jellyfin.hits("/System/ActivityLog/Entries"))
    }

    @Test
    fun `a critical entry reads as an error`() {
        jellyfin.activityEntries = listOf("""{"Id":9,"Name":"Bad","Type":"X","Severity":"Critical"}""")
        val vm = ActivityViewModel(connection)
        assertEquals(
            Severity.Error,
            vm
                .ready()
                .entries
                .single()
                .severity,
        )
    }

    @Test
    fun `a page that can't read says why, and Try again reads again`() {
        jellyfin.status["/System/ActivityLog/Entries"] = 403
        val vm = ActivityViewModel(connection)
        assertEquals(ActivityUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/System/ActivityLog/Entries")
        vm.load()
        assertEquals(5, vm.ready().entries.size)
    }

    @Test
    fun `entries group by their day in the device's zone, undated ones last`() {
        val tenPm = 1_793_570_400_000L // 31 Oct 2026, 22:00 UTC
        assertEquals(day(tenPm, ZoneOffset.UTC), day(tenPm - 3_600_000, ZoneOffset.UTC))
        // Two hours ahead, 22:00 UTC is already the next day.
        assertFalse(day(tenPm, ZoneOffset.ofHours(2)) == day(tenPm - 3 * 3_600_000, ZoneOffset.ofHours(2)))
        assertEquals(UNDATED, day(null, ZoneOffset.UTC))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
