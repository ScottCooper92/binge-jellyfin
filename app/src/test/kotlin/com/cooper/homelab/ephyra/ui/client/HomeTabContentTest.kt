package com.cooper.homelab.ephyra.ui.client

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.home.HomeRowKey
import com.cooper.homelab.ephyra.client.home.HomeUiState
import com.cooper.homelab.ephyra.client.home.homeRow
import com.cooper.homelab.ephyra.client.home.homeState
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Home tab with its state in hand: a tap goes to the right callback, and a failure offers Try again. */
@RunWith(RobolectricTestRunner::class)
class HomeTabContentTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun movie(
        id: String,
        name: String,
    ) = BrowseItem(id, name, ItemKind.Movie, year = 2020)

    private fun only(
        key: HomeRowKey,
        item: BrowseItem,
    ): HomeUiState = homeState(listOf(homeRow(key, listOf(item))!!), pending = 0)

    private fun show(
        state: HomeUiState,
        opened: MutableList<String> = mutableListOf(),
        resumed: MutableList<String> = mutableListOf(),
        refreshed: MutableList<String> = mutableListOf(),
    ) = compose.setContent {
        JellyfinTheme {
            HomeTabContent(
                state = state,
                refreshing = false,
                imageUrl = { _, _ -> null },
                onOpen = { opened += it.id },
                onResume = { resumed += it.id },
                onRefresh = { refreshed += "refresh" },
            )
        }
    }

    @Test
    fun `a Latest card opens its page`() {
        val opened = mutableListOf<String>()
        val resumed = mutableListOf<String>()
        show(only(HomeRowKey.Latest("lib", "Movies"), movie("m2", "Brazil")), opened, resumed)
        compose.onNodeWithText("Brazil").performClick()
        assertEquals(listOf("m2"), opened)
        assertEquals(emptyList<String>(), resumed)
    }

    @Test
    fun `a Continue watching card resumes`() {
        val opened = mutableListOf<String>()
        val resumed = mutableListOf<String>()
        show(only(HomeRowKey.ContinueWatching, movie("m1", "Alien")), opened, resumed)
        compose.onNodeWithText("Alien").performClick()
        assertEquals(listOf("m1"), resumed)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun `a failure that another go can fix offers Try again, which refreshes`() {
        val refreshed = mutableListOf<String>()
        show(HomeUiState.Error(ServerFailure.Unreachable()), refreshed = refreshed)
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("refresh"), refreshed)
    }

    @Test
    fun `a failure no retry fixes offers no Try again`() {
        show(HomeUiState.Error(ServerFailure.NotPermitted))
        compose.onAllNodesWithText("Try again").assertCountEquals(0)
    }
}
