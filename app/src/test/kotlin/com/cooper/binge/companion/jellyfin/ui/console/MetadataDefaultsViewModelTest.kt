package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.FetcherKind
import com.cooper.binge.companion.jellyfin.server.Provider
import com.cooper.binge.companion.jellyfin.server.fetchers
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
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
class MetadataDefaultsViewModelTest {
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
            """{"ServerName":"truenas","FutureSetting":{"keep":"me"},"MetadataOptions":[""" +
            """{"ItemType":"Movie","DisabledMetadataFetchers":[],"DisabledImageFetchers":[]},""" +
            """{"ItemType":"MusicVideo","DisabledMetadataFetchers":["The Open Movie Database"],"DisabledImageFetchers":[]}]}"""
        jellyfin.availableOptionsByType["movies"] =
            types(
                """{"Type":"Movie","MetadataFetchers":[${named(
                    "TheMovieDb",
                    "The Open Movie Database",
                )}],"ImageFetchers":[${named("TheMovieDb")}]}""",
            )
        jellyfin.availableOptionsByType["tvshows"] =
            types("""{"Type":"Series","MetadataFetchers":[${named("TheTVDB")}],"ImageFetchers":[${named("TheTVDB")}]}""")
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun named(vararg names: String) = names.joinToString(",") { """{"Name":"$it","DefaultEnabled":true}""" }

    private fun types(vararg options: String) =
        """{"MetadataSavers":[],"MetadataReaders":[],"TypeOptions":[${options.joinToString(",")}]}"""

    private fun MetadataDefaultsViewModel.ready(check: (MetadataDefaultsUiState.Ready) -> Boolean = { true }) =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) { state.first { it is MetadataDefaultsUiState.Ready && check(it) } }
        } as MetadataDefaultsUiState.Ready

    private fun saves(count: Int) =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (jellyfin.requests.count { it.url.encodedPath == CONFIGURATION && it.method == "POST" } < count) delay(POLL_MILLIS)
            }
        }

    private fun entry(type: String) =
        jellyfin
            .lastBody(CONFIGURATION)
            .getValue("MetadataOptions")
            .jsonArray
            .map { it.jsonObject }
            .first { it.getValue("ItemType").jsonPrimitive.content == type }

    @Test
    fun `lists the types the defaults hold, then the other types a library can hold, with the fetchers the server offers`() {
        val ready = made(MetadataDefaultsViewModel(connection)).ready()

        assertEquals(listOf("Movie", "MusicVideo", "Series"), ready.types())
        assertEquals(listOf("TheMovieDb", "The Open Movie Database"), ready.offered("Movie", FetcherKind.Metadata))
        assertEquals(
            listOf(Provider("TheMovieDb", true), Provider("The Open Movie Database", false)),
            ready.configuration.fetchers("MusicVideo", FetcherKind.Metadata, listOf("TheMovieDb", "The Open Movie Database")),
        )
    }

    @Test
    fun `a content type the server cannot list leaves its types without names, and the page still loads`() {
        jellyfin.status["/Libraries/AvailableOptions"] = 500

        val ready = made(MetadataDefaultsViewModel(connection)).ready()

        assertEquals(listOf("Movie", "MusicVideo"), ready.types())
        assertEquals(emptyList<String>(), ready.offered("Movie", FetcherKind.Metadata))
    }

    @Test
    fun `a switch saves as it is made, the whole configuration going back with only that list changed`() {
        val vm = made(MetadataDefaultsViewModel(connection))
        vm.ready()

        vm.set("Movie", FetcherKind.Metadata, "TheMovieDb", enabled = false)
        vm.ready { it.save.status == SaveStatus.Saved }
        saves(1)

        assertEquals(JsonArray(listOf(JsonPrimitive("TheMovieDb"))), entry("Movie").getValue("DisabledMetadataFetchers"))
        assertEquals(JsonArray(emptyList()), entry("Movie").getValue("DisabledImageFetchers"))
        assertEquals(JsonArray(listOf(JsonPrimitive("The Open Movie Database"))), entry("MusicVideo").getValue("DisabledMetadataFetchers"))
        assertEquals(
            "truenas",
            jellyfin
                .lastBody(CONFIGURATION)
                .getValue("ServerName")
                .jsonPrimitive.content,
        )
        assertEquals(
            "me",
            jellyfin
                .lastBody(CONFIGURATION)
                .getValue("FutureSetting")
                .jsonObject
                .getValue("keep")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `the first change to a type the defaults have no entry for writes one`() {
        val vm = made(MetadataDefaultsViewModel(connection))
        vm.ready()

        vm.set("Series", FetcherKind.Images, "TheTVDB", enabled = false)
        saves(1)

        assertEquals("Series", entry("Series").getValue("ItemType").jsonPrimitive.content)
        assertEquals(JsonArray(listOf(JsonPrimitive("TheTVDB"))), entry("Series").getValue("DisabledImageFetchers"))
        assertEquals(listOf("Movie", "MusicVideo", "Series"), vm.ready().types())
    }

    @Test
    fun `two switches made before the first answer both reach the server`() {
        val vm = made(MetadataDefaultsViewModel(connection))
        vm.ready()

        vm.set("Movie", FetcherKind.Metadata, "TheMovieDb", enabled = false)
        vm.set("Movie", FetcherKind.Images, "TheMovieDb", enabled = false)
        vm.ready { it.save.status == SaveStatus.Saved }
        saves(1)

        assertEquals(JsonArray(listOf(JsonPrimitive("TheMovieDb"))), entry("Movie").getValue("DisabledMetadataFetchers"))
        assertEquals(JsonArray(listOf(JsonPrimitive("TheMovieDb"))), entry("Movie").getValue("DisabledImageFetchers"))
    }

    @Test
    fun `a move saves the order of that type's kind and the page shows it`() {
        val vm = made(MetadataDefaultsViewModel(connection))
        vm.ready()

        vm.move("Movie", FetcherKind.Metadata, "The Open Movie Database", -1)
        vm.ready { it.save.status == SaveStatus.Saved }
        saves(1)

        assertEquals(
            JsonArray(listOf("The Open Movie Database", "TheMovieDb").map(::JsonPrimitive)),
            entry("Movie").getValue("MetadataFetcherOrder"),
        )
        assertEquals(
            listOf("The Open Movie Database", "TheMovieDb"),
            vm
                .ready()
                .let {
                    it.configuration.fetchers(
                        "Movie",
                        FetcherKind.Metadata,
                        it.offered("Movie", FetcherKind.Metadata),
                    )
                }.map { it.name },
        )
    }

    @Test
    fun `a move that cannot move sends nothing`() {
        val vm = made(MetadataDefaultsViewModel(connection))
        vm.ready()

        vm.move("Movie", FetcherKind.Metadata, "TheMovieDb", -1)
        vm.move("Movie", FetcherKind.Metadata, "Gone", 1)

        assertEquals(SaveProgress(), vm.ready().save)
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath == CONFIGURATION && it.method == "POST" })
    }

    @Test
    fun `a fetcher's page opens, and opening a type or the list closes it`() {
        val vm = made(MetadataDefaultsViewModel(connection))
        vm.ready()

        vm.open("Movie")
        vm.openFetcher(OpenFetcher(FetcherKind.Images, "TheMovieDb"))
        assertEquals("TheMovieDb", vm.ready { it.provider != null }.provider?.name)
        vm.openFetcher(null)
        assertNull(vm.ready { it.provider == null }.provider)
        vm.openFetcher(OpenFetcher(FetcherKind.Images, "TheMovieDb"))
        vm.open(null)
        assertNull(vm.ready { it.type == null }.provider)
    }

    @Test
    fun `a type's page opens and goes back to the list`() {
        val vm = made(MetadataDefaultsViewModel(connection))
        vm.ready()

        vm.open("Movie")
        assertEquals("Movie", vm.ready { it.type != null }.type)
        vm.open(null)
        assertNull(vm.ready { it.type == null }.type)
    }

    @Test
    fun `a save that can't reach the server keeps the switch, says so, and Retry sends it`() {
        val vm = made(MetadataDefaultsViewModel(connection))
        vm.ready()
        jellyfin.dropped += CONFIGURATION

        vm.set("Movie", FetcherKind.Metadata, "TheMovieDb", enabled = false)
        val failed = vm.ready { it.save.failed }
        assertEquals(listOf(Provider("TheMovieDb", false)), failed.configuration.fetchers("Movie", FetcherKind.Metadata, emptyList()))
        assertNull(failed.refused)

        jellyfin.dropped.clear()
        vm.retrySave()
        assertEquals(false, vm.ready { it.save.status == SaveStatus.Saved }.save.failed)
    }

    @Test
    fun `a refused save puts the switch back and says so`() {
        val vm = made(MetadataDefaultsViewModel(connection))
        vm.ready()
        jellyfin.status[CONFIGURATION] = 500

        vm.set("Movie", FetcherKind.Metadata, "TheMovieDb", enabled = false)

        val failed = vm.ready { it.refused != null }
        assertEquals(emptyList<Provider>(), failed.configuration.fetchers("Movie", FetcherKind.Metadata, emptyList()))
    }

    @Test
    fun `a configuration that can't be read fails the page, and Try again reads it`() {
        jellyfin.status[CONFIGURATION] = 403
        val vm = made(MetadataDefaultsViewModel(connection))
        val failed = runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it is MetadataDefaultsUiState.Failed } } }
        assertTrue(failed is MetadataDefaultsUiState.Failed)

        jellyfin.status.remove(CONFIGURATION)
        vm.load()

        assertEquals(listOf("Movie", "MusicVideo", "Series"), vm.ready().types())
    }

    private companion object {
        const val CONFIGURATION = "/System/Configuration"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
