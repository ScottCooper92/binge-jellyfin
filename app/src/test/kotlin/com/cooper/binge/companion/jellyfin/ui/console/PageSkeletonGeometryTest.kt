package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.BackupOptions
import com.cooper.binge.companion.jellyfin.server.ChannelMappingOptions
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.LogFile
import com.cooper.binge.companion.jellyfin.server.ProviderChannel
import com.cooper.binge.companion.jellyfin.server.ServerBackup
import com.cooper.binge.companion.jellyfin.server.ServerInfo
import com.cooper.binge.companion.jellyfin.server.TunerChannel
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The remaining pages that load as a skeleton hold their first group where the settled page puts it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PageSkeletonGeometryTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val now = 1_793_534_400_000L

    private fun ComposeContentTestRule.holdsFirstGroup(page: @Composable (resolved: Boolean) -> Unit) =
        assertSkeletonReservesGeometry(
            listOf(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
            checkStartEdge = true,
        ) { resolved -> JellyfinTheme { page(resolved) } }

    @Test
    fun `backups`() =
        compose.holdsFirstGroup { resolved ->
            BackupsScreen(
                state =
                    if (resolved) {
                        BackupsUiState.Ready(listOf(ServerBackup("jellyfin-backup.zip", now, "10.11.6.0", BackupOptions())))
                    } else {
                        BackupsUiState.Loading
                    },
                actions = BackupsActions(),
                now = now,
            )
        }

    @Test
    fun `channel mappings`() =
        compose.holdsFirstGroup { resolved ->
            val options =
                ChannelMappingOptions(
                    "Freeview XMLTV",
                    listOf(ProviderChannel("p1", "BBC One")),
                    listOf(TunerChannel("c1", "101 BBC One HD", "p1")),
                )
            ChannelMappingsScreen(
                state = if (resolved) ChannelMappingsUiState.Ready(options, null, "") else ChannelMappingsUiState.Loading,
                actions = ChannelMappingsActions(),
            )
        }

    @Test
    fun `live tv recording`() =
        compose.holdsFirstGroup { resolved ->
            val config =
                Json
                    .parseToJsonElement(
                        """{"GuideDays":7,"RecordingPath":"/media/recordings","PrePaddingSeconds":60}""",
                    ).jsonObject
            LiveTvRecordingScreen(
                state = if (resolved) LiveTvRecordingUiState.Ready(config) else LiveTvRecordingUiState.Loading,
                actions = LiveTvRecordingActions(),
            )
        }

    @Test
    fun `logs`() =
        compose.holdsFirstGroup { resolved ->
            LogsScreen(
                state = if (resolved) LogsUiState.Ready(listOf(LogFile("log_20261031.log", 2048L, now))) else LogsUiState.Loading,
                actions = LogsActions(),
            )
        }

    @Test
    fun `server`() =
        compose.holdsFirstGroup { resolved ->
            ServerScreen(
                state =
                    if (resolved) {
                        ServerUiState.Ready(ServerInfo("truenas", "10.11.6", "http://nas:8096", "X64", "Linux", false, false, true))
                    } else {
                        ServerUiState.Loading
                    },
                actions = ServerActions(),
            )
        }

    @Test
    fun `transcoding`() =
        compose.holdsFirstGroup { resolved ->
            val encoding = Json.parseToJsonElement("""{"HardwareAccelerationType":"none"}""").jsonObject
            TranscodingScreen(
                state =
                    if (resolved) {
                        TranscodingUiState.Ready(
                            encoding,
                            Json.parseToJsonElement("{}").jsonObject,
                        )
                    } else {
                        TranscodingUiState.Loading
                    },
                actions = TranscodingActions(),
                profile = JellyfinServerProfile.from("10.11.6"),
            )
        }
}
