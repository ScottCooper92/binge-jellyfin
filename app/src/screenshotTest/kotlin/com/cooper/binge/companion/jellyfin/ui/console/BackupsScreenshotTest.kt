package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStateLightPreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.BackupCreation
import com.cooper.binge.companion.jellyfin.server.BackupOptions
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import com.cooper.binge.companion.jellyfin.server.ServerBackup
import java.io.IOException

private const val NOW = 1_793_534_400_000L // 31 Oct 2026, 12:00 UTC
private const val DAY = 24 * 60 * 60 * 1000L

private val running = JellyfinVersion(10, 11, 6)

private val previewBackups =
    listOf(
        ServerBackup("jellyfin-backup-20261030090000.zip", NOW - DAY, "10.11.6.0", BackupOptions()),
        ServerBackup(
            "jellyfin-backup-20261020090000.zip",
            NOW - 11 * DAY,
            "10.11.5.0",
            BackupOptions(database = true, metadata = true, subtitles = true, trickplay = true),
        ),
    )

private val previewReady = BackupsUiState.Ready(previewBackups)

/** Backups: the list across the matrix, then each thing the page can be doing, its sheets and the restore confirm, and the read states. */
class BackupsScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun backups() = BackupsScreen(previewReady, BackupsActions(), running, NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun creating() = BackupsScreen(previewReady.copy(creation = BackupCreation.Creating), BackupsActions(), running, NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun created() =
        BackupsScreen(
            previewReady.copy(creation = BackupCreation.Created(previewBackups.first())),
            BackupsActions(),
            running,
            NOW,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun notMade() = BackupsScreen(previewReady.copy(creation = BackupCreation.Failed(IOException("down"))), BackupsActions(), running, NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun notMadeWithReason() =
        BackupsScreen(
            previewReady.copy(
                creation =
                    BackupCreation.Failed(
                        JellyfinHttpException(500, "Backup/Create", "The backup directory does not have at least 5GiB free space"),
                    ),
            ),
            BackupsActions(),
            running,
            NOW,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun restoring() = BackupsScreen(previewReady.copy(restore = Restore.Restoring), BackupsActions(), running, NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun restored() = BackupsScreen(previewReady.copy(restore = Restore.Restored), BackupsActions(), running, NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinSpanishPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun newBackup() = Surface { CreateBackupSheet(BackupOptions(database = true, metadata = true), BackupsActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinSpanishPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun oneBackup() = Surface { BackupSheet(previewBackups.last(), running, BackupsActions(), NOW, inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun oneBackupOtherRelease() =
        Surface {
            BackupSheet(previewBackups.last(), JellyfinVersion(10, 11, 7), BackupsActions(), NOW, inWindow = false)
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun restoreConfirm() = Surface { RestoreDialog(previewBackups.first(), running, BackupsActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun restoreConfirmOtherRelease() = Surface { RestoreDialog(previewBackups.last(), running, BackupsActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun empty() = BackupsScreen(BackupsUiState.Ready(emptyList()), BackupsActions(), running, NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = BackupsScreen(BackupsUiState.Loading, BackupsActions(), running, NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = BackupsScreen(BackupsUiState.Failed(ConsoleFailure.Refused), BackupsActions(), running, NOW)
}
