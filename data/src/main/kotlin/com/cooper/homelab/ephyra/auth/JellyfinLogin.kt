package com.cooper.homelab.ephyra.auth

import com.cooper.homelab.core.server.connection.CredentialCodec
import com.cooper.homelab.core.server.connection.CredentialParts

/** Who is signed in to one saved server, and the token Jellyfin minted: the part of a [JellyfinSession] that is not the server's address or name. */
data class JellyfinLogin(
    val serverId: String,
    val serverVersion: String,
    val userId: String,
    val userName: String,
    val token: String,
)

/**
 * A [JellyfinLogin] cut into the fields the shared library keeps in the clear and the token it encrypts. The names are an on-disk
 * format, and [LegacySessionImport] writes the same ones for a session the released version kept.
 */
object JellyfinLoginCodec : CredentialCodec<JellyfinLogin> {
    const val SERVER_ID = "serverId"
    const val SERVER_VERSION = "serverVersion"
    const val USER_ID = "userId"
    const val USER_NAME = "userName"

    override fun split(credential: JellyfinLogin): CredentialParts =
        CredentialParts(
            mapOf(
                SERVER_ID to credential.serverId,
                SERVER_VERSION to credential.serverVersion,
                USER_ID to credential.userId,
                USER_NAME to credential.userName,
            ),
            credential.token,
        )

    override fun join(
        fields: Map<String, String>,
        secret: String,
    ): JellyfinLogin? {
        val userId = fields[USER_ID]?.takeIf { it.isNotBlank() } ?: return null
        return JellyfinLogin(
            serverId = fields[SERVER_ID].orEmpty(),
            serverVersion = fields[SERVER_VERSION].orEmpty(),
            userId = userId,
            userName = fields[USER_NAME].orEmpty(),
            token = secret,
        )
    }
}
