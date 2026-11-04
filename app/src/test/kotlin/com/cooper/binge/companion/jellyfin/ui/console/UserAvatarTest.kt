package com.cooper.binge.companion.jellyfin.ui.console

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserAvatarTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a late picture and a fallback keep the row's dimensions`() {
        var picture by mutableStateOf<Bitmap?>(null)
        compose.setContent {
            JellyfinTheme {
                Box(Modifier.testTag("row")) {
                    ItemGroup(
                        title = null,
                        rows = listOf(ListItem(icon = Icons.Outlined.Person, label = "Ana", leadingContent = { UserAvatar("Ana", picture) })),
                    )
                }
            }
        }
        val initials = compose.onNodeWithTag("row").getBoundsInRoot()
        compose.runOnIdle { picture = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888) }
        assertEquals(initials, compose.onNodeWithTag("row").getBoundsInRoot())
        compose.runOnIdle { picture = null }
        assertEquals(initials, compose.onNodeWithTag("row").getBoundsInRoot())
    }
}
