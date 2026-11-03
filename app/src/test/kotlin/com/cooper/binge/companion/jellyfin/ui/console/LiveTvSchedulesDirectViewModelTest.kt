package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.SdCountry
import com.cooper.binge.companion.jellyfin.server.SdLineup
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveTvSchedulesDirectViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()
    private var changed = 0

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun vm() = made(LiveTvSchedulesDirectViewModel(connection) { changed++ })

    private fun LiveTvSchedulesDirectViewModel.until(check: (SdUiState) -> Boolean) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private val usa = SdCountry("USA", "United States", "North America")

    private fun posts() = jellyfin.requests.filter { it.url.encodedPath == PROVIDERS && it.method == "POST" }

    private fun providers() =
        kotlinx.serialization.json.Json
            .parseToJsonElement(jellyfin.liveTvConfiguration)
            .jsonObject
            .getValue("ListingProviders")
            .jsonArray

    private fun signedIn(vm: LiveTvSchedulesDirectViewModel): String {
        vm.start()
        vm.signIn("carol", "hunter2")
        return (vm.until { it.step is SdStep.Location }.step as SdStep.Location).providerId
    }

    @Test
    fun `the account is saved with the password hashed in the body, and never in the address`() {
        val vm = vm()
        signedIn(vm)
        val post = posts().single()
        assertEquals("true", post.url.queryParameter("validateLogin"))
        assertNull(post.url.queryParameter("pw"))
        assertFalse(post.url.toString().contains("hunter2"))
        val body = jellyfin.lastBodyText(PROVIDERS)
        assertFalse(body.contains("hunter2"))
        assertEquals(
            "carol",
            jellyfin
                .lastBody(PROVIDERS)
                .getValue("Username")
                .jsonPrimitive.content,
        )
        assertEquals(
            40,
            jellyfin
                .lastBody(PROVIDERS)
                .getValue("Password")
                .jsonPrimitive.content.length,
        )
        assertEquals(1, changed)
    }

    @Test
    fun `the countries are read once the account is saved, and a country is picked`() {
        val vm = vm()
        signedIn(vm)
        val location = vm.until { (it.step as? SdStep.Location)?.countries != null }.step as SdStep.Location
        assertEquals(listOf("United Kingdom", "United States"), location.countries?.map { it.name })
        vm.chooseCountry(true)
        assertTrue((vm.until { (it.step as? SdStep.Location)?.choosing == true }.step as SdStep.Location).choosing)
        vm.pickCountry(usa)
        val picked = vm.until { (it.step as? SdStep.Location)?.country == usa }.step as SdStep.Location
        assertFalse(picked.choosing)
    }

    @Test
    fun `the lineups are read with the saved account's id, the country and the postal code, and one is added with validateListings`() {
        val vm = vm()
        val id = signedIn(vm)
        vm.until { (it.step as? SdStep.Location)?.countries != null }
        vm.pickCountry(usa)
        vm.findLineups(" 10001 ")
        val lineups = vm.until { (it.step as? SdStep.Lineups)?.lineups != null }.step as SdStep.Lineups
        val asked = jellyfin.requests.last { it.url.encodedPath == "/LiveTv/ListingProviders/Lineups" }.url
        assertEquals(id, asked.queryParameter("id"))
        assertEquals("USA", asked.queryParameter("country"))
        assertEquals("10001", asked.queryParameter("location"))
        assertEquals(listOf("Comcast Digital", "Dish Network"), lineups.lineups?.map { it.name })
        vm.addLineup(SdLineup("USA-NY12345-X", "Comcast Digital"))
        vm.until { it.step == null && !it.saving }
        val last = posts().last()
        assertEquals("true", last.url.queryParameter("validateListings"))
        val sent = jellyfin.lastBody(PROVIDERS)
        assertEquals("USA-NY12345-X", sent.getValue("ListingsId").jsonPrimitive.content)
        assertEquals("USA", sent.getValue("Country").jsonPrimitive.content)
        assertEquals("10001", sent.getValue("ZipCode").jsonPrimitive.content)
        assertEquals("carol", sent.getValue("Username").jsonPrimitive.content)
        assertEquals(2, changed)
    }

    @Test
    fun `leaving the setup keeps the saved account, which the list then shows as not finished`() {
        val vm = vm()
        signedIn(vm)
        vm.close()
        assertNull(vm.until { it.step == null }.step)
        val account = providers().last().jsonObject
        assertEquals("carol", account.getValue("Username").jsonPrimitive.content)
        assertFalse(account.containsKey("ListingsId"))
        assertEquals(0, jellyfin.requests.count { it.method == "DELETE" })
    }

    @Test
    fun `an unfinished account is picked up again at its location step`() {
        val vm = vm()
        vm.locate("g2")
        val location = vm.until { (it.step as? SdStep.Location)?.countries != null }.step as SdStep.Location
        assertEquals("g2", location.providerId)
    }

    @Test
    fun `a lineup list that comes back empty is shown as empty, and back returns to the location with the country`() {
        jellyfin.sdLineups = "[]"
        val vm = vm()
        signedIn(vm)
        vm.until { (it.step as? SdStep.Location)?.countries != null }
        vm.pickCountry(usa)
        vm.findLineups("99999")
        assertEquals(emptyList<SdLineup>(), (vm.until { (it.step as? SdStep.Lineups)?.lineups != null }.step as SdStep.Lineups).lineups)
        vm.backToLocation()
        val back = vm.until { it.step is SdStep.Location }.step as SdStep.Location
        assertEquals(usa, back.country)
        assertEquals(2, back.countries?.size)
    }

    @Test
    fun `an account the server will not keep says so and stays on the account step`() {
        jellyfin.listingSaveStatus = 500
        val vm = vm()
        vm.start()
        vm.signIn("carol", "hunter2")
        val failed = vm.until { it.problem != null }
        assertEquals(SdProblem.AccountNotSaved, failed.problem)
        assertEquals(SdStep.Account, failed.step)
        assertFalse(failed.saving)
        assertEquals(0, changed)
    }

    @Test
    fun `a lineup the server will not add keeps the lineup step and says so`() {
        val vm = vm()
        signedIn(vm)
        vm.until { (it.step as? SdStep.Location)?.countries != null }
        vm.pickCountry(usa)
        vm.findLineups("10001")
        vm.until { (it.step as? SdStep.Lineups)?.lineups != null }
        jellyfin.listingSaveStatus = 500
        vm.addLineup(SdLineup("USA-NY12345-X", "Comcast Digital"))
        val failed = vm.until { it.problem != null }
        assertEquals(SdProblem.LineupNotAdded, failed.problem)
        assertTrue(failed.step is SdStep.Lineups)
        assertFalse(failed.saving)
    }

    @Test
    fun `a lineup read the server fails goes back to the location step with a problem`() {
        jellyfin.sdLineupsStatus = 500
        val vm = vm()
        signedIn(vm)
        vm.until { (it.step as? SdStep.Location)?.countries != null }
        vm.pickCountry(usa)
        vm.findLineups("10001")
        val failed = vm.until { it.failed != null }
        assertNull(failed.problem)
        assertEquals(ConsoleFailure.Refused, failed.failed)
        assertTrue(failed.step is SdStep.Location)
    }

    @Test
    fun `an account the user is not allowed to save is not blamed on the details, and an unreachable server is said to be unreachable`() {
        jellyfin.listingSaveStatus = 401
        val denied = vm()
        denied.start()
        denied.signIn("carol", "hunter2")
        val refused = denied.until { it.failed != null }
        assertNull(refused.problem)
        assertEquals(ConsoleFailure.Refused, refused.failed)
        assertEquals(SdStep.Account, refused.step)

        val unreachable = vm()
        jellyfin.close()
        unreachable.start()
        unreachable.signIn("carol", "hunter2")
        val down = unreachable.until { it.failed != null }
        assertNull(down.problem)
        assertEquals(ConsoleFailure.Unreachable, down.failed)
    }

    @Test
    fun `a blank username or an empty password sends nothing`() {
        val vm = vm()
        vm.start()
        vm.signIn("  ", "x")
        vm.signIn("carol", "")
        assertEquals(0, posts().size)
    }

    private companion object {
        const val PROVIDERS = "/LiveTv/ListingProviders"
        const val TIMEOUT_MILLIS = 10_000L
    }
}
