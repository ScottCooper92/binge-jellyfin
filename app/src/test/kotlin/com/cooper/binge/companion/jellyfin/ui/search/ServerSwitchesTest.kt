package com.cooper.binge.companion.jellyfin.ui.search

import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.server.HomeLayout
import com.cooper.binge.companion.jellyfin.server.PlaybackPreferences
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleFailure
import com.cooper.binge.companion.jellyfin.ui.console.HomeLayoutUiState
import com.cooper.binge.companion.jellyfin.ui.console.PlaybackUiState
import com.cooper.binge.companion.jellyfin.ui.console.SaveProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a search result's switch says of a server-backed page, from the page's own state and through the page's own setters. */
class ServerSwitchesTest {
    private val playbackRows =
        listOf(
            SettingRows.PLAYBACK_DEFAULT_AUDIO,
            SettingRows.PLAYBACK_REMEMBER_AUDIO,
            SettingRows.PLAYBACK_REMEMBER_SUBTITLES,
            SettingRows.PLAYBACK_AUTOPLAY,
        )

    private val homeRows = listOf(SettingRows.HOME_HIDE_PLAYED, SettingRows.HOME_MISSING_EPISODES, SettingRows.HOME_COLLECTIONS)

    private class Recorder {
        val calls = mutableListOf<String>()

        fun setter(name: String): (Boolean) -> Unit = { calls += "$name=$it" }
    }

    private fun playbackSwitches(recorder: Recorder) =
        PlaybackSwitches(
            recorder.setter("defaultAudio"),
            recorder.setter("rememberAudio"),
            recorder.setter("rememberSubtitles"),
            recorder.setter("autoplay"),
        )

    private fun homeSwitches(recorder: Recorder) =
        HomeSwitches(recorder.setter("hidePlayed"), recorder.setter("missingEpisodes"), recorder.setter("collections"))

    private fun playback(
        preferences: PlaybackPreferences,
        save: SaveProgress = SaveProgress(),
    ) = PlaybackUiState.Ready(preferences, emptyList(), save)

    private val prefs = PlaybackPreferences("en", "en", SubtitleMode.Default)

    @Test
    fun `each playback switch carries the page's value and calls the page's own setter`() {
        val expected =
            mapOf(
                SettingRows.PLAYBACK_DEFAULT_AUDIO to "defaultAudio",
                SettingRows.PLAYBACK_REMEMBER_AUDIO to "rememberAudio",
                SettingRows.PLAYBACK_REMEMBER_SUBTITLES to "rememberSubtitles",
                SettingRows.PLAYBACK_AUTOPLAY to "autoplay",
            )
        playbackRows.forEach { row ->
            val recorder = Recorder()
            val on = playbackToggle(row, playback(prefs), playbackSwitches(recorder))!!
            assertTrue("$row is on", on.checked)
            assertTrue(on.enabled)
            assertFalse(on.loading)
            on.onCheckedChange(false)
            assertEquals("$row calls its own setter", listOf("${expected.getValue(row)}=false"), recorder.calls)
        }
    }

    @Test
    fun `a playback switch reads its own value, not a neighbour's`() {
        val only = prefs.copy(playDefaultAudioTrack = false, rememberAudio = true, rememberSubtitles = false, autoplayNextEpisode = true)
        val checked = playbackRows.associateWith { playbackToggle(it, playback(only), playbackSwitches(Recorder()))!!.checked }
        assertEquals(
            mapOf(
                SettingRows.PLAYBACK_DEFAULT_AUDIO to false,
                SettingRows.PLAYBACK_REMEMBER_AUDIO to true,
                SettingRows.PLAYBACK_REMEMBER_SUBTITLES to false,
                SettingRows.PLAYBACK_AUTOPLAY to true,
            ),
            checked,
        )
    }

    @Test
    fun `a playback switch shows a spinner and takes no tap until the page has loaded`() {
        playbackRows.forEach { row ->
            val toggle = playbackToggle(row, PlaybackUiState.Loading, playbackSwitches(Recorder()))!!
            assertTrue(toggle.loading)
            assertFalse(toggle.enabled)
        }
    }

    @Test
    fun `a playback switch is not drawn when the page failed to load`() {
        playbackRows.forEach { row ->
            assertNull(playbackToggle(row, PlaybackUiState.Failed(ConsoleFailure.Unreachable), playbackSwitches(Recorder())))
        }
    }

    @Test
    fun `a playback switch shows a spinner while its write is in flight and returns when it lands`() {
        val saving = playback(prefs, SaveProgress(status = SaveStatus.Saving))
        assertTrue(playbackToggle(SettingRows.PLAYBACK_AUTOPLAY, saving, playbackSwitches(Recorder()))!!.loading)
        val saved = playback(prefs, SaveProgress(status = SaveStatus.Saved))
        assertFalse(playbackToggle(SettingRows.PLAYBACK_AUTOPLAY, saved, playbackSwitches(Recorder()))!!.loading)
    }

    @Test
    fun `a playback switch shows the value the page put back after a refusal`() {
        val refused = playback(prefs.copy(autoplayNextEpisode = true)).copy(refused = ConsoleFailure.Refused)
        assertTrue(playbackToggle(SettingRows.PLAYBACK_AUTOPLAY, refused, playbackSwitches(Recorder()))!!.checked)
    }

    @Test
    fun `a row that is not a playback switch has none`() {
        assertNull(playbackToggle(SettingRows.HOME_COLLECTIONS, playback(prefs), playbackSwitches(Recorder())))
        assertNull(playbackToggle(null, playback(prefs), playbackSwitches(Recorder())))
    }

    private fun home(
        layout: HomeLayout,
        save: SaveProgress = SaveProgress(),
    ) = HomeLayoutUiState.Ready(views = emptyList(), layout = layout, save = save)

    @Test
    fun `each home screen switch carries the page's value and calls the page's own setter`() {
        val expected =
            mapOf(
                SettingRows.HOME_HIDE_PLAYED to "hidePlayed",
                SettingRows.HOME_MISSING_EPISODES to "missingEpisodes",
                SettingRows.HOME_COLLECTIONS to "collections",
            )
        homeRows.forEach { row ->
            val recorder = Recorder()
            val toggle =
                homeToggle(
                    row,
                    home(HomeLayout(hidePlayedInLatest = true, displayMissingEpisodes = true, displayCollectionsView = true)),
                    homeSwitches(recorder),
                )
            assertNotNull(row, toggle)
            toggle!!.onCheckedChange(false)
            assertEquals(listOf("${expected.getValue(row)}=false"), recorder.calls)
        }
    }

    @Test
    fun `a home screen switch reads its own value`() {
        val layout = HomeLayout(hidePlayedInLatest = true, displayMissingEpisodes = false, displayCollectionsView = true)
        val checked = homeRows.associateWith { homeToggle(it, home(layout), homeSwitches(Recorder()))!!.checked }
        assertEquals(
            mapOf(
                SettingRows.HOME_HIDE_PLAYED to true,
                SettingRows.HOME_MISSING_EPISODES to false,
                SettingRows.HOME_COLLECTIONS to true,
            ),
            checked,
        )
    }

    @Test
    fun `a home screen switch waits for the page, is not drawn when it failed, and spins while it saves`() {
        homeRows.forEach { row ->
            val loading = homeToggle(row, HomeLayoutUiState.Loading, homeSwitches(Recorder()))!!
            assertTrue(loading.loading)
            assertFalse(loading.enabled)
            assertNull(homeToggle(row, HomeLayoutUiState.Failed(ConsoleFailure.Unreachable), homeSwitches(Recorder())))
            val saving = homeToggle(row, home(HomeLayout(), SaveProgress(status = SaveStatus.Saving)), homeSwitches(Recorder()))!!
            assertTrue(saving.loading)
        }
    }

    @Test
    fun `a row that is not a home screen switch has none`() {
        assertNull(homeToggle(SettingRows.PLAYBACK_AUTOPLAY, home(HomeLayout()), homeSwitches(Recorder())))
    }

    @Test
    fun `every server switch row is a keyed entry of the index, and only those`() {
        val rows = (playbackRows + homeRows).toSet()
        assertEquals(rows, PLAYBACK_SWITCH_ROWS + HOME_SWITCH_ROWS)
    }
}
