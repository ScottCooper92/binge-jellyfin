package com.cooper.binge.companion.jellyfin.ui.search

import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleFailure
import com.cooper.binge.companion.jellyfin.ui.console.SaveProgress
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a search result's switch says of a server-backed page, from the one copy of the configuration and through its setters. */
class ServerSwitchesTest {
    private val rows =
        listOf(
            SettingRows.PLAYBACK_DEFAULT_AUDIO to "defaultAudio",
            SettingRows.PLAYBACK_REMEMBER_AUDIO to "rememberAudio",
            SettingRows.PLAYBACK_REMEMBER_SUBTITLES to "rememberSubtitles",
            SettingRows.PLAYBACK_AUTOPLAY to "autoplay",
            SettingRows.HOME_HIDE_PLAYED to "hidePlayed",
            SettingRows.HOME_MISSING_EPISODES to "missingEpisodes",
            SettingRows.HOME_COLLECTIONS to "collections",
        )

    private class Recorder {
        val calls = mutableListOf<String>()

        fun setter(name: String): (Boolean) -> Unit = { calls += "$name=$it" }
    }

    private fun switches(recorder: Recorder) =
        ServerSwitches(
            recorder.setter("defaultAudio"),
            recorder.setter("rememberAudio"),
            recorder.setter("rememberSubtitles"),
            recorder.setter("autoplay"),
            recorder.setter("hidePlayed"),
            recorder.setter("missingEpisodes"),
            recorder.setter("collections"),
        )

    private fun ready(
        configuration: JsonObject = JsonObject(emptyMap()),
        save: SaveProgress = SaveProgress(),
    ) = SearchSwitchesState.Ready(configuration, save)

    @Test
    fun `each switch carries its own value and calls its own setter`() {
        rows.forEach { (row, name) ->
            val recorder = Recorder()
            val toggle = serverToggle(row, ready(), switches(recorder))
            assertNotNull(row, toggle)
            toggle!!.onCheckedChange(false)
            assertEquals("$row calls its own setter", listOf("$name=false"), recorder.calls)
            assertTrue(toggle.enabled)
            assertFalse(toggle.loading)
        }
    }

    @Test
    fun `each switch reads its own key, not a neighbour's`() {
        val configuration =
            buildJsonObject {
                put("PlayDefaultAudioTrack", false)
                put("RememberAudioSelections", true)
                put("RememberSubtitleSelections", false)
                put("EnableNextEpisodeAutoPlay", true)
                put("HidePlayedInLatest", false)
                put("DisplayMissingEpisodes", true)
                put("DisplayCollectionsView", false)
            }
        val checked = rows.associate { (row, _) -> row to serverToggle(row, ready(configuration), switches(Recorder()))!!.checked }
        assertEquals(
            mapOf(
                SettingRows.PLAYBACK_DEFAULT_AUDIO to false,
                SettingRows.PLAYBACK_REMEMBER_AUDIO to true,
                SettingRows.PLAYBACK_REMEMBER_SUBTITLES to false,
                SettingRows.PLAYBACK_AUTOPLAY to true,
                SettingRows.HOME_HIDE_PLAYED to false,
                SettingRows.HOME_MISSING_EPISODES to true,
                SettingRows.HOME_COLLECTIONS to false,
            ),
            checked,
        )
    }

    @Test
    fun `a switch shows a spinner and takes no tap until the configuration has been read`() {
        listOf(SearchSwitchesState.Idle, SearchSwitchesState.Loading).forEach { state ->
            rows.forEach { (row, _) ->
                val toggle = serverToggle(row, state, switches(Recorder()))!!
                assertTrue(toggle.loading)
                assertFalse(toggle.enabled)
            }
        }
    }

    @Test
    fun `a switch is not drawn when the configuration could not be read`() {
        rows.forEach { (row, _) ->
            assertNull(serverToggle(row, SearchSwitchesState.Failed(ConsoleFailure.Unreachable), switches(Recorder())))
        }
    }

    @Test
    fun `a switch shows a spinner while its write is in flight and returns when it lands`() {
        val saving = ready(save = SaveProgress(status = SaveStatus.Saving))
        assertTrue(serverToggle(SettingRows.HOME_COLLECTIONS, saving, switches(Recorder()))!!.loading)
        val saved = ready(save = SaveProgress(status = SaveStatus.Saved))
        assertFalse(serverToggle(SettingRows.HOME_COLLECTIONS, saved, switches(Recorder()))!!.loading)
    }

    @Test
    fun `a row that is not one of the seven has none`() {
        assertNull(serverToggle(SettingRows.THIS_APP_USAGE_DATA, ready(), switches(Recorder())))
        assertNull(serverToggle(null, ready(), switches(Recorder())))
    }

    @Test
    fun `the seven rows are the ones the view model's switches cover`() {
        assertEquals(rows.map { it.first }.toSet(), PLAYBACK_SWITCH_ROWS + HOME_SWITCH_ROWS)
    }
}
