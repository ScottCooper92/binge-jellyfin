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
            val literals = current?.let(::HeldLiterals)
            // The host is replaced as a whole word only: a single-label name such as `jellyfin` is also in this app's own tags.
            literals?.host?.let(::registerWholeToken)
            literals?.anywhere?.forEach(::register)
            held = listOfNotNull(literals?.host) + literals?.anywhere.orEmpty()
        }
    }

/** What a session holds that must not reach the log: the host is matched as a word, the rest wherever it appears. */
private class HeldLiterals(
    session: JellyfinSession,
) {
    private val address = session.baseUrl.trim().trimEnd('/')
    val host: String? = address.substringAfter("://").substringBefore('/').takeIf { it.isNotBlank() && it != address }
    val anywhere: List<String> = listOf(address, session.token, session.userName).filter { it.isNotBlank() }.distinct()
}
