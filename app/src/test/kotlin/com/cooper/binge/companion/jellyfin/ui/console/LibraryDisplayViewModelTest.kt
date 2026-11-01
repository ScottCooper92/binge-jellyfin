package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.DISPLAY_FOLDER_VIEW
import com.cooper.binge.companion.jellyfin.server.DisplayList
import com.cooper.binge.companion.jellyfin.server.ListEntry
import com.cooper.binge.companion.jellyfin.server.PATH_SUBSTITUTIONS
import com.cooper.binge.companion.jellyfin.server.SORT_REMOVE_WORDS
import com.cooper.binge.companion.jellyfin.server.UiLanguage
import com.cooper.binge.companion.jellyfin.server.entries
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryDisplayViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.serverConfiguration =
            """{"ServerName":"truenas","EnableFolderView":false,"SortRemoveWords":["the","a"],"FutureSetting":{"keep":"me"},""" +
            """"PathSubstitutions":[{"From":"/media","To":"/mnt/media"}]}"""
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun LibraryDisplayViewModel.ready(check: (LibraryDisplayUiState.Ready) -> Boolean = { true }) =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) { state.first { it is LibraryDisplayUiState.Ready && check(it) } }
        } as LibraryDisplayUiState.Ready

    private fun saves(count: Int) =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (jellyfin.requests.count { it.url.encodedPath == CONFIGURATION && it.method == "POST" } < count) delay(POLL_MILLIS)
            }
        }

    @Test
    fun `reads the configuration and the languages the server can be shown in`() {
        val ready = made(LibraryDisplayViewModel(connection)).ready()
        assertEquals(
            "truenas",
            ready.configuration
                .getValue("ServerName")
                .jsonPrimitive.content,
        )
        assertEquals(listOf(UiLanguage("English (United States)", "en-US"), UiLanguage("Español", "es")), ready.languages)
    }

    @Test
    fun `a setting saves as it is made, with the whole configuration and what this app doesn't know untouched`() {
        val vm = made(LibraryDisplayViewModel(connection))
        vm.ready()
        vm.set(DISPLAY_FOLDER_VIEW, JsonPrimitive(true))
        vm.ready { it.configuration[DISPLAY_FOLDER_VIEW] == JsonPrimitive(true) }
        saves(1)
        val sent = jellyfin.lastBody(CONFIGURATION)
        assertTrue(sent.getValue(DISPLAY_FOLDER_VIEW).jsonPrimitive.boolean)
        assertEquals("truenas", sent.getValue("ServerName").jsonPrimitive.content)
        assertEquals(
            "me",
            sent
                .getValue("FutureSetting")
                .jsonObject
                .getValue("keep")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `a refused save puts the setting back and says so`() {
        val vm = made(LibraryDisplayViewModel(connection))
        vm.ready()
        jellyfin.status[CONFIGURATION] = 500
        vm.set(DISPLAY_FOLDER_VIEW, JsonPrimitive(true))
        val failed = vm.ready { it.saveFailed != null }
        assertEquals(JsonPrimitive(false), failed.configuration[DISPLAY_FOLDER_VIEW])
    }

    @Test
    fun `a list is edited as a draft, and saves whole with its Save`() {
        val vm = made(LibraryDisplayViewModel(connection))
        vm.ready()
        vm.edit(DisplayList.RemoveWords)
        assertEquals(listOf(ListEntry("the"), ListEntry("a")), vm.ready().draft)
        vm.sheet(DisplaySheet.NewEntry)
        vm.add(ListEntry("  an "))
        vm.add(ListEntry("an"))
        vm.add(ListEntry("   "))
        vm.remove(0)
        val draft = vm.ready { it.draft.size == 2 }
        assertNull(draft.sheet)
        assertEquals(listOf(ListEntry("a"), ListEntry("an")), draft.draft)
        // Nothing is sent until Save.
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath == CONFIGURATION && it.method == "POST" })
        vm.saveList()
        assertNull(vm.ready { it.editing == null }.editing)
        saves(1)
        assertEquals(
            listOf("a", "an"),
            jellyfin
                .lastBody(CONFIGURATION)
                .getValue(SORT_REMOVE_WORDS)
                .jsonArray
                .map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `leaving the editor drops the draft`() {
        val vm = made(LibraryDisplayViewModel(connection))
        vm.ready()
        vm.edit(DisplayList.RemoveWords)
        vm.remove(0)
        vm.edit(null)
        val ready = vm.ready { it.editing == null }
        assertEquals(listOf(ListEntry("the"), ListEntry("a")), ready.configuration.entries(DisplayList.RemoveWords))
        assertEquals(emptyList<ListEntry>(), ready.draft)
        vm.saveList()
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath == CONFIGURATION && it.method == "POST" })
    }

    @Test
    fun `path substitutions are pairs, and a half-filled pair is not added`() {
        val vm = made(LibraryDisplayViewModel(connection))
        vm.ready()
        vm.edit(DisplayList.Substitutions)
        vm.add(ListEntry("/data", " "))
        vm.add(ListEntry(" /data ", " /mnt/data "))
        assertEquals(listOf(ListEntry("/media", "/mnt/media"), ListEntry("/data", "/mnt/data")), vm.ready { it.draft.size == 2 }.draft)
        vm.saveList()
        saves(1)
        val saved =
            jellyfin
                .lastBody(CONFIGURATION)
                .getValue(PATH_SUBSTITUTIONS)
                .jsonArray
                .map { it.jsonObject }
        assertEquals(listOf("/media", "/data"), saved.map { it.getValue("From").jsonPrimitive.content })
        assertEquals(listOf("/mnt/media", "/mnt/data"), saved.map { it.getValue("To").jsonPrimitive.content })
    }

    @Test
    fun `the dummy chapter duration is a whole number of seconds in range`() {
        assertEquals(30, parseChapterSeconds(" 30 "))
        assertEquals(0, parseChapterSeconds("0"))
        assertNull(parseChapterSeconds("-1"))
        assertNull(parseChapterSeconds("1.5"))
        assertNull(parseChapterSeconds((MAX_DUMMY_CHAPTER_SECONDS + 1).toString()))
        assertNull(parseChapterSeconds(""))
    }

    @Test
    fun `a page that can't read says why, works without the languages, and Try again reads again`() {
        jellyfin.status["/Localization/Options"] = 500
        assertEquals(emptyList<UiLanguage>(), made(LibraryDisplayViewModel(connection)).ready().languages)
        jellyfin.status[CONFIGURATION] = 403
        val vm = made(LibraryDisplayViewModel(connection))
        runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it is LibraryDisplayUiState.Failed } } }
        jellyfin.status.remove(CONFIGURATION)
        vm.load()
        vm.ready()
    }

    private companion object {
        const val CONFIGURATION = "/System/Configuration"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
