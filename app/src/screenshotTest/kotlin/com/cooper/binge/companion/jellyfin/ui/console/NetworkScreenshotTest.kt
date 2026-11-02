package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStateLightPreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.NetworkImpact
import com.cooper.binge.companion.jellyfin.server.NetworkSettings
import com.cooper.binge.companion.jellyfin.server.networkKeys

private val newest = networkKeys(JellyfinServerProfile.from("10.10.7"))
private val oldest = networkKeys(JellyfinServerProfile.from("10.8.13"))

private val everyKey =
    setOf(
        "InternalHttpPort",
        "InternalHttpsPort",
        "PublicHttpPort",
        "PublicHttpsPort",
        "BaseUrl",
        "LocalNetworkAddresses",
        "EnableHttps",
        "RequireHttps",
        "CertificatePath",
        "EnableRemoteAccess",
        "RemoteIPFilter",
        "IsRemoteIPFilterBlacklist",
        "EnableIPv4",
        "EnableIPv6",
        "KnownProxies",
        "LocalNetworkSubnets",
        "AutoDiscovery",
        "EnableUPnP",
        "EnablePublishedServerUriByRequest",
        "PublishedServerUriBySubnet",
        "IgnoreVirtualInterfaces",
        "VirtualInterfaceNames",
    )

private val preview =
    NetworkSettings(
        present = everyKey,
        baseUrl = "/jellyfin",
        enableHttps = true,
        certificatePath = "/config/cert.pfx",
        knownProxies = listOf("10.0.0.2"),
        localSubnets = listOf("192.168.1.0/24", "10.0.0.0/8"),
        virtualNames = listOf("veth*"),
    )

private val ready = NetworkUiState.Ready(preview, newest)

/** Networking: the page across the matrix, an older server's, a change not yet saved, the confirm, each sheet, then the other states. */
class NetworkScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun network() = NetworkScreen(ready, NetworkActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun olderServer() = NetworkScreen(NetworkUiState.Ready(preview.copy(present = everyKey - "EnableUPnP"), oldest), NetworkActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun unsaved() =
        NetworkScreen(
            ready.copy(settings = preview.copy(httpPort = 9000, requireHttps = true), draft = setOf("InternalHttpPort", "RequireHttps")),
            NetworkActions(),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun committing() = NetworkScreen(ready.copy(draft = setOf("RequireHttps"), committing = true), NetworkActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun restart() = NetworkScreen(ready.copy(restartNeeded = true), NetworkActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun confirmSevere() =
        Surface {
            NetworkConfirm(
                setOf(NetworkImpact.Port, NetworkImpact.BindAddress),
                "http://192.168.1.5:8096",
                NetworkActions(),
                inWindow = false,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun confirmSoft() =
        Surface { NetworkConfirm(setOf(NetworkImpact.RemoteAccess), "https://media.example", NetworkActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @Composable
    fun portSheet() = Surface { NetworkNumberSheet(NetworkNumber.HttpPort, ready, NetworkActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun baseUrlSheet() = Surface { NetworkTextSheet(NetworkText.BaseUrl, preview, NetworkActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun bindSheet() = Surface { NetworkItemsSheet(NetworkList.BindAddresses, preview, NetworkActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun subnetsSheet() = Surface { NetworkItemsSheet(NetworkList.LocalSubnets, preview, NetworkActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = NetworkScreen(NetworkUiState.Loading, NetworkActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = NetworkScreen(NetworkUiState.Failed(ConsoleFailure.Refused), NetworkActions())
}
