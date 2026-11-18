package com.cooper.homelab.ephyra.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.HttpUrl

/** The server as it answers before sign-in. */
data class PublicServerInfo(
    val serverName: String,
    val version: String,
    val serverId: String,
    /**
     * The server's base as it answered, after any redirects: `https://…` for an address typed bare
     * whose http request was redirected. Sign-in and every later call use this one, since OkHttp turns
     * a redirected POST into a GET and drops `Authorization` on a change of scheme or host.
     */
    val baseUrl: HttpUrl,
    /**
     * Whether the server has finished its first-run setup, from `StartupWizardCompleted`. A server that does not say (an older one)
     * counts as finished, so nothing here depends on a version number.
     */
    val startupWizardCompleted: Boolean = true,
) {
    /** A fresh server with no account yet: nothing can sign in to it until its setup is done. */
    val setupIncomplete: Boolean get() = !startupWizardCompleted
}

/** A session the server minted: its token, and who it is for. */
data class SignedIn(
    val token: String,
    val userId: String,
    val userName: String,
    val serverId: String,
)

/** What the server will do for a user who forgot their password. A value this app does not know is [ContactAdmin]. */
enum class ForgotPasswordAction { ContactAdmin, PinCode, InNetworkRequired }

/** The server's answer to a forgotten password: what happens next, and for [ForgotPasswordAction.PinCode] the file that holds the PIN. */
data class ForgotPasswordAnswer(
    val action: ForgotPasswordAction,
    val pinFile: String?,
)

/** An account the server lists on its login screen, as `/Users/Public` gives it: no sign-in needed to read it. */
data class PublicUser(
    val id: String,
    val name: String,
    val hasPassword: Boolean,
    /** The tag of the user's picture (`PrimaryImageTag`), or null when they have none. */
    val imageTag: String? = null,
)

/** A Quick Connect request: the code the user approves, and the secret this app polls with. */
data class QuickConnectStart(
    val secret: String,
    val code: String,
)

suspend fun JellyfinClient.publicInfo(): PublicServerInfo {
    val (json, from) = getObjectFrom(PUBLIC_INFO)
    return PublicServerInfo(
        serverName = json.string("ServerName").orEmpty(),
        version = json.string("Version").orEmpty(),
        serverId = json.string("Id").orEmpty(),
        baseUrl = baseOf(from) ?: baseUrl,
        startupWizardCompleted = json.bool("StartupWizardCompleted") ?: true,
    )
}

/** Asks the server to start a password reset for [username]. Anonymous; only the name is sent. */
suspend fun JellyfinClient.forgotPassword(username: String): ForgotPasswordAnswer {
    val json = postObject("Users/ForgotPassword", JsonObject(mapOf("EnteredUsername" to JsonPrimitive(username))))
    val action = ForgotPasswordAction.entries.firstOrNull { it.name == json.string("Action") } ?: ForgotPasswordAction.ContactAdmin
    return ForgotPasswordAnswer(action, json.string("PinFile")?.takeIf { it.isNotBlank() })
}

/**
 * Redeems the PIN from the file the server wrote. True when it was right: the server then sets the account's password to the PIN
 * as sent. A PIN that matches no request, or one that has run out, is a 404 from the server, so that is false here too.
 */
suspend fun JellyfinClient.redeemPasswordPin(pin: String): Boolean =
    try {
        postObject("Users/ForgotPassword/Pin", JsonObject(mapOf("Pin" to JsonPrimitive(pin)))).bool("Success") == true
    } catch (e: JellyfinHttpException) {
        if (e.code == HTTP_NOT_FOUND) false else throw e
    }

private const val HTTP_NOT_FOUND = 404

/** The accounts the server's owner has not hidden from the login screen; empty when there are none. */
suspend fun JellyfinClient.publicUsers(): List<PublicUser> {
    val answer = get(PUBLIC_USERS) as? JsonArray ?: throw JellyfinUnreadableAnswer(PUBLIC_USERS)
    return answer.mapNotNull { element ->
        val json = element as? JsonObject ?: return@mapNotNull null
        val name = json.string("Name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        PublicUser(
            id = json.string("Id").orEmpty(),
            name = name,
            hasPassword = json.bool("HasPassword") != false,
            imageTag = json.string("PrimaryImageTag")?.takeIf { it.isNotBlank() },
        )
    }
}

suspend fun JellyfinClient.authenticateByName(
    username: String,
    password: String,
): SignedIn =
    postObject(
        "Users/AuthenticateByName",
        JsonObject(mapOf("Username" to JsonPrimitive(username), "Pw" to JsonPrimitive(password))),
    ).toSignedIn()

suspend fun JellyfinClient.quickConnectEnabled(): Boolean = quickConnectEnabledOrNull() == true

/** Whether Quick Connect is on, or null when the server did not say (an answer that is not a plain true or false). */
suspend fun JellyfinClient.quickConnectEnabledOrNull(): Boolean? = (get("QuickConnect/Enabled") as? JsonPrimitive)?.booleanOrNull

suspend fun JellyfinClient.quickConnectInitiate(): QuickConnectStart {
    val json = postObject("QuickConnect/Initiate", null)
    return QuickConnectStart(secret = json.string("Secret").orEmpty(), code = json.string("Code").orEmpty())
}

suspend fun JellyfinClient.quickConnectAuthenticated(secret: String): Boolean =
    getObject("QuickConnect/Connect", mapOf("secret" to secret)).bool("Authenticated") == true

suspend fun JellyfinClient.authenticateWithQuickConnect(secret: String): SignedIn =
    postObject("Users/AuthenticateWithQuickConnect", JsonObject(mapOf("Secret" to JsonPrimitive(secret)))).toSignedIn()

/** Ends this device's session on the server, so it leaves the user's devices list. */
suspend fun JellyfinClient.logout() {
    post("Sessions/Logout", null)
}

/** The signed-in user, with their policy: what the console offers them. */
suspend fun JellyfinClient.me(): JellyfinAccount {
    val json = getObject("Users/Me")
    return JellyfinAccount(
        name = json.string("Name").orEmpty(),
        policy = json.obj("Policy") ?: JsonObject(emptyMap()),
        imageTag = json.string("PrimaryImageTag")?.takeIf { it.isNotBlank() },
    )
}

/**
 * Approves a Quick Connect [code] another device shows, signing that device in as this user. A code
 * the server is not waiting on answers 404; Quick Connect turned off answers 401, as does a token
 * the server refuses. The console's `consoleCall` tells the two apart by asking `/Users/Me`. A code
 * already approved answers 500.
 */
suspend fun JellyfinClient.authorizeQuickConnect(code: String) {
    post("QuickConnect/Authorize", null, mapOf("code" to code))
}

private fun JsonObject.toSignedIn(): SignedIn {
    val user = obj("User")
    return SignedIn(
        token = string("AccessToken").orEmpty(),
        userId = user?.string("Id").orEmpty(),
        userName = user?.string("Name").orEmpty(),
        serverId = string("ServerId").orEmpty(),
    )
}

private const val PUBLIC_USERS = "Users/Public"
private const val PUBLIC_INFO = "System/Info/Public"

/** The base [answered] sits under, or null for a redirect to somewhere that is not this call's path. */
private fun baseOf(answered: HttpUrl): HttpUrl? {
    val path = answered.encodedPath
    if (!path.endsWith("/$PUBLIC_INFO", ignoreCase = true)) return null
    return answered
        .newBuilder()
        .encodedPath(path.dropLast(PUBLIC_INFO.length))
        .query(null)
        .fragment(null)
        .build()
}
