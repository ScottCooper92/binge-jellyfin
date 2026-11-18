package com.cooper.binge.companion.jellyfin.ui.search

import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleFailure
import com.cooper.binge.companion.jellyfin.ui.console.SaveProgress
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a search result's switch says of its configuration, and the change it makes, for every switch the search can carry. */
class ServerSwitchesTest {
    private val userRows =
        listOf(
            SettingRows.PLAYBACK_DEFAULT_AUDIO,
            SettingRows.PLAYBACK_REMEMBER_AUDIO,
            SettingRows.PLAYBACK_REMEMBER_SUBTITLES,
            SettingRows.PLAYBACK_AUTOPLAY,
            SettingRows.HOME_HIDE_PLAYED,
            SettingRows.HOME_MISSING_EPISODES,
            SettingRows.HOME_COLLECTIONS,
        )

    private val serverRows =
        listOf(
            SettingRows.SERVER_QUICK_CONNECT,
            SettingRows.TRICKPLAY_HW_ACCELERATION,
            SettingRows.TRICKPLAY_HW_ENCODING,
            SettingRows.TRICKPLAY_KEY_FRAMES,
            SettingRows.ADVANCED_SLOW_WARNING,
            SettingRows.ADVANCED_CLIENT_LOGS,
            SettingRows.ADVANCED_METRICS,
            SettingRows.ADVANCED_LEGACY_AUTH,
        )

    /** A server configuration that has every key the Advanced page offers, so each of its rows is there to read. */
    private val server =
        Json
            .parseToJsonElement(
                """{"QuickConnectAvailable":true,"EnableSlowResponseWarning":true,"AllowClientLogUpload":false,"EnableMetrics":true,""" +
                    """"EnableLegacyAuthorization":false,"TrickplayOptions":{"EnableHwAcceleration":true,"EnableHwEncoding":false,""" +
                    """"EnableKeyFrameOnlyExtraction":true,"Interval":10000}}""",
            ).jsonObject

    private fun ready(
        configuration: JsonObject = JsonObject(emptyMap()),
        save: SaveProgress = SaveProgress(),
    ) = SearchConfigState.Ready(configuration, save)

    private fun spec(row: String) = SWITCH_SPECS.getValue(row)

    private fun toggle(
        row: String,
        state: SearchConfigState,
        set: (Boolean) -> Unit = {},
    ) = configToggle(spec(row).read, state, set)

    @Test
    fun `every switch row has a spec, in the configuration its page saves to`() {
        assertEquals((userRows + serverRows).toSet(), SWITCH_SPECS.keys)
        assertTrue(userRows.all { spec(it).kind == ConfigKind.User })
        assertTrue(serverRows.all { spec(it).kind == ConfigKind.Server })
    }

    @Test
    fun `each user switch reads its own key and a missing key reads as the page's default`() {
        // The pages treat a key the server left out as its default, and a switch must show what the page shows.
        val defaults = userRows.associateWith { toggle(it, ready())!!.checked }
        assertEquals(
            mapOf(
                SettingRows.PLAYBACK_DEFAULT_AUDIO to true,
                SettingRows.PLAYBACK_REMEMBER_AUDIO to true,
                SettingRows.PLAYBACK_REMEMBER_SUBTITLES to true,
                SettingRows.PLAYBACK_AUTOPLAY to true,
                SettingRows.HOME_HIDE_PLAYED to true,
                SettingRows.HOME_MISSING_EPISODES to false,
                SettingRows.HOME_COLLECTIONS to false,
            ),
            defaults,
        )
        val configuration =
            buildJsonObject {
                put("PlayDefaultAudioTrack", false)
                put("EnableNextEpisodeAutoPlay", false)
                put("DisplayCollectionsView", true)
            }
        assertFalse(toggle(SettingRows.PLAYBACK_DEFAULT_AUDIO, ready(configuration))!!.checked)
        assertFalse(toggle(SettingRows.PLAYBACK_AUTOPLAY, ready(configuration))!!.checked)
        assertTrue(toggle(SettingRows.HOME_COLLECTIONS, ready(configuration))!!.checked)
        assertTrue(toggle(SettingRows.PLAYBACK_REMEMBER_AUDIO, ready(configuration))!!.checked)
    }

    @Test
    fun `each server switch reads its own key, the trickplay ones from inside the options`() {
        val checked = serverRows.associateWith { toggle(it, ready(server))!!.checked }
        assertEquals(
            mapOf(
                SettingRows.SERVER_QUICK_CONNECT to true,
                SettingRows.TRICKPLAY_HW_ACCELERATION to true,
                SettingRows.TRICKPLAY_HW_ENCODING to false,
                SettingRows.TRICKPLAY_KEY_FRAMES to true,
                SettingRows.ADVANCED_SLOW_WARNING to true,
                SettingRows.ADVANCED_CLIENT_LOGS to false,
                SettingRows.ADVANCED_METRICS to true,
                SettingRows.ADVANCED_LEGACY_AUTH to false,
            ),
            checked,
        )
    }

    @Test
    fun `an Advanced switch the server did not send is not drawn, as its page row is not`() {
        val without = buildJsonObject { put("QuickConnectAvailable", true) }
        listOf(
            SettingRows.ADVANCED_SLOW_WARNING,
            SettingRows.ADVANCED_CLIENT_LOGS,
            SettingRows.ADVANCED_METRICS,
            SettingRows.ADVANCED_LEGACY_AUTH,
        ).forEach { assertNull(it, toggle(it, ready(without))) }
        // Quick Connect and the trickplay options have defaults on the page, so they are drawn.
        assertNotNull(toggle(SettingRows.SERVER_QUICK_CONNECT, ready(without)))
        assertNotNull(toggle(SettingRows.TRICKPLAY_HW_ACCELERATION, ready(without)))
    }

    @Test
    fun `a top level switch makes a change to its own key, and a trickplay switch to the options object made from the copy`() {
        val quick = spec(SettingRows.SERVER_QUICK_CONNECT).change(false)
        assertEquals("QuickConnectAvailable", quick.id)
        assertEquals("QuickConnectAvailable" to JsonPrimitive(false), quick.apply(server))

        val change = spec(SettingRows.TRICKPLAY_HW_ENCODING).change(true)
        val (key, options) = change.apply(server)
        assertEquals("TrickplayOptions", key)
        val updated = options as JsonObject
        assertTrue(updated.getValue("EnableHwEncoding").jsonPrimitive.boolean)
        // What the change did not touch goes back as it was, from the copy the change is made on.
        assertTrue(updated.getValue("EnableHwAcceleration").jsonPrimitive.boolean)
        assertEquals(
            10000,
            updated
                .getValue("Interval")
                .jsonPrimitive.content
                .toInt(),
        )
        // Put on a newer copy, it keeps what the newer copy holds for the other options.
        val newer =
            Json.parseToJsonElement("""{"TrickplayOptions":{"EnableHwAcceleration":false,"Interval":5000}}""").jsonObject
        val (_, onNewer) = change.apply(newer)
        assertFalse((onNewer as JsonObject).getValue("EnableHwAcceleration").jsonPrimitive.boolean)
        assertEquals(
            5000,
            onNewer
                .getValue("Interval")
                .jsonPrimitive.content
                .toInt(),
        )
    }

    @Test
    fun `a switch calls the setter it is given with the value asked for`() {
        val calls = mutableListOf<Boolean>()
        val toggle = toggle(SettingRows.SERVER_QUICK_CONNECT, ready(server)) { calls += it }!!
        assertTrue(toggle.enabled)
        assertFalse(toggle.loading)
        toggle.onCheckedChange(false)
        assertEquals(listOf(false), calls)
    }

    @Test
    fun `the key frame and legacy switches are offered only where the server has them`() {
        val old = JellyfinServerProfile.from("10.8.13")
        val current = JellyfinServerProfile.from("10.11.6")
        assertFalse(spec(SettingRows.TRICKPLAY_KEY_FRAMES).gate(old))
        assertTrue(spec(SettingRows.TRICKPLAY_KEY_FRAMES).gate(current))
        assertFalse(spec(SettingRows.ADVANCED_LEGACY_AUTH).gate(old))
        assertTrue(spec(SettingRows.ADVANCED_LEGACY_AUTH).gate(current))
        assertTrue(spec(SettingRows.SERVER_QUICK_CONNECT).gate(old))
    }

    @Test
    fun `a switch shows a spinner and takes no tap until the configuration has been read`() {
        listOf(SearchConfigState.Idle, SearchConfigState.Loading).forEach { state ->
            (userRows + serverRows).forEach { row ->
                val toggle = toggle(row, state)!!
                assertTrue(toggle.loading)
                assertFalse(toggle.enabled)
            }
        }
    }

    @Test
    fun `a switch shows a spinner and takes no tap while the configuration is read again`() {
        (userRows + serverRows).forEach { row ->
            val toggle = toggle(row, ready(server).copy(reading = true))
            if (toggle != null) {
                assertTrue(toggle.loading)
                assertFalse(toggle.enabled)
            }
        }
    }

    @Test
    fun `a switch is not drawn when the configuration could not be read`() {
        (userRows + serverRows).forEach { row ->
            assertNull(toggle(row, SearchConfigState.Failed(ConsoleFailure.Unreachable)))
        }
    }

    @Test
    fun `a switch shows a spinner while its write is in flight and returns when it lands`() {
        val saving = ready(server, SaveProgress(status = SaveStatus.Saving))
        assertTrue(toggle(SettingRows.TRICKPLAY_HW_ENCODING, saving)!!.loading)
        val saved = ready(server, SaveProgress(status = SaveStatus.Saved))
        assertFalse(toggle(SettingRows.TRICKPLAY_HW_ENCODING, saved)!!.loading)
    }

    @Test
    fun `a row that is not a switch the search carries has no spec`() {
        assertNull(SWITCH_SPECS[SettingRows.THIS_APP_USAGE_DATA])
        assertNull(SWITCH_SPECS[SettingRows.ADVANCED_CACHE])
    }
}
