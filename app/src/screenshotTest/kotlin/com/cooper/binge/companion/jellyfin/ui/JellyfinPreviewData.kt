package com.cooper.binge.companion.jellyfin.ui

import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.DiscoveredServer
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.PublicServerInfo
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

val previewSignIn = UiState.SignIn(baseUrl = LAN, server = lanServer, username = "sam")

val previewSignInHttps = UiState.SignIn(baseUrl = REMOTE, server = remoteServer)

val previewSignInWrongPassword = previewSignIn.copy(password = "hunter2", passwordError = SetupError.WrongCredentials)

val previewQuickConnect = previewSignIn.copy(quickConnectCode = "482913")

val previewQuickConnectOff = previewSignIn.copy(quickConnectError = SetupError.QuickConnectOff)

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

val previewSignedIn = UiState.SignedIn(session = session, titles = 1_284, card = previewCard)

/** The card before any read has come in: a placeholder in each total, and no Connected. */
val previewSignedInCardLoading = previewSignedIn.copy(card = ServerCardState())

val previewSignedInChecking = UiState.SignedIn(session = session, titles = null, refreshing = true)

val previewSignedInFailed = previewSignedIn.copy(titles = null, refreshFailed = true)

val previewSignedInHttps = previewSignedIn.copy(session = session.copy(baseUrl = REMOTE, serverName = "Cabin"))

val previewQuickConnectGetting = previewSignIn.copy(gettingCode = true)

val previewSignInUnreachable = previewSignIn.copy(password = "hunter2", passwordError = SetupError.Unreachable)

val previewSignedInAdmin =
    previewSignedIn.copy(
        account = JellyfinAccount(previewSignedIn.session.userName, buildJsonObject { put("IsAdministrator", true) }),
        card = previewCard.copy(users = 5),
    )

/** An administrator's card with everything it can flag: an update, a restart waiting and a server on its way down. */
val previewSignedInAdminAlerts =
    previewSignedInAdmin.copy(card = previewSignedInAdmin.card.copy(updateAvailable = true, restartPending = true, shuttingDown = true))
