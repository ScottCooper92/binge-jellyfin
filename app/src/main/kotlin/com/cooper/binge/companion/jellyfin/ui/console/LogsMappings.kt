package com.cooper.binge.companion.jellyfin.ui.console

import androidx.annotation.StringRes
import com.binge.designsystem.component.LogRowEntry
import com.binge.designsystem.component.LogRowLevel
import com.cooper.binge.companion.jellyfin.server.LogEntry
import com.cooper.binge.companion.jellyfin.server.LogLevel
import com.binge.designsystem.R as DesR

/** The row for [this]: an entry with no level of its own is drawn as Info, with no logger label. */
internal fun LogEntry.toRowEntry() =
    LogRowEntry(
        id = id,
        timestampMillis = timestampMillis,
        timestampRaw = timestampRaw.orEmpty(),
        level = level.toRowLevel(),
        label = logger,
        message = message,
        data = null,
    )

private fun LogLevel?.toRowLevel() =
    when (this) {
        LogLevel.Debug -> LogRowLevel.Debug
        LogLevel.Warn -> LogRowLevel.Warn
        LogLevel.Error -> LogRowLevel.Error
        LogLevel.Info, null -> LogRowLevel.Info
    }

/** The design system's own name for a level, which its rows show too. */
@StringRes
internal fun LogLevel.labelRes() =
    when (this) {
        LogLevel.Debug -> DesR.string.log_level_debug
        LogLevel.Info -> DesR.string.log_level_info
        LogLevel.Warn -> DesR.string.log_level_warn
        LogLevel.Error -> DesR.string.log_level_error
    }
