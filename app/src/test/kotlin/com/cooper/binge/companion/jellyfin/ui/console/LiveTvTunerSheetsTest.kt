package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.GuideProviderSummary
import com.cooper.binge.companion.jellyfin.server.LiveTvSummary
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_HDHOMERUN
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_M3U
import com.cooper.binge.companion.jellyfin.server.TunerHostSummary
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class LiveTvTunerSheetsTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val summary =
        LiveTvSummary(
            tuners = listOf(TunerHostSummary("t1", "IPTV", "m3u", "https://iptv.example:8443")),
            providers = listOf(GuideProviderSummary("g1", "xmltv", "/config/guide.xml")),
        )

    private fun sheet(
        sheet: TunerSheet,
        actions: LiveTvActions = LiveTvActions(),
        saving: Boolean = false,
    ) = compose.setContent { JellyfinTheme { TunerSheetContent(sheet, saving, actions) } }

    @Test
    fun `the page's Add tuner opens the choice, and a tuner row opens that tuner`() {
        val sheets = mutableListOf<TunerSheet?>()
        val edited = mutableListOf<String>()
        compose.setContent {
            JellyfinTheme {
                LiveTvScreen(
                    LiveTvUiState.Ready(summary),
                    LiveTvActions(onSheet = { sheets += it }, onEdit = { edited += it }),
                    now = NOW,
                )
            }
        }
        compose.onNodeWithText("Add tuner").performClick()
        compose.onNodeWithText("IPTV").performClick()
        assertEquals(listOf<TunerSheet?>(TunerSheet.Choose), sheets)
        assertEquals(listOf("t1"), edited)
    }

    @Test
    fun `Add tuner is not offered on a failed page`() {
        compose.setContent { JellyfinTheme { LiveTvScreen(LiveTvUiState.Failed(ConsoleFailure.Unreachable), LiveTvActions(), now = NOW) } }
        compose.onNodeWithText("Add tuner").assertDoesNotExist()
    }

    @Test
    fun `the choice offers each kind and the search`() {
        val forms = mutableListOf<TunerDraft>()
        var discovered = 0
        sheet(TunerSheet.Choose, LiveTvActions(onForm = { forms += it }, onDiscover = { discovered++ }))
        compose.onNodeWithText("HDHomeRun").performClick()
        compose.onNodeWithText("M3U playlist").performClick()
        compose.onNodeWithText("Find tuners").performClick()
        assertEquals(listOf("hdhomerun", "m3u"), forms.map { it.type })
        assertEquals(1, discovered)
    }

    @Test
    fun `what the search found opens as a form, and finding nothing says so`() {
        val forms = mutableListOf<TunerDraft>()
        val device = TunerDraft(TUNER_TYPE_HDHOMERUN, name = "HDHomeRun PRIME", url = "http://192.168.1.77")
        sheet(TunerSheet.Found(listOf(device)), LiveTvActions(onForm = { forms += it }))
        compose.onNodeWithText("HDHomeRun PRIME").performClick()
        assertEquals(listOf(device), forms)
    }

    @Test
    fun `nothing found is said in words`() {
        sheet(TunerSheet.Found(emptyList()))
        compose.onNodeWithText("The server found no tuner it does not already have", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an M3U form takes a playlist, a name, a user agent and a stream limit, and Add sends what was typed`() {
        val saved = mutableListOf<TunerDraft>()
        sheet(TunerSheet.Form(TunerDraft(TUNER_TYPE_M3U)), LiveTvActions(onSave = { saved += it }))
        compose.onNodeWithText("Playlist address or path").performTextInput("https://iptv.example/list.m3u")
        compose.onNodeWithText("Name").performTextInput("Sports")
        compose.onNodeWithText("Stream limit").performTextInput("2")
        compose.onNodeWithText("Add").performClick()
        assertEquals(1, saved.size)
        assertEquals("https://iptv.example/list.m3u", saved.single().url)
        assertEquals("Sports", saved.single().name)
        assertEquals("2", saved.single().streamLimit)
    }

    @Test
    fun `an HDHomeRun form has no playlist fields, and a stream limit that does not read is not sent`() {
        val saved = mutableListOf<TunerDraft>()
        sheet(TunerSheet.Form(TunerDraft(TUNER_TYPE_HDHOMERUN)), LiveTvActions(onSave = { saved += it }))
        compose.onNodeWithText("User agent").assertDoesNotExist()
        compose.onNodeWithText("Import favourites only").assertIsDisplayed()
        compose.onNodeWithText("Add").performClick()
        assertEquals(emptyList<TunerDraft>(), saved)
    }

    @Test
    fun `an existing tuner offers Remove, which asks and does not remove`() {
        val asked = mutableListOf<String>()
        sheet(
            TunerSheet.Form(TunerDraft(TUNER_TYPE_M3U, id = "t1", url = "https://iptv.example/list.m3u")),
            LiveTvActions(onAskRemove = {
                asked +=
                    it
            }),
        )
        compose.onNodeWithText("Remove tuner").performClick()
        assertEquals(listOf("t1"), asked)
    }

    @Test
    fun `a new tuner has no Remove`() {
        sheet(TunerSheet.Form(TunerDraft(TUNER_TYPE_M3U)))
        compose.onNodeWithText("Remove tuner").assertDoesNotExist()
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }

    private val m3uHost =
        JsonObject(
            mapOf(
                "Id" to JsonPrimitive("t1"),
                "Type" to JsonPrimitive("m3u"),
                "Url" to JsonPrimitive("https://iptv.example/list.m3u"),
                "AllowStreamSharing" to JsonPrimitive(true),
                "EnableStreamLooping" to JsonPrimitive(false),
                "IgnoreDts" to JsonPrimitive(true),
                "FutureKey" to JsonPrimitive("kept"),
            ),
        )

    @Test
    fun `an M3U form shows the stream options the server sent, and only those`() {
        sheet(TunerSheet.Form(TunerDraft.of(m3uHost)))
        compose.onNodeWithText("Stream options", ignoreCase = true).assertExists()
        compose.onNodeWithText("Allow stream sharing").assertExists()
        compose.onNodeWithText("Auto-loop live streams").assertExists()
        compose.onNodeWithText("Ignore DTS", substring = true).assertExists()
        // The keys this server did not send (an older release) are not offered.
        compose.onNodeWithText("Allow fMP4 transcoding container").assertDoesNotExist()
        compose.onNodeWithText("Read input at native frame rate").assertDoesNotExist()
    }

    @Test
    fun `a new M3U tuner and an HDHomeRun have no stream options`() {
        sheet(TunerSheet.Form(TunerDraft(TUNER_TYPE_M3U)))
        compose.onNodeWithText("Stream options", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `a stream option switched in the form is saved, and every other key goes back as it came`() {
        val saved = mutableListOf<TunerDraft>()
        sheet(TunerSheet.Form(TunerDraft.of(m3uHost)), LiveTvActions(onSave = { saved += it }))
        compose.onNodeWithText("Auto-loop live streams").performClick()
        compose.onNodeWithText("Save").performClick()
        val body = saved.single().applyTo(m3uHost)
        assertEquals(JsonPrimitive(true), body.getValue("EnableStreamLooping"))
        assertEquals(JsonPrimitive(true), body.getValue("AllowStreamSharing"))
        assertEquals(JsonPrimitive(true), body.getValue("IgnoreDts"))
        assertEquals(JsonPrimitive("kept"), body.getValue("FutureKey"))
        assertEquals(null, body["AllowFmp4TranscodingContainer"])
    }

    private val hdHomeRunHost =
        JsonObject(
            mapOf(
                "Id" to JsonPrimitive("t2"),
                "Type" to JsonPrimitive("hdhomerun"),
                "Url" to JsonPrimitive("http://192.168.1.77"),
                "FallbackMaxStreamingBitrate" to JsonPrimitive(30_000_000),
            ),
        )

    @Test
    fun `the fallback bitrate is shown in Mbps where the server sent it`() {
        sheet(TunerSheet.Form(TunerDraft.of(JsonObject(m3uHost + ("FallbackMaxStreamingBitrate" to JsonPrimitive(12_500_000))))))
        compose.onNodeWithText("12.5").assertExists()
        compose.onNodeWithText("Fallback max stream bitrate (Mbps)", substring = true).assertExists()
    }

    @Test
    fun `a host from before 10_10 has no fallback bitrate field`() {
        sheet(TunerSheet.Form(TunerDraft.of(m3uHost)))
        compose.onNodeWithText("Fallback max stream bitrate", substring = true).assertDoesNotExist()
        assertEquals(null, TunerDraft.of(m3uHost).applyTo(m3uHost)["FallbackMaxStreamingBitrate"])
    }

    @Test
    fun `a fallback bitrate converts exactly, an untouched one goes back as it came, and one out of range is invalid`() {
        val draft = TunerDraft.of(JsonObject(hdHomeRunHost + ("FallbackMaxStreamingBitrate" to JsonPrimitive(2_097_145))))
        assertEquals("2.097145", draft.fallbackMbps)
        assertEquals(JsonPrimitive(2_097_145L), draft.applyTo(hdHomeRunHost)["FallbackMaxStreamingBitrate"])
        assertEquals(JsonPrimitive(8_200_000L), draft.copy(fallbackMbps = "8.2").applyTo(hdHomeRunHost)["FallbackMaxStreamingBitrate"])
        assertEquals(JsonPrimitive(1_500_000L), draft.copy(fallbackMbps = "1,5").applyTo(hdHomeRunHost)["FallbackMaxStreamingBitrate"])
        listOf("0.0000001", "3000").forEach { typed ->
            assertEquals(true, draft.copy(fallbackMbps = typed).fallbackInvalid)
        }
    }

    @Test
    fun `a typed fallback bitrate is saved in bits a second, and one that is not a positive number cannot be saved`() {
        val draft = TunerDraft.of(hdHomeRunHost)
        assertEquals("30", draft.fallbackMbps)
        assertEquals(JsonPrimitive(20_000_000L), draft.copy(fallbackMbps = "20").applyTo(hdHomeRunHost)["FallbackMaxStreamingBitrate"])
        assertEquals(JsonPrimitive(1_500_000L), draft.copy(fallbackMbps = " 1.5 ").applyTo(hdHomeRunHost)["FallbackMaxStreamingBitrate"])
        for (typed in listOf("", "0", "-3", "abc")) {
            assertEquals(true, draft.copy(fallbackMbps = typed).fallbackInvalid)
            assertEquals(false, draft.copy(fallbackMbps = typed).valid)
        }
        // Untouched, the host goes back with the figure it came with.
        assertEquals(JsonPrimitive(30_000_000L), draft.applyTo(hdHomeRunHost)["FallbackMaxStreamingBitrate"])
    }
}
