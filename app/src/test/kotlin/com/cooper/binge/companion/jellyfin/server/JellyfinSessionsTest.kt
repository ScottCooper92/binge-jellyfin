package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinSessionsTest {
    private fun session(json: String) = (Json.parseToJsonElement(json) as JsonObject).nowPlayingOrNull()

    @Test
    fun `an episode reads as its series, with the episode under it`() {
        val playing =
            session(
                """{"Id":"s","UserName":"Ana","DeviceName":"TV","Client":"Web",
                "PlayState":{"PositionTicks":250,"IsPaused":true,"PlayMethod":"DirectStream"},
                "NowPlayingItem":{"Name":"Pilot","SeriesName":"Severance","ParentIndexNumber":1,"IndexNumber":1,"RunTimeTicks":1000}}""",
            )!!
        assertEquals("Severance", playing.title)
        assertEquals("S1:E1 · Pilot", playing.episode)
        assertEquals(PlayMethod.DirectStream, playing.method)
        assertEquals(true, playing.paused)
        assertEquals(0.25f, playing.progress)
    }

    @Test
    fun `a movie has no episode line, and a missing runtime no progress`() {
        val playing = session("""{"Id":"s","NowPlayingItem":{"Name":"Sintel"},"PlayState":{"PlayMethod":"Mystery"}}""")!!
        assertEquals("Sintel", playing.title)
        assertNull(playing.episode)
        assertNull(playing.progress)
        assertEquals(PlayMethod.DirectPlay, playing.method)
        assertEquals(emptyList<String>(), playing.transcodeReasons)
    }

    @Test
    fun `a session playing nothing, or with no id, isn't a stream`() {
        assertNull(session("""{"Id":"s","DeviceName":"Phone"}"""))
        assertNull(session("""{"NowPlayingItem":{"Name":"Sintel"}}"""))
    }
}
