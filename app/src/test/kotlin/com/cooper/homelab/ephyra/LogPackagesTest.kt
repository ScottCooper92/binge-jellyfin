package com.cooper.homelab.ephyra

import com.cooper.homelab.core.diagnostics.Redactor
import com.cooper.homelab.core.diagnostics.RingLog
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.foundation.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/** The prefixes the app's log keeps frames for, held against the ring log that uses them, so a rename cannot leave them matching nothing. */
class LogPackagesTest {
    private val ring =
        RingLog(Clock { Instant.fromEpochMilliseconds(0) }, Redactor(), serverOrder = { emptyList() }, ownPackages = OWN_LOG_PACKAGES)

    private fun frame(
        className: String,
        method: String,
        file: String,
    ) = StackTraceElement(className, method, file, 12)

    @Test
    fun `a logged error keeps a frame of the app and a frame of the library and no other`() {
        val error =
            IllegalStateException("never kept").apply {
                stackTrace =
                    arrayOf(
                        frame("com.cooper.homelab.ephyra.ui.console.LogsScreen", "open", "LogsScreen.kt"),
                        frame("com.cooper.homelab.core.server.connection.Connection", "call", "Connection.kt"),
                        frame("okhttp3.internal.connection.RealCall", "execute", "RealCall.kt"),
                    )
            }

        ring.log(LogLevel.Error, "Test", "failed", emptyList(), error)

        val kept =
            ring.entries.value
                .single()
                .error
                .orEmpty()
        assertTrue(kept, kept.contains("com.cooper.homelab.ephyra.ui.console.LogsScreen.open(LogsScreen.kt:12)"))
        assertTrue(kept, kept.contains("com.cooper.homelab.core.server.connection.Connection.call(Connection.kt:12)"))
        assertFalse(kept, kept.contains("okhttp3"))
        assertFalse(kept, kept.contains("never kept"))
    }

    @Test
    fun `the prefix is the one package root the app and the library share`() {
        assertEquals(listOf("com.cooper.homelab."), OWN_LOG_PACKAGES)
        assertTrue(LogPackagesTest::class.java.name.startsWith(OWN_LOG_PACKAGES.single()))
    }
}
