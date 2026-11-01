package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.AccessSchedule
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.accessSchedules
import com.cooper.binge.companion.jellyfin.server.json
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Each section of a user's settings: a change saves the whole policy, with that one change and nothing else. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UserPolicyViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()

    /** Sam's policy as 10.11 sends it, with a key this app doesn't know. */
    private val full =
        """{"IsAdministrator":false,"IsDisabled":false,"EnableAllFolders":true,"EnabledFolders":[],"MaxActiveSessions":3,""" +
            """"EnableRemoteAccess":true,"IsHidden":false,"EnableUserPreferenceAccess":true,"LoginAttemptsBeforeLockout":-1,""" +
            """"EnableMediaPlayback":true,"EnableAudioPlaybackTranscoding":true,"EnableVideoPlaybackTranscoding":true,""" +
            """"EnablePlaybackRemuxing":true,"ForceRemoteSourceTranscoding":false,"RemoteClientBitrateLimit":0,""" +
            """"EnableContentDownloading":true,"EnableContentDeletion":false,"EnableContentDeletionFromFolders":["lib-movies"],""" +
            """"EnableMediaConversion":true,"EnablePublicSharing":true,"EnableCollectionManagement":false,""" +
            """"EnableSubtitleManagement":false,"EnableLyricManagement":false,"EnableRemoteControlOfOtherUsers":false,""" +
            """"EnableSharedDeviceControl":true,"SyncPlayAccess":"CreateAndJoinGroups","EnableLiveTvAccess":true,""" +
            """"EnableLiveTvManagement":false,"EnableAllChannels":true,"EnabledChannels":[],"EnableAllDevices":true,""" +
            """"EnabledDevices":[],"MaxParentalRating":null,"MaxParentalSubRating":null,"BlockUnratedItems":[],""" +
            """"BlockedTags":["gore"],"AllowedTags":[],""" +
            """"AccessSchedules":[{"Id":7,"UserId":"user-2","DayOfWeek":"Monday","StartHour":8,"EndHour":17}],""" +
            """"FutureKey":{"keep":"me"}}"""

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.serverUsers["user-2"] = JsonObject(jellyfin.serverUsers.getValue("user-2") + ("Policy" to Json.parseToJsonElement(full)))
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun UsersViewModel.ready(check: (UsersUiState.Ready) -> Boolean = { true }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it is UsersUiState.Ready && check(it) } } } as UsersUiState.Ready

    private fun saves() = jellyfin.requests.count { it.url.encodedPath == "/Users/user-2/Policy" }

    private fun saved(count: Int): JsonObject {
        runBlocking { withTimeout(TIMEOUT_MILLIS) { while (saves() < count) delay(POLL_MILLIS) } }
        return jellyfin.lastBody("/Users/user-2/Policy")
    }

    private fun sam(): UsersViewModel = made(UsersViewModel(connection)).also { vm -> vm.open(vm.ready().users.first { it.name == "Sam" }) }

    /** Makes [changes] in order, and checks the last save holds each, and every other key as it was. */
    private fun UsersViewModel.changing(vararg changes: Pair<String, JsonElement>) {
        val before = Json.parseToJsonElement(full).jsonObject
        changes.forEach { (key, value) -> setPolicy(key, value) }
        val after = saved(changes.size)
        changes.forEach { (key, value) -> assertEquals(key, value, after[key]) }
        val untouched = before.filterKeys { it !in changes.map { change -> change.first } }
        untouched.forEach { (key, value) -> assertEquals(key, value, after[key]) }
        assertEquals(
            "me",
            after
                .getValue("FutureKey")
                .jsonObject
                .getValue("keep")
                .jsonPrimitive.content,
        )
    }

    private fun on(value: Boolean) = JsonPrimitive(value)

    @Test
    fun `access settings save the whole policy`() {
        sam().changing(
            "EnableRemoteAccess" to on(false),
            "IsHidden" to on(true),
            "EnableUserPreferenceAccess" to on(false),
            "LoginAttemptsBeforeLockout" to JsonPrimitive(5),
            "MaxActiveSessions" to JsonPrimitive(1),
        )
    }

    @Test
    fun `playback settings save the whole policy`() {
        sam().changing(
            "EnableMediaPlayback" to on(false),
            "EnableAudioPlaybackTranscoding" to on(false),
            "EnableVideoPlaybackTranscoding" to on(false),
            "EnablePlaybackRemuxing" to on(false),
            "ForceRemoteSourceTranscoding" to on(true),
            "RemoteClientBitrateLimit" to JsonPrimitive(8_000_000),
        )
    }

    @Test
    fun `content settings save the whole policy, and deletion folders take a library in and out`() {
        val vm = sam()
        vm.changing(
            "EnableContentDownloading" to on(false),
            "EnableContentDeletion" to on(true),
            "EnableMediaConversion" to on(false),
            "EnablePublicSharing" to on(false),
            "EnableCollectionManagement" to on(true),
            "EnableSubtitleManagement" to on(true),
            "EnableLyricManagement" to on(true),
        )
        vm.editList("EnableContentDeletionFromFolders", memberChange("lib-shows", true))
        vm.editList("EnableContentDeletionFromFolders", memberChange("lib-movies", false))
        val after = saved(9)
        assertEquals(listOf("lib-shows"), after.getValue("EnableContentDeletionFromFolders").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `remote control settings save the whole policy`() {
        sam().changing(
            "EnableRemoteControlOfOtherUsers" to on(true),
            "EnableSharedDeviceControl" to on(false),
            "SyncPlayAccess" to JsonPrimitive("JoinGroups"),
        )
    }

    @Test
    fun `live TV settings save the whole policy, and channels take one in and out`() {
        val vm = sam()
        vm.changing("EnableLiveTvAccess" to on(false), "EnableLiveTvManagement" to on(true), "EnableAllChannels" to on(false))
        vm.editList("EnabledChannels", memberChange("ch-2", true))
        assertEquals(listOf("ch-2"), saved(4).getValue("EnabledChannels").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `device settings save the whole policy, and devices take one in and out`() {
        val vm = sam()
        vm.changing("EnableAllDevices" to on(false))
        vm.editList("EnabledDevices", memberChange("dev-1", true))
        vm.editList("EnabledDevices", memberChange("dev-2", true))
        vm.editList("EnabledDevices", memberChange("dev-1", false))
        assertEquals(listOf("dev-2"), saved(4).getValue("EnabledDevices").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `parental settings save the whole policy, with no limit as null`() {
        val vm = sam()
        vm.changing("MaxParentalRating" to JsonPrimitive(5), "MaxParentalSubRating" to JsonPrimitive(2))
        vm.setPolicy("MaxParentalRating", JsonNull)
        assertEquals(JsonNull, saved(3)["MaxParentalRating"])
        vm.editList("BlockUnratedItems", memberChange("Movie", true))
        vm.editList("BlockedTags", memberChange("violence", true))
        vm.editList("BlockedTags", memberChange("gore", false))
        vm.editList("AllowedTags", memberChange("kids", true))
        val after = saved(7)
        assertEquals(listOf("Movie"), after.getValue("BlockUnratedItems").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("violence"), after.getValue("BlockedTags").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("kids"), after.getValue("AllowedTags").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `an access schedule is added and removed with the others kept as the server sent them`() {
        val vm = sam()
        vm.editList("AccessSchedules") { it + AccessSchedule("Weekend", 9.0, 21.5).json() }
        val added = saved(1)
        // The server's own entry keeps its id and user.
        assertEquals(
            7,
            added
                .getValue("AccessSchedules")
                .jsonArray[0]
                .jsonObject
                .getValue("Id")
                .jsonPrimitive.content
                .toInt(),
        )
        assertEquals(listOf(AccessSchedule("Monday", 8.0, 17.0), AccessSchedule("Weekend", 9.0, 21.5)), added.accessSchedules())
        vm.editList("AccessSchedules") { list -> list.filterIndexed { i, _ -> i != 0 } }
        assertEquals(listOf(AccessSchedule("Weekend", 9.0, 21.5)), saved(2).accessSchedules())
    }

    @Test
    fun `a change the server refuses goes back, and says so`() {
        val vm = sam()
        jellyfin.status["/Users/user-2/Policy"] = 403
        vm.setPolicy("EnableMediaPlayback", on(false))
        val back = vm.ready { it.actionFailed != null }
        assertEquals(ConsoleFailure.Refused, back.actionFailed)
        assertEquals(on(true), back.openUser!!.policy["EnableMediaPlayback"])
    }

    @Test
    fun `a section opens and closes, and opening another user's page closes it`() {
        val vm = sam()
        vm.section(UserSection.Parental)
        assertEquals(UserSection.Parental, vm.ready().section)
        vm.section(null)
        assertEquals(null, vm.ready().section)
        vm.section(UserSection.Playback)
        vm.open(null)
        assertEquals(null, vm.ready { it.open == null }.section)
        assertTrue(vm.ready().users.isNotEmpty())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
