package com.cooper.binge.companion.jellyfin.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.ui.console.LocalUserPictures
import com.cooper.binge.companion.jellyfin.ui.console.UserPictureSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The signed-in user's own picture on the home: read by the tag `/Users/Me` gave, again when the tag changes. */
@RunWith(RobolectricTestRunner::class)
class OwnAvatarTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val session =
        JellyfinSession("http://192.168.1.2:8096", "Living room", "a1", "10.11.6", "u1", "sam", "not-a-token")

    private val reads = mutableListOf<Pair<String, String?>>()
    private val pictures =
        object : UserPictureSource {
            override suspend fun read(user: ServerUser): Bitmap? {
                reads += user.id to user.imageTag
                return null
            }
        }

    private fun state(tag: String?) =
        UiState.SignedIn(
            session,
            titles = null,
            account =
                JellyfinAccount("sam", Json.parseToJsonElement("""{"IsAdministrator":true}""").jsonObject, imageTag = tag),
        )

    private var current by mutableStateOf(state("first"))

    private fun show() =
        compose.setContent {
            CompositionLocalProvider(LocalUserPictures provides pictures) {
                JellyfinTheme { JellyfinRoot(current, Actions()) }
            }
        }

    @Test
    fun `the home reads the signed-in user's own picture by the tag the server gave, for the top bar and the account row`() {
        show()
        compose.waitForIdle()
        // The administrator's avatar and the account row each ask; both for this user and this tag.
        assertEquals(setOf("u1" to "first"), reads.toSet())
    }

    @Test
    fun `a new tag, because the user set or removed the picture, is read again without signing in`() {
        show()
        compose.waitForIdle()
        current = state(null)
        compose.waitForIdle()
        assertEquals(setOf("u1" to "first", "u1" to null), reads.toSet())
        current = state("second")
        compose.waitForIdle()
        assertEquals(setOf("u1" to "first", "u1" to null, "u1" to "second"), reads.toSet())
    }

    @Test
    fun `without a picture source the home shows initials and does not fail`() {
        compose.setContent { JellyfinTheme { JellyfinRoot(current, Actions()) } }
        compose.waitForIdle()
        assertEquals(emptyList<Pair<String, String?>>(), reads)
    }
}
