package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.FetcherKind
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a metadata-defaults screen reports is said on that screen only: a refusal left on a provider's page is gone by
 * the time Back shows the type's page, and the type's refusal is not replayed on the provider page it opens (#395).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class MetadataRouteBackTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private var item by mutableStateOf(metadataTypeItem("Movie"))

    @Before
    fun signIn() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.serverConfiguration =
            """{"ServerName":"truenas","MetadataOptions":[{"ItemType":"Movie","DisabledMetadataFetchers":[],"DisabledImageFetchers":[]}]}"""
        jellyfin.availableOptionsByType["movies"] =
            """{"MetadataSavers":[],"MetadataReaders":[],"TypeOptions":[{"Type":"Movie","MetadataFetchers":""" +
            """[{"Name":"TheMovieDb","DefaultEnabled":true}],"ImageFetchers":[]}]}"""
    }

    @After
    fun stop() = jellyfin.close()

    private fun show() =
        compose.setContent {
            JellyfinTheme {
                MetadataItemRoute(
                    connection,
                    item,
                    openSubItem = { item = it },
                    onBack = { item = metadataTypeItem("Movie") },
                )
            }
        }

    private fun waitFor(text: String) =
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun notSaved() = compose.onAllNodes(hasText("Not saved", substring = true))

    @Test
    fun `a refusal on a provider's page is gone when Back shows the type's page`() {
        item = metadataFetcherItem("Movie", OpenFetcher(FetcherKind.Metadata, "TheMovieDb"))
        show()
        waitFor("Use this provider")
        jellyfin.status["/System/Configuration"] = 500
        compose.onNodeWithText("Use this provider").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Not saved")
        compose.onNode(hasContentDescription("Back")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MILLIS) { item == metadataTypeItem("Movie") }
        compose.waitForIdle()
        notSaved().assertCountEquals(0)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
