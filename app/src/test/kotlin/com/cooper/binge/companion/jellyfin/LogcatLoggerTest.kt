package com.cooper.binge.companion.jellyfin

import android.util.Log
import com.cooper.homelab.core.foundation.LogField
import com.cooper.homelab.core.foundation.LogLevel
import com.cooper.homelab.core.foundation.ServerId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class LogcatLoggerTest {
    private val written = mutableListOf<Triple<Int, String, String>>()
    private val logger = LogcatLogger { priority, tag, line -> written += Triple(priority, tag, line) }

    @Test
    fun `each level writes at its own priority`() {
        logger.log(LogLevel.Info, "T", "a", emptyList(), null)
        logger.log(LogLevel.Warn, "T", "b", emptyList(), null)
        logger.log(LogLevel.Error, "T", "c", emptyList(), null)
        assertEquals(listOf(Log.INFO, Log.WARN, Log.ERROR), written.map { it.first })
        assertEquals(listOf("a", "b", "c"), written.map { it.third })
    }

    @Test
    fun `fields follow the event as name equals value`() {
        logger.log(
            LogLevel.Info,
            "T",
            "synced",
            listOf(
                LogField.Count("items", 3),
                LogField.Status("status", 502),
                LogField.Flag("cached", true),
                LogField.Case("reason", LogLevel.Warn),
                LogField.Case("failure", IOException("x")),
                LogField.Server(ServerId("server-1")),
                LogField.Path("https://nas.lan:8096/Users/0123456789abcdef0123456789abcdef/Items/42?api_key=secret"),
                LogField.Text("phase", "index"),
            ),
            null,
        )
        assertEquals(
            "synced items=3 status=502 cached=true reason=Warn failure=IOException server=server-1 " +
                "path=Users/{id}/Items/{id} phase=index",
            written.single().third,
        )
    }

    @Test
    fun `an error is named by its class and its message is never written`() {
        logger.log(LogLevel.Error, "T", "failed", emptyList(), IOException("http://nas.lan token=abc"))
        assertEquals("failed error=IOException", written.single().third)
    }
}
