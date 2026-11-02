package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LiveChannel
import com.cooper.binge.companion.jellyfin.server.ParentalRating
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UserChoicesViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun UserChoicesViewModel.until(check: (UserChoices) -> Boolean) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    @Test
    fun `a section reads what it picks from when it opens, and only then`() {
        val vm = made(UserChoicesViewModel(connection))
        vm.ensure(null)
        vm.ensure(UserSection.Playback)
        assertEquals(UserChoices(), vm.state.value)
        vm.ensure(UserSection.Parental)
        val ratings = (vm.until { it.ratings is Loaded.Ready }.ratings as Loaded.Ready).value
        assertEquals(listOf(ParentalRating("G", 1, 0), ParentalRating("PG", 5, 2), ParentalRating("R", 9)), ratings)
        vm.ensure(UserSection.LiveTv)
        assertEquals(
            listOf(LiveChannel("ch-1", "News"), LiveChannel("ch-2", "Sport")),
            (vm.until { it.channels is Loaded.Ready }.channels as Loaded.Ready).value,
        )
        vm.ensure(UserSection.Devices)
        assertTrue(vm.until { it.devices is Loaded.Ready }.devices is Loaded.Ready)
        // Opening it again doesn't ask again.
        val asked = jellyfin.hits("/Localization/ParentalRatings")
        vm.ensure(UserSection.Parental)
        assertEquals(asked, jellyfin.hits("/Localization/ParentalRatings"))
    }

    @Test
    fun `a list that can't be read says why, and Try again reads it again`() {
        val vm = made(UserChoicesViewModel(connection))
        jellyfin.status["/LiveTv/Channels"] = 500
        vm.ensure(UserSection.LiveTv)
        assertEquals(Loaded.Failed(ConsoleFailure.Refused), vm.until { it.channels is Loaded.Failed }.channels)
        jellyfin.status.remove("/LiveTv/Channels")
        vm.retry(UserSection.LiveTv)
        assertTrue(vm.until { it.channels is Loaded.Ready }.channels is Loaded.Ready)
        jellyfin.status["/Localization/ParentalRatings"] = 500
        vm.retry(UserSection.Parental)
        assertTrue(vm.until { it.ratings is Loaded.Failed }.ratings is Loaded.Failed)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
