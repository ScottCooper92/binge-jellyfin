package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The names the server's network configuration (`/System/Configuration/network`) uses, which differ by release: 10.8 calls the
 * ports `HttpServerPortNumber`, `HttpsPortNumber` and `PublicPort`, and IPv4 and IPv6 `EnableIPV4` and `EnableIPV6`; from 10.9 they are
 * `InternalHttpPort`, `InternalHttpsPort`, `PublicHttpPort`, `EnableIPv4` and `EnableIPv6`. The base URL is in this document
 * on every release.
 */
data class NetworkKeys(
    val httpPort: String,
    val httpsPort: String,
    val publicHttpPort: String,
    val publicHttpsPort: String,
    val ipv4: String,
    val ipv6: String,
    /** Whether this release has the UPnP port mapping switch, which 10.11 marks no longer supported. */
    val upnp: Boolean,
)

fun networkKeys(profile: JellyfinServerProfile): NetworkKeys =
    if (profile.hasInternalPortNames) {
        NetworkKeys(
            "InternalHttpPort",
            "InternalHttpsPort",
            "PublicHttpPort",
            "PublicHttpsPort",
            "EnableIPv4",
            "EnableIPv6",
            profile.hasUpnpOption,
        )
    } else {
        NetworkKeys("HttpServerPortNumber", "HttpsPortNumber", "PublicPort", "PublicHttpsPort", "EnableIPV4", "EnableIPV6", true)
    }

/** The keys the two releases share. */
internal const val NETWORK_ENABLE_HTTPS = "EnableHttps"
internal const val NETWORK_REQUIRE_HTTPS = "RequireHttps"
internal const val NETWORK_CERTIFICATE_PATH = "CertificatePath"

/** The certificate's password: a secret. It is never read into [NetworkSettings] or shown; it goes back to the server as it came, or as the person typed a new one. */
internal const val NETWORK_CERTIFICATE_PASSWORD = "CertificatePassword"
internal const val NETWORK_BASE_URL = "BaseUrl"
internal const val NETWORK_BIND_ADDRESSES = "LocalNetworkAddresses"
internal const val NETWORK_REMOTE_ACCESS = "EnableRemoteAccess"
internal const val NETWORK_REMOTE_IP_FILTER = "RemoteIPFilter"
internal const val NETWORK_IP_FILTER_BLACKLIST = "IsRemoteIPFilterBlacklist"
internal const val NETWORK_KNOWN_PROXIES = "KnownProxies"
internal const val NETWORK_LOCAL_SUBNETS = "LocalNetworkSubnets"
internal const val NETWORK_AUTO_DISCOVERY = "AutoDiscovery"
internal const val NETWORK_UPNP = "EnableUPnP"
internal const val NETWORK_PUBLISHED_BY_REQUEST = "EnablePublishedServerUriByRequest"
internal const val NETWORK_PUBLISHED_BY_SUBNET = "PublishedServerUriBySubnet"
internal const val NETWORK_IGNORE_VIRTUAL = "IgnoreVirtualInterfaces"
internal const val NETWORK_VIRTUAL_NAMES = "VirtualInterfaceNames"

/**
 * The server's network options as the page shows them. A server leaves out an option its release doesn't have, so [has] says
 * whether it sent one. The certificate password is not here: it is never read into a screen, and goes back to the server as the
 * server sent it, inside the configuration this is read from.
 */
data class NetworkSettings(
    val present: Set<String> = emptySet(),
    val httpPort: Int = DEFAULT_HTTP_PORT,
    val httpsPort: Int = DEFAULT_HTTPS_PORT,
    val publicHttpPort: Int = DEFAULT_HTTP_PORT,
    val publicHttpsPort: Int = DEFAULT_HTTPS_PORT,
    val baseUrl: String = "",
    val enableHttps: Boolean = false,
    val requireHttps: Boolean = false,
    val certificatePath: String = "",
    val bindAddresses: List<String> = emptyList(),
    val ipv4: Boolean = true,
    val ipv6: Boolean = false,
    val remoteAccess: Boolean = true,
    val remoteIpFilter: List<String> = emptyList(),
    val filterIsBlacklist: Boolean = false,
    val knownProxies: List<String> = emptyList(),
    val localSubnets: List<String> = emptyList(),
    val autoDiscovery: Boolean = true,
    val upnp: Boolean = false,
    val publishedByRequest: Boolean = false,
    val publishedBySubnet: List<String> = emptyList(),
    val ignoreVirtual: Boolean = true,
    val virtualNames: List<String> = emptyList(),
) {
    fun has(key: String): Boolean = key in present
}

internal const val DEFAULT_HTTP_PORT = 8096
internal const val DEFAULT_HTTPS_PORT = 8920

/** The network configuration [this], read with [keys]'s names. */
fun JsonObject.networkSettings(keys: NetworkKeys): NetworkSettings =
    NetworkSettings(
        present = this.keys,
        httpPort = int(keys.httpPort) ?: DEFAULT_HTTP_PORT,
        httpsPort = int(keys.httpsPort) ?: DEFAULT_HTTPS_PORT,
        publicHttpPort = int(keys.publicHttpPort) ?: DEFAULT_HTTP_PORT,
        publicHttpsPort = int(keys.publicHttpsPort) ?: DEFAULT_HTTPS_PORT,
        baseUrl = string(NETWORK_BASE_URL).orEmpty(),
        enableHttps = bool(NETWORK_ENABLE_HTTPS) == true,
        requireHttps = bool(NETWORK_REQUIRE_HTTPS) == true,
        certificatePath = string(NETWORK_CERTIFICATE_PATH).orEmpty(),
        bindAddresses = strings(NETWORK_BIND_ADDRESSES),
        ipv4 = bool(keys.ipv4) != false,
        ipv6 = bool(keys.ipv6) == true,
        remoteAccess = bool(NETWORK_REMOTE_ACCESS) != false,
        remoteIpFilter = strings(NETWORK_REMOTE_IP_FILTER),
        filterIsBlacklist = bool(NETWORK_IP_FILTER_BLACKLIST) == true,
        knownProxies = strings(NETWORK_KNOWN_PROXIES),
        localSubnets = strings(NETWORK_LOCAL_SUBNETS),
        autoDiscovery = bool(NETWORK_AUTO_DISCOVERY) != false,
        upnp = bool(NETWORK_UPNP) == true,
        publishedByRequest = bool(NETWORK_PUBLISHED_BY_REQUEST) == true,
        publishedBySubnet = strings(NETWORK_PUBLISHED_BY_SUBNET),
        ignoreVirtual = bool(NETWORK_IGNORE_VIRTUAL) != false,
        virtualNames = strings(NETWORK_VIRTUAL_NAMES),
    )

/** The server's network configuration, kept whole so a save sends back what it didn't change (the certificate password included). */
suspend fun JellyfinClient.networkConfiguration(): JsonObject = getObject(NETWORK_CONFIGURATION)

suspend fun JellyfinClient.saveNetworkConfiguration(configuration: JsonObject) {
    post(NETWORK_CONFIGURATION, configuration)
}

private const val NETWORK_CONFIGURATION = "System/Configuration/network"

/** A way a network change reaches the connection this app is using. */
enum class NetworkImpact(
    /** Whether it can lock out every client, this app included, until someone changes it on the server itself. */
    val severe: Boolean,
) {
    /** The port this app connects on is changing. */
    Port(true),

    /** HTTPS is being turned off, and this app connects over it. */
    HttpsOff(true),

    /** Requiring HTTPS, and this app connects over plain http. */
    RequireHttps(true),

    /** The base URL this app's address includes is changing. */
    BaseUrl(true),

    /** The server is told which addresses to listen on, and the address this app uses is not one of them. */
    BindAddress(true),

    /** HTTPS is on and its certificate is changing: a wrong file or password leaves the server unable to serve HTTPS. */
    Certificate(true),

    /** Remote access is being turned off: this app may be connecting from outside the local network. */
    RemoteAccess(false),

    /** Which addresses may connect from outside is changing. */
    IpFilter(false),

    /** IPv4 or IPv6 is being turned off. */
    Ip(false),
}

/**
 * How going from [before] to [after] reaches the server address [appUrl], the one this app is signed in to. It compares what it
 * can know: the app's scheme, port, host and path against the changed options. A server reached through a proxy or another name
 * can still be affected by a change this doesn't flag, which is why these options are only ever saved on request.
 */
fun networkImpacts(
    appUrl: String,
    before: NetworkSettings,
    after: NetworkSettings,
): Set<NetworkImpact> =
    buildSet {
        appUrl.toHttpUrlOrNull()?.let { addAll(addressImpacts(it, before, after)) }
        addAll(accessImpacts(before, after))
    }

/** The changes that reach [app]'s own scheme, port, host or path. */
private fun addressImpacts(
    app: HttpUrl,
    before: NetworkSettings,
    after: NetworkSettings,
): Set<NetworkImpact> {
    val port = if (app.isHttps) before.httpsPort to after.httpsPort else before.httpPort to after.httpPort
    return listOfNotNull(
        NetworkImpact.Port.takeIf { app.port == port.first && port.first != port.second },
        NetworkImpact.HttpsOff.takeIf { app.isHttps && before.enableHttps && !after.enableHttps },
        NetworkImpact.RequireHttps.takeIf { !app.isHttps && !before.requireHttps && after.requireHttps },
        NetworkImpact.BaseUrl.takeIf { before.baseUrl != after.baseUrl },
        NetworkImpact.BindAddress.takeIf { listensElsewhere(app.host, before, after) },
        NetworkImpact.Certificate.takeIf { after.enableHttps && before.certificatePath != after.certificatePath },
    ).toSet()
}

/** Whether [after] tells the server which addresses to listen on, and [host] isn't one of them. */
private fun listensElsewhere(
    host: String,
    before: NetworkSettings,
    after: NetworkSettings,
): Boolean = after.bindAddresses != before.bindAddresses && after.bindAddresses.isNotEmpty() && host !in after.bindAddresses

/** The changes that narrow who can connect, whichever address they use. */
private fun accessImpacts(
    before: NetworkSettings,
    after: NetworkSettings,
): Set<NetworkImpact> =
    listOfNotNull(
        NetworkImpact.RemoteAccess.takeIf { before.remoteAccess && !after.remoteAccess },
        NetworkImpact.IpFilter.takeIf {
            before.remoteIpFilter != after.remoteIpFilter || before.filterIsBlacklist != after.filterIsBlacklist
        },
        NetworkImpact.Ip.takeIf { (before.ipv4 && !after.ipv4) || (before.ipv6 && !after.ipv6) },
    ).toSet()

/** A base URL as the server stores it: a leading slash, no trailing one, and empty for none. */
fun normalBaseUrl(text: String): String = text.trim().trim('/').let { if (it.isEmpty()) "" else "/$it" }
