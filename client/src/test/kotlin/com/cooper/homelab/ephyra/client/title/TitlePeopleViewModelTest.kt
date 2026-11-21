package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinVersion
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cast and crew page against a fake server: the title's people split into cast and crew, a failure, and a retry. */
class TitlePeopleViewModelTest {
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
    private val profile = JellyfinServerProfile(JellyfinVersion(10, 11, 6))

    @After
    fun close() {
        scope.cancel()
        jellyfin.close()
    }

    private fun viewModel() =
        TitlePeopleViewModel(
            scope = scope,
            dispatcher = Dispatchers.Default,
            titles = JellyfinTitleRepository(client, "user-1", profile),
            classify = { it.serverFailure() },
        )

    private suspend fun TitlePeopleViewModel.ready(): TitlePeopleUi =
        (withTimeout(TIMEOUT) { state.first { it is TitlePeopleUiState.Ready } } as TitlePeopleUiState.Ready).people

    @Test
    fun `the title's people are split into cast and crew, in the server's order`() =
        runBlocking {
            jellyfin.browse.titles.add(
                FakeTitle(
                    "m1",
                    "Alien",
                    "lib-movies",
                    extra =
                        """
                        "People":[{"Id":"a","Name":"Weaver","Role":"Ripley","Type":"Actor"},
                          {"Id":"d","Name":"Scott","Type":"Director"},{"Id":"g","Name":"Guest","Type":"GuestStar"},
                          {"Id":"w","Name":"O'Bannon","Type":"Writer"},{"Id":"x","Name":"Odd","Type":"Lyricist"}]
                        """.trimIndent(),
                ),
            )
            val model = viewModel().also { it.start("m1") }
            val people = model.ready()
            assertEquals("Alien", people.title)
            assertEquals(listOf("a", "g"), people.cast.map { it.id })
            assertEquals(listOf("d", "w"), people.crew.map { it.id })
            assertEquals(listOf(CrewJob.Director, CrewJob.Writer), people.crew.map { it.job })
        }

    @Test
    fun `a title with nobody listed is ready with both lists empty`() =
        runBlocking {
            jellyfin.browse.titles.add(FakeTitle("m2", "Bare", "lib-movies"))
            val people = viewModel().also { it.start("m2") }.ready()
            assertTrue(people.cast.isEmpty() && people.crew.isEmpty())
        }

    @Test
    fun `a title the server refuses is a failure and a retry reads it again`() =
        runBlocking {
            jellyfin.browse.titles.add(FakeTitle("m1", "Alien", "lib-movies"))
            jellyfin.status["/Items/m1"] = 500
            val model = viewModel().also { it.start("m1") }
            val failed = withTimeout(TIMEOUT) { model.state.first { it is TitlePeopleUiState.Failed } } as TitlePeopleUiState.Failed
            assertEquals(ServerFailure.ServerError, failed.failure)
            jellyfin.status.clear()
            model.retry()
            assertEquals("Alien", model.ready().title)
        }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
