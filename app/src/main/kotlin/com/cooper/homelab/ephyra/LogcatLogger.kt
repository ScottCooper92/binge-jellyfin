package com.cooper.homelab.ephyra

import android.util.Log
import com.cooper.homelab.core.diagnostics.keepPath
import com.cooper.homelab.core.foundation.LogField
import com.cooper.homelab.core.foundation.LogLevel
import com.cooper.homelab.core.foundation.Logger

/**
 * The shared library's [Logger] on Logcat: the entry's priority, `tag`, and `event` followed by its fields as `name=value`.
 * The error is named by its class only, never by its message, which can carry an address or a token. Only a debug build builds one
 * (the ring log's forward, and the debug-only server stores' logger), so a release build writes nothing to Logcat through it.
 */
internal class LogcatLogger(
    private val write: (priority: Int, tag: String, line: String) -> Unit = { priority, tag, line ->
        Log.println(priority, tag, line)
    },
) : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        fields: List<LogField>,
        error: Throwable?,
    ) {
        val parts = listOf(event) + fields.map(::render) + listOfNotNull(error?.let { "error=${it::class.java.simpleName}" })
        write(priority(level), tag, parts.joinToString(" "))
    }

    private fun priority(level: LogLevel): Int =
        when (level) {
            LogLevel.Info -> Log.INFO
            LogLevel.Warn -> Log.WARN
            LogLevel.Error -> Log.ERROR
        }

    private fun render(field: LogField): String =
        when (field) {
            is LogField.Count -> "${field.name}=${field.value}"
            is LogField.Elapsed -> "${field.name}=${field.value}"
            is LogField.Status -> "${field.name}=${field.code}"
            is LogField.Flag -> "${field.name}=${field.value}"
            is LogField.Case -> "${field.name}=${(field.value as? Enum<*>)?.name ?: field.value::class.java.simpleName}"
            is LogField.Server -> "server=${field.id.value}"
            is LogField.Path -> "path=${keepPath(field.url)}"
            is LogField.Version -> "${field.name}=${field.value}"
            is LogField.Text -> "${field.name}=${field.value}"
        }
}
