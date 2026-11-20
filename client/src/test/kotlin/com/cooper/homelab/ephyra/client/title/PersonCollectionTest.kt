package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemUserData
import com.cooper.homelab.ephyra.browse.JellyfinCollectionRepository
import com.cooper.homelab.ephyra.browse.JellyfinPersonRepository
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.browse.JellyfinUserDataRepository
import com.cooper.homelab.ephyra.browse.PersonDetail
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinVersion
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The person and collection pages: the mapping, and the view models against a fake server. */
class PersonCollectionTest {
    private val jellyfin = FakeJellyfin()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client =
        JellyfinClient(
            OkHttpClient(),
            jellyfin.baseUrl.toHttpUrl(),
            ClientIdentity("Ephyra", "Pixel", "device-1", "1.0"),
            Dispatchers.IO,
            logger = DiscardLogger,
        ).withToken("token-1")
    private val socket = MutableSharedFlow<List<UserDataChange>>(extraBufferCapacity = 8)
    private val profile = JellyfinServerProfile(JellyfinVersion(12, 4, 0))
    private val userData = JellyfinUserDataRepository(client, "user-1", profile, socket)

    @After
    fun close() {
        scope.cancel()
        jellyfin.close()
    }

    private fun personModel(pageSize: Int = 2) =
        PersonViewModel(
            scope,
            Dispatchers.Default,
            JellyfinPersonRepository(client, "user-1", profile),
            userData,
            { it.serverFailure() },
            pageSize,
        )

    private fun collectionModel() =
        CollectionViewModel(
            scope,
            Dispatchers.Default,
            JellyfinTitleRepository(client, "user-1", profile),
            JellyfinCollectionRepository(client, "user-1", profile),
            userData,
            { it.serverFailure() },
        )

    private suspend fun PersonViewModel.ready(until: (PersonPageUi) -> Boolean = { true }) =
        (withTimeout(TIMEOUT) { state.first { it is PersonUiState.Ready && until(it.person) } } as PersonUiState.Ready).person

    private suspend fun CollectionViewModel.ready(until: (CollectionPageUi) -> Boolean = { true }) =
        (
            withTimeout(
                TIMEOUT,
            ) { state.first { it is CollectionUiState.Ready && until(it.collection) } } as CollectionUiState.Ready
        ).collection

    private fun seedPerson() {
        jellyfin.browse.titles.add(
            FakeTitle(
                "p1",
                "Sigourney Weaver",
                "people",
                "Person",
                extra = """"Overview":"Actor.","PremiereDate":"1949-10-08T00:00:00Z","ProductionLocations":["New York"]""",
            ),
        )
        listOf(
            "a",
            "b",
            "c",
        ).forEach { jellyfin.browse.titles.add(FakeTitle(it, "Film $it", "lib-movies", year = 2000, personIds = listOf("p1"))) }
    }

    @Test
    fun `a person's facts and biography are mapped, and a blank place or biography is left out`() {
        val page =
            personPageUi(PersonDetail("p", "Name", " ", "1949-10-08", null, " ", "t"), emptyList(), CreditsState.Ready(false))
        assertEquals(TitleImage("p", ImageKind.Primary, "t"), page.photo)
        assertNull(page.biography)
        assertEquals(PersonFacts("1949-10-08", null, null), page.facts)
    }

    @Test
    fun `a collection counts what is watched and gives each part its runtime`() {
        val parts =
            listOf(
                BrowseItem("a", "A", ItemKind.Movie, runTimeTicks = 600_000_000L * 90, userData = ItemUserData(played = true)),
                BrowseItem("b", "B", ItemKind.Movie),
            )
        val detail =
            com.cooper.homelab.ephyra.browse.TitleDetail(
                item = BrowseItem("box", "Box", ItemKind.BoxSet, imageTags = mapOf(ImageKind.Primary to "p")),
                originalTitle = null,
                overview = "All of them.",
                taglines = emptyList(),
                genres = emptyList(),
                studios = emptyList(),
                criticRating = null,
                premiereDate = null,
                endDate = null,
                status = null,
                externalIds = emptyMap(),
                people = emptyList(),
                versions = emptyList(),
                chapters = emptyList(),
                seasonName = null,
            )
        val page = collectionPageUi(detail, parts)
        assertEquals(1, page.watchedCount)
        assertEquals(listOf(90, null), page.parts.map { it.runtimeMinutes })
        assertEquals("All of them.", page.overview)
        assertEquals(TitleImage("box", ImageKind.Primary, "p"), page.poster)
    }

    @Test
    fun `a person loads, then their titles page in as asked for`() =
        runBlocking {
            seedPerson()
            val model = personModel().also { it.start("p1") }
            val first = model.ready { it.credits.size == 2 && it.creditsState is CreditsState.Ready }
            assertEquals("Sigourney Weaver", first.name)
            assertEquals("New York", first.facts.birthPlace)
            assertEquals(CreditsState.Ready(hasMore = true), first.creditsState)
            model.loadMore()
            val all = model.ready { it.credits.size == 3 }
            assertEquals(listOf("a", "b", "c"), all.credits.map { it.id })
            assertEquals(CreditsState.Ready(hasMore = false), all.creditsState)
            val reads = jellyfin.browse.itemRequests.count { it.url.queryParameter("personIds") == "p1" }
            model.loadMore()
            Thread.sleep(SETTLE_MILLIS)
            assertEquals(reads, jellyfin.browse.itemRequests.count { it.url.queryParameter("personIds") == "p1" })
        }

    @Test
    fun `a page of titles that fails keeps the ones shown, and the next ask retries it`() =
        runBlocking {
            seedPerson()
            val model = personModel().also { it.start("p1") }
            model.ready { it.credits.size == 2 }
            jellyfin.status["/Items"] = 500
            model.loadMore()
            val failed = model.ready { it.creditsState is CreditsState.Failed }
            assertEquals(ServerFailure.ServerError, (failed.creditsState as CreditsState.Failed).failure)
            assertEquals(2, failed.credits.size)
            jellyfin.status.clear()
            model.loadMore()
            assertEquals(3, model.ready { it.credits.size == 3 }.credits.size)
        }

    @Test
    fun `a person the server does not have is a failure, and a retry reads again`() =
        runBlocking {
            seedPerson()
            val model = personModel().also { it.start("nobody") }
            assertTrue(withTimeout(TIMEOUT) { model.state.first { it is PersonUiState.Failed } } is PersonUiState.Failed)
            model.start("p1")
            assertEquals("Sigourney Weaver", model.ready().name)
        }

    @Test
    fun `a title watched in another app shows on the person's cards`() =
        runBlocking {
            seedPerson()
            val model = personModel(pageSize = 5).also { it.start("p1") }
            model.ready { it.credits.size == 3 }
            Thread.sleep(SETTLE_MILLIS)
            socket.emit(listOf(UserDataChange("user-1", "b", played = true)))
            assertTrue(
                model
                    .ready { page -> page.credits.first { it.id == "b" }.played }
                    .credits
                    .first { it.id == "b" }
                    .played,
            )
        }

    private fun seedCollection() {
        jellyfin.browse.titles.add(FakeTitle("box", "Alien Collection", "lib-box", "BoxSet", extra = """"Overview":"All of them.""""))
        jellyfin.browse.titles.add(FakeTitle("a2", "Aliens", "box", year = 1986))
        jellyfin.browse.titles.add(FakeTitle("a1", "Alien", "box", year = 1979, played = true))
    }

    @Test
    fun `a collection loads its parts in release order with the watched count`() =
        runBlocking {
            seedCollection()
            val model = collectionModel().also { it.start("box") }
            val page = model.ready()
            assertEquals("Alien Collection", page.name)
            assertEquals(listOf("a1", "a2"), page.parts.map { it.card.id })
            assertEquals(1, page.watchedCount)
        }

    @Test
    fun `a collection that fails is a failure and a retry reads it again`() =
        runBlocking {
            seedCollection()
            jellyfin.status["/Items/box"] = 500
            val model = collectionModel().also { it.start("box") }
            assertTrue(withTimeout(TIMEOUT) { model.state.first { it is CollectionUiState.Failed } } is CollectionUiState.Failed)
            jellyfin.status.clear()
            model.retry()
            assertEquals(2, model.ready().parts.size)
        }

    @Test
    fun `a part watched in another app updates the count`() =
        runBlocking {
            seedCollection()
            val model = collectionModel().also { it.start("box") }
            model.ready()
            Thread.sleep(SETTLE_MILLIS)
            socket.emit(listOf(UserDataChange("user-1", "a2", played = true)))
            assertEquals(2, model.ready { it.watchedCount == 2 }.watchedCount)
        }

    private companion object {
        const val TIMEOUT = 15_000L
        const val SETTLE_MILLIS = 500L
    }
}
