package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Which rows a release and an acceleration type show, and that a tap reaches its action. */
@RunWith(RobolectricTestRunner::class)
class TranscodingScreenTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun encoding(type: String): JsonObject =
        Json
            .parseToJsonElement(FakeJellyfin().also { it.close() }.encodingConfiguration)
            .jsonObject
            .let { JsonObject(it + ("HardwareAccelerationType" to JsonPrimitive(type))) }

    private val server = Json.parseToJsonElement("""{"MinResumePct":5,"RemoteClientBitrateLimit":8000000}""").jsonObject

    private fun show(
        type: String,
        version: String,
        actions: TranscodingActions = TranscodingActions(),
        threads: Int = -1,
    ) {
        val config = JsonObject(encoding(type) + ("EncodingThreadCount" to JsonPrimitive(threads)))
        val state = TranscodingUiState.Ready(config, server)
        compose.setContent { JellyfinTheme { TranscodingScreen(state, actions, JellyfinServerProfile.from(version)) } }
    }

    @Test
    fun `the 10_9 options are hidden before 10_9, and shown from it`() {
        show("nvenc", "10.8.13")
        compose.onNodeWithText("Throttle transcodes").performScrollTo().assertExists()
        compose.onAllNodesWithText("Delete played segments").assertCountEquals(0)
        compose.onAllNodesWithText("Variable bitrate audio").assertCountEquals(0)
        compose.onAllNodesWithText("Stereo downmix algorithm").assertCountEquals(0)
    }

    @Test
    fun `from 10_9 they show`() {
        show("nvenc", "10.9.0")
        compose.onNodeWithText("Delete played segments").performScrollTo().assertExists()
        compose.onNodeWithText("Variable bitrate audio").performScrollTo().assertExists()
        compose.onNodeWithText("Stereo downmix algorithm").performScrollTo().assertExists()
    }

    @Test
    fun `the Quick Sync device is for Quick Sync on 10_10`() {
        show("qsv", "10.9.0")
        compose.onAllNodesWithText("Quick Sync device").assertCountEquals(0)
    }

    @Test
    fun `the Quick Sync device shows on 10_10`() {
        show("qsv", "10.10.7")
        compose.onNodeWithText("Quick Sync device").assertExists()
        compose.onAllNodesWithText("VAAPI device").assertCountEquals(0)
    }

    @Test
    fun `no hardware means no hardware decoding or encoding, and software tone mapping has its switch but no mode`() {
        show("none", "10.11.6")
        compose.onAllNodesWithText("HARDWARE DECODING").assertCountEquals(0)
        compose.onAllNodesWithText("HARDWARE ENCODING").assertCountEquals(0)
        compose.onNodeWithText("TONE MAPPING").performScrollTo().assertExists()
        compose.onNodeWithText("Enable tone mapping").performScrollTo().assertExists()
        compose.onAllNodesWithText("Mode").assertCountEquals(0)
    }

    @Test
    fun `software tone mapping is not on 10_9`() {
        show("none", "10.9.0")
        compose.onAllNodesWithText("TONE MAPPING").assertCountEquals(0)
    }

    @Test
    fun `hardware has decoding by codec, and tone mapping with its switch and mode`() {
        show("nvenc", "10.11.6")
        compose.onNodeWithText("HARDWARE DECODING").assertExists()
        compose.onNodeWithText("MPEG-4").assertExists()
        compose.onNodeWithText("Enable tone mapping").performScrollTo().assertExists()
        compose.onNodeWithText("Mode").performScrollTo().assertExists()
        compose.onNodeWithText("Enhanced NVDEC decoder").performScrollTo().assertExists()
        compose.onAllNodesWithText("Low power HEVC encoder").assertCountEquals(0)
    }

    @Test
    fun `10-bit decoding is for the types that have it, and the range extensions for three of them from 10_10`() {
        show("nvenc", "10.10.7")
        compose.onNodeWithText("HEVC 10-bit").performScrollTo().assertExists()
        compose.onNodeWithText("VP9 10-bit").assertExists()
        compose.onNodeWithText("HEVC RExt 8/10-bit").assertExists()
        compose.onNodeWithText("HEVC RExt 12-bit").assertExists()
    }

    @Test
    fun `AMF and RKMPP decode 10-bit but not the range extensions`() {
        show("amf", "10.10.7")
        compose.onNodeWithText("HEVC 10-bit").performScrollTo().assertExists()
        compose.onAllNodesWithText("HEVC RExt 8/10-bit").assertCountEquals(0)
        compose.onAllNodesWithText("HEVC RExt 12-bit").assertCountEquals(0)
    }

    @Test
    fun `the range extensions are not on 10_9, and V4L2 has no 10-bit decoding`() {
        show("vaapi", "10.9.0")
        compose.onNodeWithText("HEVC 10-bit").performScrollTo().assertExists()
        compose.onAllNodesWithText("HEVC RExt 8/10-bit").assertCountEquals(0)
        compose.onAllNodesWithText("HEVC RExt 12-bit").assertCountEquals(0)
    }

    @Test
    fun `V4L2 has no 10-bit decoding`() {
        show("v4l2m2m", "10.11.6")
        compose.onAllNodesWithText("HEVC 10-bit").assertCountEquals(0)
        compose.onAllNodesWithText("VP9 10-bit").assertCountEquals(0)
    }

    @Test
    fun `AV1 encoding is not on 10_8, and neither is the low power H_264 encoder on a type that isn't Intel`() {
        show("nvenc", "10.8.13")
        compose.onAllNodesWithText("Allow AV1 encoding").assertCountEquals(0)
        compose.onAllNodesWithText("Low power H.264 encoder").assertCountEquals(0)
    }

    @Test
    fun `AV1 encoding shows from 10_9`() {
        show("nvenc", "10.9.0")
        compose.onNodeWithText("Allow AV1 encoding").performScrollTo().assertExists()
        compose.onAllNodesWithText("Low power H.264 encoder").assertCountEquals(0)
    }

    @Test
    fun `Quick Sync has both low power encoders`() {
        show("qsv", "10.11.6")
        compose.onNodeWithText("Low power HEVC encoder").performScrollTo().assertExists()
        compose.onNodeWithText("Low power H.264 encoder").performScrollTo().assertExists()
    }

    @Test
    fun `the quality numbers show their values, and a tap opens the sheet`() {
        val calls = mutableListOf<String>()
        show("none", "10.11.6", TranscodingActions(onSheet = { calls += "sheet ${(it as? TranscodingSheet.Number)?.field}" }))
        compose.onNodeWithText("H.264 quality (CRF)").performScrollTo().assertExists()
        compose.onNodeWithText("23").assertExists()
        compose.onNodeWithText("28").assertExists()
        compose.onNodeWithText("2048").performScrollTo().assertExists()
        compose.onNodeWithText("Max muxing queue size").performScrollTo().performClick()
        assertEquals(listOf("sheet MuxingQueue"), calls)
    }

    @Test
    fun `V4L2 has no tone mapping`() {
        show("v4l2m2m", "10.11.6")
        compose.onAllNodesWithText("TONE MAPPING").assertCountEquals(0)
        compose.onNodeWithText("H.264").assertExists()
    }

    @Test
    fun `a switch, a codec and a number reach their actions`() {
        val calls = mutableListOf<String>()
        show(
            "nvenc",
            "10.11.6",
            TranscodingActions(
                onEncoding = { key, value -> calls += "$key ${(value as JsonPrimitive).content}" },
                onCodec = { codec, on -> calls += "codec $codec $on" },
                onSheet = { calls += "sheet ${(it as? TranscodingSheet.Number)?.field}" },
            ),
        )
        compose.onNodeWithText("MPEG-4").performScrollTo().performClick()
        compose.onNodeWithText("Use hardware encoding").performScrollTo().performClick()
        compose.onNodeWithText("Downmix volume boost").performScrollTo().performClick()
        assertEquals(listOf("codec mpeg4 true", "EnableHardwareEncoding false", "sheet DownmixBoost"), calls)
    }

    @Test
    fun `zero threads reads as all available, not as 0`() {
        show("nvenc", "10.11.6", threads = 0)
        compose.onNodeWithText("All available").performScrollTo().assertExists()
    }

    @Test
    fun `the page warns about the wrong type`() {
        show("none", "10.11.6")
        compose.onNodeWithText("The wrong type breaks every transcode.").assertExists()
    }

    @Test
    fun `a server that never saved the page leaves out the folders, and both rows still show`() {
        val fresh = JsonObject(encoding("none") - "TranscodingTempPath" - "FallbackFontPath")
        compose.setContent {
            JellyfinTheme {
                TranscodingScreen(
                    TranscodingUiState.Ready(fresh, server),
                    TranscodingActions(),
                    JellyfinServerProfile.from("10.10.7"),
                )
            }
        }
        compose.onNodeWithText("Fallback font folder").performScrollTo().assertExists()
        compose.onNodeWithText("Not set").performScrollTo().assertExists()
        compose.onNodeWithText("Transcoding temporary files").performScrollTo().assertExists()
        compose.onNodeWithText("Server default").performScrollTo().assertExists()
    }
}
