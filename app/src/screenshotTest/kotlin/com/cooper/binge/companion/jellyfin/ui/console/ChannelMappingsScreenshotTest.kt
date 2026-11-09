package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinWidePreview
import com.cooper.binge.companion.jellyfin.server.ChannelMappingOptions
import com.cooper.binge.companion.jellyfin.server.ProviderChannel
import com.cooper.binge.companion.jellyfin.server.TunerChannel

private val options =
    ChannelMappingOptions(
        providerName = "Freeview XMLTV",
        providerChannels =
            listOf(
                ProviderChannel("p1", "BBC One"),
                ProviderChannel("p2", "BBC Two"),
                ProviderChannel("p3", "ITV"),
                ProviderChannel("p4", "Channel 4"),
            ),
        tunerChannels =
            listOf(
                TunerChannel("c1", "101 BBC One HD", "p1"),
                TunerChannel("c2", "102 BBC Two HD", "p2"),
                TunerChannel("c3", "103 ITV HD", null),
                TunerChannel("c4", "104 ITV2", "p1"),
                TunerChannel("c5", "105 Channel 4 HD", null),
            ),
    )

private fun ready(
    selected: String? = null,
    query: String = "",
) = ChannelMappingsUiState.Ready(options, selected, query)

/** Channel mappings: the list across the matrix, the tuner channels in the phone's sheet and beside the list on a wide window. */
class ChannelMappingsScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun list() = ChannelMappingsScreen(ready(), ChannelMappingsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = ChannelMappingsScreen(ChannelMappingsUiState.Loading, ChannelMappingsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = ChannelMappingsScreen(ChannelMappingsUiState.Failed(ConsoleFailure.Unreachable), ChannelMappingsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun noChannels() = ChannelMappingsScreen(ChannelMappingsUiState.Ready(options.copy(providerChannels = emptyList())), ChannelMappingsActions())

    /** The phone's sheet, as its content: the search, the channel it is paired with ticked, and one paired with another channel. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun tunerChannels() =
        Surface { TunerChannelChoices(ready("p1"), options.providerChannels.first(), ChannelMappingsActions(), PaddingValues()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun tunerChannelsSearched() =
        Surface { TunerChannelChoices(ready("p3", "itv"), options.providerChannels[2], ChannelMappingsActions(), PaddingValues()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun tunerChannelsNoMatch() =
        Surface { TunerChannelChoices(ready("p3", "zzz"), options.providerChannels[2], ChannelMappingsActions(), PaddingValues()) }

    /** From 600 dp wide: the list and the tuner channels of the open channel, side by side. */
    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun twoPanes() = ChannelMappingsScreen(ready("p3"), ChannelMappingsActions())

    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun twoPanesNothingOpen() = ChannelMappingsScreen(ready(), ChannelMappingsActions())
}
