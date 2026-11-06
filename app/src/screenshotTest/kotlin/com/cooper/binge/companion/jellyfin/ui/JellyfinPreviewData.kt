package com.cooper.binge.companion.jellyfin.ui

import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.DiscoveredServer
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.PublicServerInfo
import com.cooper.binge.companion.jellyfin.server.TmdbCoverage
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

/** A library where 12 of 134 titles carry no TMDB id, so nothing that looks one up can find them. */
val previewSignedInNoTmdb =
    previewSignedIn.copy(card = previewCard.copy(tmdb = TmdbCoverage(titles = 134, withoutTmdbId = 12)))

/** The card before any read has come in: a placeholder in each total, and no Connected. */
val previewSignedInCardLoading = previewSignedIn.copy(card = ServerCardState())

val previewSignedInChecking = UiState.SignedIn(session = session, titles = null, refreshing = true)

/** The saved server did not answer: the home gives way to the connection problem. */
val previewSignedInFailed = previewSignedIn.copy(titles = null, refreshFailed = true, health = ConnectionHealth.Unreachable)

/** The server answered with something the app could not use. */
val previewSignedInCouldNotLoad = previewSignedInFailed.copy(health = ConnectionHealth.CouldNotLoad)

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

/** A user who is not an administrator but may manage Live TV: their home gets the Live TV row. */
val previewSignedInLiveTvManager =
    previewSignedIn.copy(
        account =
            JellyfinAccount(
                previewSignedIn.session.userName,
                buildJsonObject {
                    put("IsAdministrator", false)
                    put("EnableLiveTvManagement", true)
                },
            ),
    )

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
