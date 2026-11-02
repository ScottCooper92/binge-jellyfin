package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.BackupCreation
import com.cooper.binge.companion.jellyfin.server.BackupOptions
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupsViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()
    private val listening = CoroutineScope(Dispatchers.Unconfined)

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() {
        listening.cancel()
        teardown.close(jellyfin)
        connection.saveScope.cancel()
    }

    private fun viewModel() = made(BackupsViewModel(connection, pollMillis = 10, downWaitMillis = 100, backWaitMillis = 600))

    private fun BackupsViewModel.settled(check: (BackupsUiState) -> Boolean = { it !is BackupsUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun BackupsViewModel.ready(check: (BackupsUiState.Ready) -> Boolean = { true }) =
        settled { it is BackupsUiState.Ready && check(it) } as BackupsUiState.Ready

    @Test
    fun `lists the backups newest first, each with its release and what it holds`() {
        val ready = viewModel().ready()
        assertEquals(
            listOf("jellyfin-backup-20261030090000.zip", "jellyfin-backup-20261020090000.zip"),
            ready.backups.map { it.fileName },
        )
        assertEquals("10.11.6.0", ready.backups.first().serverVersion)
        assertEquals(BackupOptions(database = true, metadata = true), ready.backups.last().options)
        assertEquals(BackupCreation.Idle, ready.creation)
    }

    @Test
    fun `a failed read says so, and can be tried again`() {
        jellyfin.status["/Backup"] = 500
        val vm = viewModel()
        assertEquals(ConsoleFailure.Refused, (vm.settled() as BackupsUiState.Failed).failure)
        jellyfin.status.clear()
        vm.load()
        vm.ready()
    }

    @Test
    fun `a backup is made with what the sheet chose, shown when it is done, and put away when dismissed`() {
        val vm = viewModel()
        vm.ready()
        vm.askCreate(true)
        assertEquals(BackupOptions(), vm.ready().choosing)
        vm.choose(BackupOptions(database = true, metadata = true, trickplay = true))
        vm.create()
        val made = vm.ready { it.creation is BackupCreation.Created }
        assertNull(made.choosing)
        assertEquals("jellyfin-backup-20261102100000.zip", (made.creation as BackupCreation.Created).backup.fileName)
        val body = jellyfin.lastBody("/Backup/Create")
        assertEquals("true", body.getValue("Database").toString())
        assertEquals("true", body.getValue("Metadata").toString())
        assertEquals("false", body.getValue("Subtitles").toString())
        assertEquals("true", body.getValue("Trickplay").toString())
        // The list is read again, so the new backup is in it, first.
        assertEquals(
            "jellyfin-backup-20261102100000.zip",
            vm
                .ready { it.backups.size == 3 }
                .backups
                .first()
                .fileName,
        )
        vm.dismissCreation()
        assertEquals(BackupCreation.Idle, vm.ready { it.creation == BackupCreation.Idle }.creation)
    }

    @Test
    fun `a backup the server refuses is a failure to dismiss, and the list is as it was`() {
        jellyfin.status["/Backup/Create"] = 500
        val vm = viewModel()
        vm.ready()
        vm.askCreate(true)
        vm.create()
        val failed = vm.ready { it.creation is BackupCreation.Failed }
        assertEquals(2, failed.backups.size)
        vm.dismissCreation()
        vm.ready { it.creation == BackupCreation.Idle }
    }

    @Test
    fun `a backup the server refuses with a reason carries it`() {
        jellyfin.status["/Backup/Create"] = 500
        jellyfin.statusBody["/Backup/Create"] = """{"title":"The backup directory does not have at least '5GiB' free space"}"""
        val vm = viewModel()
        vm.ready()
        vm.askCreate(true)
        vm.create()
        val failed = vm.ready { it.creation is BackupCreation.Failed }.creation as BackupCreation.Failed
        assertEquals("The backup directory does not have at least '5GiB' free space", (failed.error as JellyfinHttpException).reason)
    }

    @Test
    fun `a backup refused with Jellyfin's boilerplate body carries no reason`() {
        jellyfin.status["/Backup/Create"] = 500
        jellyfin.statusBody["/Backup/Create"] = "Error processing request."
        val vm = viewModel()
        vm.ready()
        vm.askCreate(true)
        vm.create()
        val failed = vm.ready { it.creation is BackupCreation.Failed }.creation as BackupCreation.Failed
        assertEquals(null, (failed.error as JellyfinHttpException).reason)
    }

    @Test
    fun `a backup still being made is not started twice, and cannot be dismissed`() {
        jellyfin.backupCreateDelayMillis = 300
        val vm = viewModel()
        vm.ready()
        vm.askCreate(true)
        vm.create()
        assertTrue(vm.ready { it.creating }.creating)
        vm.askCreate(true)
        assertNull(vm.ready().choosing)
        vm.dismissCreation()
        assertTrue(vm.ready().creating)
        vm.ready { it.creation is BackupCreation.Created }
        assertEquals(1, jellyfin.hits("/Backup/Create"))
    }

    @Test
    fun `leaving the page mid-backup does not cancel it, and the result is there when the page is opened again`() {
        jellyfin.backupCreateDelayMillis = 400
        val first = viewModel()
        first.ready()
        first.askCreate(true)
        first.create()
        assertTrue(first.ready { it.creating }.creating)
        // The page is closed: its view model and its scope go, as the console does when Back leaves it.
        first.viewModelScope.cancel()
        val second = viewModel()
        val back = second.ready { it.creation is BackupCreation.Created }
        assertEquals("jellyfin-backup-20261102100000.zip", (back.creation as BackupCreation.Created).backup.fileName)
        assertEquals(3, second.ready { it.backups.size == 3 }.backups.size)
        assertEquals(1, jellyfin.hits("/Backup/Create"))
    }

    @Test
    fun `a restore asks first, sends the file name, follows the server down and back, and reads the list again`() {
        val vm = viewModel()
        val target = vm.ready().backups.last()
        vm.open(target)
        vm.askRestore(target)
        assertEquals(target, vm.ready().confirmingRestore)
        vm.askRestore(null)
        vm.askRestore(target)
        vm.restore()
        val restoring = vm.ready { it.restore == Restore.Restoring }
        assertNull(restoring.confirmingRestore)
        assertNull(restoring.open)
        val done = vm.ready { it.restore == Restore.Restored }
        assertEquals(2, done.backups.size)
        assertEquals(
            "jellyfin-backup-20261020090000.zip",
            jellyfin
                .lastBody("/Backup/Restore")
                .getValue("ArchiveFileName")
                .toString()
                .trim('"'),
        )
        assertEquals(1, jellyfin.hits("/Backup/Restore"))
        vm.dismissRestore()
        assertNull(vm.ready { it.restore == null }.restore)
    }

    @Test
    fun `a restore of a file the server no longer has is a failure`() {
        val vm = viewModel()
        val target = vm.ready().backups.first()
        jellyfin.backups = emptyList()
        vm.askRestore(target)
        vm.restore()
        vm.ready { it.restore == Restore.Failed }
    }

    @Test
    fun `a server that does not come back in time says so`() {
        jellyfin.restartDownPolls = 1000
        val vm = viewModel()
        val target = vm.ready().backups.first()
        vm.askRestore(target)
        vm.restore()
        vm.ready { it.restore == Restore.NotBack }
    }

    @Test
    fun `a restore that ends this app's session is found out on the first call after the restart, and the console is told`() {
        jellyfin.restoreEndsSession = true
        val refused = refusals()
        val vm = viewModel()
        val target = vm.ready().backups.first()
        vm.askRestore(target)
        vm.restore()
        vm.ready { it.restore == Restore.Failed }
        runBlocking { withTimeout(TIMEOUT_MILLIS) { while (refused.isEmpty()) delay(POLL_MILLIS) } }
        assertEquals(1, refused.size)
    }

    @Test
    fun `no restore starts while one is going, or without a confirm`() {
        val vm = viewModel()
        vm.ready()
        vm.restore()
        assertEquals(0, jellyfin.hits("/Backup/Restore"))
        assertFalse(vm.ready().restore != null)
    }

    /** What [Connection.refused] reports from here on. */
    private fun refusals(): MutableList<Unit> {
        val seen = CopyOnWriteArrayList<Unit>()
        listening.launch { connection.refused.collect { seen += it } }
        return seen
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L
    }
}
