package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.ActivityFilter
import com.cooper.binge.companion.jellyfin.server.ActivityTimePreset
import com.cooper.binge.companion.jellyfin.server.ActivityWho
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.Severity
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ActivityFiltersViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()
    private val now = Instant.parse("2026-11-09T15:30:00Z")

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun vm() = made(ActivityViewModel(connection, pageSize = 2, now = { now }, zone = ZoneOffset.UTC))

    private fun ActivityViewModel.ready(check: (ActivityUiState.Ready) -> Boolean = { true }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it is ActivityUiState.Ready && check(it) } } } as ActivityUiState.Ready

    private fun entries() = jellyfin.requests.filter { it.url.encodedPath == "/System/ActivityLog/Entries" }

    private fun ActivityViewModel.opened(): ActivityViewModel {
        ready()
        openFilters()
        return this
    }

    @Test
    fun `the first read carries no filter`() {
        vm().ready()
        assertEquals(setOf("startIndex", "limit"), entries().single().url.queryParameterNames)
    }

    @Test
    fun `an edit changes the draft and the list is not read again until Show results`() {
        val vm = vm().opened()
        vm.preset(ActivityTimePreset.Last7Days)
        vm.edit { copy(severity = Severity.Warning) }
        assertEquals(Severity.Warning, vm.filters.value.draft.severity)
        assertTrue(vm.filters.value.applied.isEmpty)
        assertEquals(1, entries().size)
    }

    @Test
    fun `Show results applies the draft, restarts from the first page and sends every filter`() {
        val vm = vm().opened()
        vm.preset(ActivityTimePreset.Last7Days)
        vm.edit { copy(who = ActivityWho.Users, severity = Severity.Error, type = "VideoPlayback") }
        vm.apply()
        vm.ready()
        val query = entries().last().url
        assertEquals("0", query.queryParameter("startIndex"))
        assertEquals("2026-11-02T15:30:00Z", query.queryParameter("minDate"))
        assertEquals("true", query.queryParameter("hasUserId"))
        assertEquals("Error", query.queryParameter("severity"))
        assertEquals("VideoPlayback", query.queryParameter("type"))
        assertFalse(vm.filters.value.open)
        assertEquals(vm.filters.value.draft, vm.filters.value.applied)
    }

    @Test
    fun `the next page carries the same filter`() {
        val vm = vm().opened()
        vm.edit { copy(who = ActivityWho.System) }
        vm.apply()
        vm.ready()
        vm.loadMore()
        vm.ready { it.entries.size == 4 }
        val paged = entries().last().url
        assertEquals("2", paged.queryParameter("startIndex"))
        assertEquals("false", paged.queryParameter("hasUserId"))
    }

    @Test
    fun `closing the sheet drops what was edited`() {
        val vm = vm().opened()
        vm.edit { copy(type = "SessionStarted") }
        vm.closeFilters()
        assertNull(vm.filters.value.draft.type)
        assertFalse(vm.filters.value.open)
    }

    @Test
    fun `a chip clears its own filter alone and reads the list again`() {
        val vm = vm().opened()
        vm.edit { copy(severity = Severity.Warning, type = "VideoPlayback") }
        vm.apply()
        vm.ready()
        vm.clear(ActivityFilterKind.Severity)
        vm.ready()
        assertEquals(ActivityFilter(type = "VideoPlayback"), vm.filters.value.applied)
        val query = entries().last().url
        assertNull(query.queryParameter("severity"))
        assertEquals("VideoPlayback", query.queryParameter("type"))
    }

    @Test
    fun `clearing the sheet's draft leaves the list as it is until Show results`() {
        val vm = vm().opened()
        vm.edit { copy(severity = Severity.Warning) }
        vm.apply()
        vm.ready()
        vm.openFilters()
        vm.clearDraft()
        assertTrue(vm.filters.value.draft.isEmpty)
        assertEquals(Severity.Warning, vm.filters.value.applied.severity)
        assertEquals(2, entries().size)
        vm.apply()
        vm.ready()
        assertTrue(vm.filters.value.applied.isEmpty)
    }

    @Test
    fun `clearing all shows the whole log`() {
        val vm = vm().opened()
        vm.range(Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(), Instant.parse("2026-11-03T23:59:59Z").toEpochMilli())
        vm.apply()
        vm.ready()
        assertEquals("2026-11-03T23:59:59Z", entries().last().url.queryParameter("maxDate"))
        vm.clearAll()
        vm.ready()
        assertTrue(vm.filters.value.applied.isEmpty)
        assertEquals(setOf("startIndex", "limit"), entries().last().url.queryParameterNames)
    }

    @Test
    fun `a named user is sent as the username, with no split`() {
        val vm = vm().opened()
        vm.edit { copy(who = ActivityWho.Users, username = "alice") }
        vm.apply()
        vm.ready()
        val query = entries().last().url
        assertEquals("alice", query.queryParameter("username"))
        assertNull(query.queryParameter("hasUserId"))
    }

    @Test
    fun `the users the who filter picks from are read once and kept`() {
        val vm = vm().opened()
        vm.loadUsers()
        val names =
            (
                runBlocking {
                    withTimeout(TIMEOUT_MILLIS) { vm.filters.first { it.users is ActivityUsers.Ready } }
                }.users as ActivityUsers.Ready
            ).names
        assertTrue(names.isNotEmpty())
        vm.loadUsers()
        assertEquals(1, jellyfin.hits("/Users"))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
