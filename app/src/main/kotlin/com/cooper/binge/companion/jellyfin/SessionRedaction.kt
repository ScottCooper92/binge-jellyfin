package com.cooper.binge.companion.jellyfin

import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.homelab.core.diagnostics.Redactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Keeps [Redactor] told about what the signed-in session holds: the server's address and host, the access token and the user's
 * name. Each change of session forgets the previous literals first, so a literal that is gone stops being replaced.
 */
internal fun Redactor.followSession(
    sessions: Flow<JellyfinSession?>,
    scope: CoroutineScope,
): Job =
    scope.launch {
        var held = emptyList<String>()
        sessions.collect { current ->
            held.forEach(::unregister)
            held = current?.literals().orEmpty()
            held.forEach(::register)
        }
    }

private fun JellyfinSession.literals(): List<String> {
    val address = baseUrl.trim().trimEnd('/')
    val host = address.substringAfter("://").substringBefore('/')
    return listOf(address, host, token, userName).filter { it.isNotBlank() }.distinct()
}
