package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.server.BackupCreation
import com.cooper.binge.companion.jellyfin.server.BackupOptions
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ServerBackup
import com.cooper.binge.companion.jellyfin.server.backups
import com.cooper.binge.companion.jellyfin.server.createBackup
import com.cooper.binge.companion.jellyfin.server.restoreBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a restore has got to. */
enum class Restore { Restoring, Restored, NotBack, Failed }

sealed interface BackupsUiState {
    data object Loading : BackupsUiState

    data class Failed(
        val failure: ConsoleFailure,
    ) : BackupsUiState

    /**
     * The backups, newest first. [creation] is the backup being made, or the last one's result until it is dismissed.
     * [choosing] is the create sheet while it is open, with what it would hold; [open] is the backup whose sheet is open;
     * [confirmingRestore] the one a restore is being confirmed for; and [restore] where a restore has got to.
     */
    data class Ready(
        val backups: List<ServerBackup>,
        val creation: BackupCreation = BackupCreation.Idle,
        val choosing: BackupOptions? = null,
        val open: ServerBackup? = null,
        val confirmingRestore: ServerBackup? = null,
        val restore: Restore? = null,
        /** True while a pull reads again; the page stays as it is. [refreshFailed] says why a pull didn't. */
        val refreshing: Boolean = false,
        val refreshFailed: ConsoleFailure? = null,
    ) : BackupsUiState {
        val creating: Boolean get() = creation is BackupCreation.Creating
    }
}

/**
 * The server's backups: listed, made, and restored from. Admin only, on a server with backups (10.11). A backup is made on
 * the connection's own scope, not the page's, because the server keeps writing it whether or not this page is open: its
 * result is held on the connection and shown when the page is next open. A restore restarts the server and replaces its
 * database with the backup's, which can end this app's own session: the next call after the restart finds out, and the
 * console sends the user to sign in as it does for any refused token.
 */
class BackupsViewModel(
    private val connection: Connection,
    pollMillis: Long = RestartTiming.POLL_MILLIS,
    downWaitMillis: Long = RestartTiming.DOWN_WAIT_MILLIS,
    backWaitMillis: Long = RestartTiming.BACK_WAIT_MILLIS,
) : ViewModel() {
    private val timing = RestartTiming(pollMillis, downWaitMillis, backWaitMillis)
    private val _state = MutableStateFlow<BackupsUiState>(BackupsUiState.Loading)
    val state: StateFlow<BackupsUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            var seen: BackupCreation = BackupCreation.Idle
            connection.backupCreation.collect { creation ->
                update { copy(creation = creation) }
                if (creation is BackupCreation.Created && seen !is BackupCreation.Created) relist()
                seen = creation
            }
        }
    }

    fun load() {
        _state.value = BackupsUiState.Loading
        viewModelScope.launch {
            _state.value =
                connection
                    .consoleCall(TAG) { _, client -> client.backups() }
                    .fold(
                        onSuccess = { BackupsUiState.Ready(it, creation = connection.backupCreation.value) },
                        onFailure = { BackupsUiState.Failed(it.consoleFailure()) },
                    )
        }
    }

    /** A pull: reads the backups again in place, keeping the page, and says in a message when it couldn't. */
    fun refresh() {
        val ready = _state.value as? BackupsUiState.Ready ?: return
        if (ready.refreshing || ready.restore == Restore.Restoring) return
        update { copy(refreshing = true, refreshFailed = null) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client -> client.backups() }
                .onSuccess { fresh -> update { copy(backups = fresh, refreshing = false) } }
                .onFailure { error -> update { copy(refreshing = false, refreshFailed = error.consoleFailure()) } }
        }
    }

    fun askCreate(show: Boolean) = update { copy(choosing = if (show && !creating) BackupOptions() else null) }

    fun choose(options: BackupOptions) = update { if (choosing == null) this else copy(choosing = options) }

    /** Starts a backup with what the sheet holds. It runs on the connection, so leaving the page does not cancel it. */
    fun create() {
        val ready = _state.value as? BackupsUiState.Ready ?: return
        val options = ready.choosing ?: return
        if (ready.creating) return
        update { copy(choosing = null) }
        connection.setBackupCreation(BackupCreation.Creating)
        connection.launchBackup {
            val result = connection.consoleCall(TAG) { _, client -> client.createBackup(options) }
            connection.setBackupCreation(result.fold({ BackupCreation.Created(it) }, { BackupCreation.Failed(it) }))
        }
    }

    /** Puts the last backup's result away; a backup still being made stays shown. */
    fun dismissCreation() {
        if (connection.backupCreation.value !is BackupCreation.Creating) connection.setBackupCreation(BackupCreation.Idle)
    }

    fun open(backup: ServerBackup?) = update { copy(open = backup) }

    fun askRestore(backup: ServerBackup?) = update { copy(confirmingRestore = backup, restore = if (backup != null) null else restore) }

    /** Restores the confirmed backup: the server restarts, and this follows it down and back up, then reads the list again. */
    fun restore() {
        val ready = _state.value as? BackupsUiState.Ready ?: return
        val backup = ready.confirmingRestore ?: return
        if (ready.restore == Restore.Restoring) return
        update { copy(confirmingRestore = null, open = null, restore = Restore.Restoring) }
        viewModelScope.launch {
            connection
                .consoleCall(TAG) { _, client ->
                    client.restoreBackup(backup.fileName)
                    client.awaitBack(timing)
                }.fold(
                    onSuccess = { back -> if (back) restored() else update { copy(restore = Restore.NotBack) } },
                    onFailure = { update { copy(restore = Restore.Failed) } },
                )
        }
    }

    fun dismissRestore() = update { copy(restore = null) }

    /**
     * The server is back: read what it holds now. This is the first signed-in call after the restore, so it is also where
     * a session the restore ended is found out: a refused token goes to the console, which sends the user to sign in.
     */
    private suspend fun restored() {
        connection
            .consoleCall(TAG) { _, client -> client.backups() }
            .fold(
                onSuccess = { list -> update { copy(backups = list, restore = Restore.Restored) } },
                onFailure = { update { copy(restore = Restore.Failed) } },
            )
    }

    private fun relist() {
        viewModelScope.launch {
            connection.consoleCall(TAG) { _, client -> client.backups() }.onSuccess { list -> update { copy(backups = list) } }
        }
    }

    private fun update(change: BackupsUiState.Ready.() -> BackupsUiState.Ready) =
        _state.update { (it as? BackupsUiState.Ready)?.change() ?: it }

    private companion object {
        const val TAG = "JellyfinBackups"
    }
}
