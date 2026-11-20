package com.cooper.homelab.ephyra.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.server.Country
import com.cooper.homelab.ephyra.server.Culture
import com.cooper.homelab.ephyra.server.DiscoveredServer
import com.cooper.homelab.ephyra.server.JellyfinAccount
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.server.PublicUser
import com.cooper.homelab.ephyra.server.StartupConfiguration
import com.cooper.homelab.ephyra.server.TmdbCoverage
import com.cooper.homelab.ephyra.server.UiLanguage
import com.cooper.homelab.ephyra.ui.console.ConsoleFailure
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val LAN = "http://192.168.1.20:8096"
private const val REMOTE = "https://jellyfin.example.com"

private val lanServer = PublicServerInfo(serverName = "Living room", version = "10.11.6", serverId = "a1", baseUrl = LAN.toHttpUrl())
private val remoteServer = PublicServerInfo(serverName = "Cabin", version = "10.11.6", serverId = "b2", baseUrl = REMOTE.toHttpUrl())

val previewEnterServerEmpty = UiState.EnterServer()

val previewEnterServerSearching = UiState.EnterServer(discovering = true)

val previewEnterServerFound =
    UiState.EnterServer(
        discovered =
            listOf(
                DiscoveredServer(id = "a1", name = "Living room", address = LAN),
                DiscoveredServer(id = "b2", name = "Cabin", address = REMOTE),
            ),
    )

val previewEnterServerError = UiState.EnterServer(address = "192.168.1.99:8096", error = SetupError.Unreachable)

val previewEnterServerInvalid = UiState.EnterServer(address = "jellyfin lan", error = SetupError.InvalidAddress)

val previewEnterServerNotJellyfin = UiState.EnterServer(address = "192.168.1.30:8080", error = SetupError.NotJellyfin)

val previewEnterServerGeneric = UiState.EnterServer(address = "192.168.1.20:8096", error = SetupError.Generic)

/** A typed `http://` address to a public host: Connect waits for the user to agree to plain HTTP. */
val previewEnterServerInsecure = UiState.EnterServer(address = "http://media.example.com:8096", insecureHost = "media.example.com")

val previewEnterServerInsecureAgreed = previewEnterServerInsecure.copy(cleartextAllowed = true)

/** The app refused plain HTTP to a public host, and says why. */
val previewEnterServerCleartextRefused = previewEnterServerInsecure.copy(error = SetupError.CleartextRefused)

/** The server asked for a client certificate, and the app says so. */
val previewEnterServerClientCertificate =
    UiState.EnterServer(
        address = "https://media.example.com",
        error = SetupError.ClientCertificateNeeded,
    )

val previewSignIn = UiState.SignIn(baseUrl = LAN, server = lanServer, username = "sam")

val previewSetupIncomplete = previewSignIn.copy(server = lanServer.copy(startupWizardCompleted = false))

val previewSignInUsers =
    previewSignIn.copy(
        username = "",
        publicUsers =
            listOf(
                PublicUser("u1", "Mum", hasPassword = true),
                PublicUser("u2", "Dad", hasPassword = true),
                PublicUser("u3", "Kid", hasPassword = false),
            ),
    )

/** Two of the three listed accounts have a picture (a flat colour is enough to tell it from the initials); Dad keeps his initials. */
val previewSignInUsersPictures =
    previewSignInUsers.copy(
        publicPictures = mapOf("u1" to previewPicture(Color.rgb(0x2E, 0x7D, 0x32)), "u3" to previewPicture(Color.rgb(0xC6, 0x28, 0x28))),
    )

private fun previewPicture(color: Int): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

private val setupServer = lanServer.copy(startupWizardCompleted = false)

private fun previewStartupConfiguration(withName: Boolean = true) =
    StartupConfiguration(
        buildJsonObject {
            if (withName) put("ServerName", "Living room")
            put("UICulture", "en-GB")
            put("MetadataCountryCode", "GB")
            put("PreferredMetadataLanguage", "en")
        },
    )

private fun previewStartupStep(
    withName: Boolean = true,
    busy: Boolean = false,
    error: SetupError? = null,
) = StartupPhase.Configuring(
    configuration = previewStartupConfiguration(withName),
    uiLanguages = listOf(UiLanguage("English (United Kingdom)", "en-GB"), UiLanguage("Español", "es")),
    cultures = listOf(Culture("English", "en", listOf("eng")), Culture("Spanish", "es", listOf("spa"))),
    countries = listOf(Country("GB", "United Kingdom"), Country("ES", "Spain")),
    busy = busy,
    error = error,
)

val previewStartup = UiState.Startup(LAN, setupServer, previewStartupStep())

val previewStartupNoName = previewStartup.copy(phase = previewStartupStep(withName = false))

val previewStartupSaving = previewStartup.copy(phase = previewStartupStep(busy = true))

val previewStartupRefused = previewStartup.copy(phase = previewStartupStep(error = SetupError.Generic))

val previewStartupAccount = previewStartup.copy(phase = StartupPhase.Account(name = "root"))

val previewStartupAccountFilled = previewStartup.copy(phase = StartupPhase.Account("sam", "hunter2", "hunter2"))

val previewStartupAccountMismatch = previewStartup.copy(phase = StartupPhase.Account("sam", "hunter2", "hunter"))

val previewStartupAccountSaving = previewStartup.copy(phase = StartupPhase.Account("sam", "hunter2", "hunter2", busy = true))

val previewStartupAccountRefused =
    previewStartup.copy(
        phase = StartupPhase.Account("sam", "hunter2", "hunter2", error = SetupError.Generic),
    )

val previewStartupAccountReason =
    previewStartup.copy(
        phase = StartupPhase.Account("sam", "hunter2", "hunter2", error = SetupError.Generic, reason = "Password must not be empty"),
    )

val previewStartupLoading = previewStartup.copy(phase = StartupPhase.Loading)

val previewStartupFailed = previewStartup.copy(phase = StartupPhase.Failed(ConsoleFailure.Unreachable))

val previewStartupFinish = previewStartup.copy(phase = StartupPhase.Finish("sam", "hunter2"))

val previewStartupFinishPorts = previewStartup.copy(phase = StartupPhase.Finish("sam", "hunter2", portMapping = true))

val previewStartupFinishOff = previewStartup.copy(phase = StartupPhase.Finish("sam", "hunter2", remoteAccess = false))

val previewStartupFinishSaving = previewStartup.copy(phase = StartupPhase.Finish("sam", "hunter2", busy = true))

val previewStartupFinishRefused = previewStartup.copy(phase = StartupPhase.Finish("sam", "hunter2", error = SetupError.Generic))

val previewSignInHttps = UiState.SignIn(baseUrl = REMOTE, server = remoteServer)

val previewSignInWrongPassword = previewSignIn.copy(password = "hunter2", passwordError = SetupError.WrongCredentials)

val previewQuickConnect = previewSignIn.copy(quickConnectCode = "482913")

val previewQuickConnectOff = previewSignIn.copy(quickConnectError = SetupError.QuickConnectOff)

/** The server said Quick Connect is off before it was tried: no button and no hint. */
val previewQuickConnectNotOffered = previewSignIn.copy(quickConnectOn = false)

val previewQuickConnectExpired = previewSignIn.copy(quickConnectError = SetupError.QuickConnectExpired)

private val session =
    JellyfinSession(
        baseUrl = LAN,
        serverName = "Living room",
        serverId = "a1",
        serverVersion = "10.11.6",
        userId = "u1",
        userName = "sam",
        token = "not-a-token",
    )

/** What a server card shows once it has read everything a non-administrator may see. */
private val previewCard = ServerCardState(connected = true, playing = 2, movies = 842, series = 120)

val previewSignedIn = UiState.SignedIn(session = session, titles = 1_284, health = ConnectionHealth.Healthy, card = previewCard)

/** A library where 12 of 134 titles carry no TMDB id, so nothing that looks one up can find them. */
val previewSignedInNoTmdb =
    previewSignedIn.copy(card = previewCard.copy(tmdb = TmdbCoverage(titles = 134, withoutTmdbId = 12)))

/** The card before any read has come in: a placeholder in each total, and no Connected. */
val previewSignedInCardLoading = previewSignedIn.copy(card = ServerCardState())

val previewSignedInChecking = UiState.SignedIn(session = session, titles = null, refreshing = true, health = ConnectionHealth.Healthy)

/** The saved server has not answered yet, which is where every start begins: nothing of the home is drawn until it does. */
val previewSignedInWaiting = UiState.SignedIn(session = session, titles = null, refreshing = true)

/** The saved server did not answer: the home gives way to the connection problem. */
val previewSignedInFailed = previewSignedIn.copy(titles = null, refreshFailed = true, health = ConnectionHealth.Unreachable)

/** The server answered with something the app could not use. */
val previewSignedInCouldNotLoad = previewSignedInFailed.copy(health = ConnectionHealth.CouldNotLoad)

/** The server asks for a client certificate, which the app cannot present. */
val previewSignedInClientCertificate = previewSignedInFailed.copy(health = ConnectionHealth.ClientCertificateNeeded)

/** The server is on the local network and Android has not let the app reach it. */
val previewSignedInLocalNetworkDenied = previewSignedInFailed.copy(health = ConnectionHealth.LocalNetworkDenied)

/** A retry in flight on the connection problem: Try again says so and cannot be pressed again. */
val previewSignedInRetrying = previewSignedInFailed.copy(refreshing = true, refreshFailed = false)

val previewSignedInHttps = previewSignedIn.copy(session = session.copy(baseUrl = REMOTE, serverName = "Cabin"))

val previewQuickConnectGetting = previewSignIn.copy(gettingCode = true)

val previewEnterServerLocalNetwork = UiState.EnterServer(address = "192.168.1.99:8096", error = SetupError.LocalNetworkDenied)

val previewSignInLocalNetwork = previewSignIn.copy(password = "hunter2", passwordError = SetupError.LocalNetworkDenied)

val previewSignInUnreachable = previewSignIn.copy(password = "hunter2", passwordError = SetupError.Unreachable)

val previewSignedInAdmin =
    previewSignedIn.copy(
        account = JellyfinAccount(previewSignedIn.session.userName, buildJsonObject { put("IsAdministrator", true) }),
        card = previewCard.copy(users = 5),
    )

/** An administrator whose server has no libraries: the home hints at adding the first. */
val previewSignedInNoLibraries = previewSignedInAdmin.copy(card = previewSignedInAdmin.card.copy(libraries = 0))

/** An administrator whose policy lets them manage Live TV: the row is on their home too. */
val previewSignedInAdminLiveTv =
    previewSignedInAdmin.copy(
        account =
            JellyfinAccount(
                previewSignedIn.session.userName,
                buildJsonObject {
                    put("IsAdministrator", true)
                    put("EnableLiveTvManagement", true)
                },
            ),
    )

/**
 * An administrator's card with every pill that can show at once: not encrypted (this address is http), an update, and a
 * restart waiting. The worst case for the card's top row.
 */
val previewSignedInAdminAlerts =
    previewSignedInAdmin.copy(card = previewSignedInAdmin.card.copy(updateAvailable = true, restartPending = true))

/** The administrator's home with their own picture set. */
val previewSignedInAdminPicture = previewSignedInAdmin.copy(account = previewSignedInAdmin.account.copy(imageTag = "picture"))

/** Someone who is not an administrator, with their own picture set: it is on their account row alone. */
val previewSignedInPicture = previewSignedIn.copy(account = previewSignedIn.account.copy(imageTag = "picture"))
