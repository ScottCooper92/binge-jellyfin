package com.cooper.binge.companion.jellyfin.ui.search

import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.server.ENCODING_ACCELERATION
import com.cooper.binge.companion.jellyfin.server.ENCODING_AUDIO_VBR
import com.cooper.binge.companion.jellyfin.server.ENCODING_AV1
import com.cooper.binge.companion.jellyfin.server.ENCODING_DECODE_HEVC_10
import com.cooper.binge.companion.jellyfin.server.ENCODING_DECODE_HEVC_REXT_10
import com.cooper.binge.companion.jellyfin.server.ENCODING_DECODE_HEVC_REXT_12
import com.cooper.binge.companion.jellyfin.server.ENCODING_DECODE_VP9_10
import com.cooper.binge.companion.jellyfin.server.ENCODING_DEINTERLACE_DOUBLE_RATE
import com.cooper.binge.companion.jellyfin.server.ENCODING_ENHANCED_NVDEC
import com.cooper.binge.companion.jellyfin.server.ENCODING_FALLBACK_FONT
import com.cooper.binge.companion.jellyfin.server.ENCODING_HARDWARE_ENCODING
import com.cooper.binge.companion.jellyfin.server.ENCODING_HEVC
import com.cooper.binge.companion.jellyfin.server.ENCODING_INTEL_LOW_POWER_H264
import com.cooper.binge.companion.jellyfin.server.ENCODING_INTEL_LOW_POWER_HEVC
import com.cooper.binge.companion.jellyfin.server.ENCODING_SEGMENT_DELETION
import com.cooper.binge.companion.jellyfin.server.ENCODING_SUBTITLE_EXTRACTION
import com.cooper.binge.companion.jellyfin.server.ENCODING_SYSTEM_NATIVE
import com.cooper.binge.companion.jellyfin.server.ENCODING_THROTTLING
import com.cooper.binge.companion.jellyfin.server.ENCODING_TONEMAPPING
import com.cooper.binge.companion.jellyfin.server.ENCODING_VIDEO_TOOLBOX_TONEMAPPING
import com.cooper.binge.companion.jellyfin.server.ENCODING_VPP_TONEMAPPING
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.NETWORK_AUTO_DISCOVERY
import com.cooper.binge.companion.jellyfin.server.NETWORK_IGNORE_VIRTUAL
import com.cooper.binge.companion.jellyfin.server.NETWORK_PUBLISHED_BY_REQUEST
import com.cooper.binge.companion.jellyfin.server.NETWORK_UPNP
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

    private val networkRows =
        listOf(NETWORK_AUTO_DISCOVERY, NETWORK_UPNP, NETWORK_PUBLISHED_BY_REQUEST, NETWORK_IGNORE_VIRTUAL).map { SettingRows.network(it) }

    private val encodingRows =
        listOf(
            ENCODING_DECODE_HEVC_10,
            ENCODING_DECODE_VP9_10,
            ENCODING_DECODE_HEVC_REXT_10,
            ENCODING_DECODE_HEVC_REXT_12,
            ENCODING_HARDWARE_ENCODING,
            ENCODING_HEVC,
            ENCODING_AV1,
            ENCODING_INTEL_LOW_POWER_HEVC,
            ENCODING_INTEL_LOW_POWER_H264,
            ENCODING_ENHANCED_NVDEC,
            ENCODING_SYSTEM_NATIVE,
            ENCODING_TONEMAPPING,
            ENCODING_VPP_TONEMAPPING,
            ENCODING_VIDEO_TOOLBOX_TONEMAPPING,
            ENCODING_DEINTERLACE_DOUBLE_RATE,
            ENCODING_AUDIO_VBR,
            ENCODING_THROTTLING,
            ENCODING_SEGMENT_DELETION,
            ENCODING_SUBTITLE_EXTRACTION,
            ENCODING_FALLBACK_FONT,
        ).map { SettingRows.transcoding(it) }

    /** An encoding configuration that has every switch key, set to hardware acceleration [type]. */
    private fun encoding(type: String): JsonObject =
        buildJsonObject {
            put(ENCODING_ACCELERATION, type)
            encodingRows.forEach { put(it.removePrefix("transcoding."), true) }
        }

    private fun encodingShown(
        row: String,
        type: String,
        version: String = "10.11.6",
    ): Boolean {
        val spec = spec(row)
        val profile = JellyfinServerProfile.from(version)
        return spec.gate(profile) && spec.applies(encoding(type), profile) && spec.read(encoding(type)) != null
    }

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
        assertEquals((userRows + serverRows + encodingRows + networkRows).toSet(), SWITCH_SPECS.keys)
        assertTrue(userRows.all { spec(it).kind == ConfigKind.User })
        assertTrue(serverRows.all { spec(it).kind == ConfigKind.Server })
        assertTrue(encodingRows.all { spec(it).kind == ConfigKind.Encoding })
        assertTrue(networkRows.all { spec(it).kind == ConfigKind.Network })
    }

    @Test
    fun `a network switch reads its own key as the page does and none where the server did not send it`() {
        val on = buildJsonObject { networkRows.forEach { put(it.removePrefix("network."), true) } }
        networkRows.forEach { assertTrue(it, spec(it).read(on)!!) }
        val off = buildJsonObject { networkRows.forEach { put(it.removePrefix("network."), false) } }
        networkRows.forEach { assertFalse(it, spec(it).read(off)!!) }
        networkRows.forEach { assertNull(it, spec(it).read(buildJsonObject { put("EnableHttps", true) })) }
    }

    @Test
    fun `the switches of the network page that wait for Save changes have no switch here`() {
        val formKeys = listOf("EnableHttps", "RequireHttps", "EnableRemoteAccess", "IsRemoteIPFilterBlacklist", "EnableIPv4", "EnableIPv6")
        formKeys.forEach { assertNull(it, SWITCH_SPECS[SettingRows.network(it)]) }
    }

    @Test
    fun `UPnP is offered only on a release that has it, and the other network switches on every one`() {
        val upnp = spec(SettingRows.network(NETWORK_UPNP))
        assertTrue(upnp.gate(JellyfinServerProfile.from("10.10.7")))
        assertTrue(upnp.gate(JellyfinServerProfile.from("10.8.13")))
        assertFalse(upnp.gate(JellyfinServerProfile.from("10.11.6")))
        assertTrue(spec(SettingRows.network(NETWORK_AUTO_DISCOVERY)).gate(JellyfinServerProfile.from("10.11.6")))
    }

    @Test
    fun `a network switch makes a change to its own key`() {
        val change = spec(SettingRows.network(NETWORK_IGNORE_VIRTUAL)).change(false)
        assertEquals(NETWORK_IGNORE_VIRTUAL, change.id)
        assertEquals(NETWORK_IGNORE_VIRTUAL to JsonPrimitive(false), change.apply(buildJsonObject { }))
    }

    @Test
    fun `an encoding switch reads its own key and a key the server did not send reads as none`() {
        val configuration = encoding("qsv")
        encodingRows.forEach { assertTrue(it, spec(it).read(configuration)!!) }
        val without = buildJsonObject { put(ENCODING_ACCELERATION, "qsv") }
        encodingRows.forEach { assertNull(it, spec(it).read(without)) }
        val off = buildJsonObject { put(ENCODING_THROTTLING, false) }
        assertFalse(spec(SettingRows.transcoding(ENCODING_THROTTLING)).read(off)!!)
    }

    @Test
    fun `an encoding switch makes a change to its own key`() {
        val change = spec(SettingRows.transcoding(ENCODING_THROTTLING)).change(true)
        assertEquals(ENCODING_THROTTLING, change.id)
        assertEquals(ENCODING_THROTTLING to JsonPrimitive(true), change.apply(encoding("none")))
    }

    @Test
    fun `the switches the page draws for each acceleration type are the ones the search draws`() {
        fun shown(type: String) = encodingRows.filter { encodingShown(it, type) }.map { it.removePrefix("transcoding.") }.toSet()

        val none = shown("none")
        assertEquals(
            setOf(
                ENCODING_TONEMAPPING,
                ENCODING_DEINTERLACE_DOUBLE_RATE,
                ENCODING_AUDIO_VBR,
                ENCODING_THROTTLING,
                ENCODING_SEGMENT_DELETION,
                ENCODING_SUBTITLE_EXTRACTION,
                ENCODING_FALLBACK_FONT,
            ),
            none,
        )
        val qsv = shown("qsv")
        assertTrue(ENCODING_INTEL_LOW_POWER_HEVC in qsv && ENCODING_SYSTEM_NATIVE in qsv && ENCODING_VPP_TONEMAPPING in qsv)
        assertFalse(ENCODING_ENHANCED_NVDEC in qsv || ENCODING_VIDEO_TOOLBOX_TONEMAPPING in qsv)
        val nvenc = shown("nvenc")
        assertTrue(ENCODING_ENHANCED_NVDEC in nvenc && ENCODING_DECODE_HEVC_REXT_10 in nvenc)
        assertFalse(ENCODING_INTEL_LOW_POWER_HEVC in nvenc || ENCODING_SYSTEM_NATIVE in nvenc)
        val toolbox = shown("videotoolbox")
        assertTrue(ENCODING_VIDEO_TOOLBOX_TONEMAPPING in toolbox && ENCODING_HARDWARE_ENCODING in toolbox)
        assertFalse(ENCODING_DECODE_HEVC_10 in toolbox || ENCODING_DECODE_HEVC_REXT_10 in toolbox)
        // A blank type is how 10.8 stores none.
        assertEquals(none - ENCODING_TONEMAPPING, shown("").minus(ENCODING_TONEMAPPING))
    }

    @Test
    fun `the switches of a later release are not offered on an earlier one`() {
        listOf(ENCODING_AV1, ENCODING_AUDIO_VBR, ENCODING_SEGMENT_DELETION).forEach {
            assertTrue(it, encodingShown(SettingRows.transcoding(it), "qsv", "10.11.6"))
            assertFalse(it, encodingShown(SettingRows.transcoding(it), "qsv", "10.8.13"))
        }
        assertTrue(encodingShown(SettingRows.transcoding(ENCODING_DECODE_HEVC_REXT_10), "qsv", "10.10.7"))
        assertFalse(encodingShown(SettingRows.transcoding(ENCODING_DECODE_HEVC_REXT_10), "qsv", "10.9.11"))
        // Tone mapping in software is 10.10's.
        assertTrue(encodingShown(SettingRows.transcoding(ENCODING_TONEMAPPING), "none", "10.10.7"))
        assertFalse(encodingShown(SettingRows.transcoding(ENCODING_TONEMAPPING), "none", "10.9.11"))
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
