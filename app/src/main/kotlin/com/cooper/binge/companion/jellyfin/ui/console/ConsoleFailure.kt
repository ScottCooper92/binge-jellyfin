package com.cooper.binge.companion.jellyfin.ui.console

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.companion.sdk.attempt
import com.binge.designsystem.ErrorKind
import com.binge.designsystem.template.ErrorScreen
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.me
import java.io.IOException

/** Why a console page couldn't read or save, in the words its screen shows. */
enum class ConsoleFailure(
    val message: Int,
) {
    /** The server answered, but not with what was asked: it refused, or failed itself. */
    Refused(R.string.error_generic),

    /** The server didn't answer, or there is no session to ask it with. */
    Unreachable(R.string.error_unreachable),
}

/**
 * Runs [block] against the signed-in server. A failure is logged under [tag] and handed back as a
 * [ConsoleFailure]; a cancellation is not caught.
 *
 * A 401 can mean the action is refused (Quick Connect turned off answers one) or that the token is.
 * So a 401 asks the server who the token belongs to (`/Users/Me`), and when that is refused too the
 * token is dead: it's reported on [Connection.refused], and the home sends the user to sign in.
 */
internal suspend fun <T> Connection.consoleCall(
    tag: String,
    block: suspend (JellyfinSession, JellyfinClient) -> T,
): Result<T> {
    val (session, client) = signedIn() ?: return Result.failure(IOException("Not signed in"))
    return attempt { block(session, client) }.onFailure { error ->
        Log.i(tag, "Console call failed", error)
        if (error.isUnauthorized() && attempt { client.me() }.exceptionOrNull()?.isUnauthorized() == true) reportRefused()
    }
}

private const val HTTP_UNAUTHORIZED = 401

private fun Throwable.isUnauthorized(): Boolean = this is JellyfinHttpException && code == HTTP_UNAUTHORIZED

internal fun Throwable.consoleFailure(): ConsoleFailure =
    if (this is JellyfinHttpException) ConsoleFailure.Refused else ConsoleFailure.Unreachable

/** The design system's [ErrorKind] that says this failure: no answer is a network failure, and a refusal is the server's. */
internal fun ConsoleFailure.errorKind(): ErrorKind = if (this == ConsoleFailure.Unreachable) ErrorKind.Network else ErrorKind.Server

/**
 * A console page that couldn't read: the design system's [ErrorScreen] for [failure], with Try again. The kind's own copy
 * says what happened; when the server didn't answer, this app's own headline names it ("Couldn't reach your Jellyfin
 * server"), which the kind's "No connection" does not. [modifier] carries the page's inner padding.
 */
@Composable
internal fun ConsoleFailureScreen(
    failure: ConsoleFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) = ErrorScreen(
    kind = failure.errorKind(),
    onRetry = onRetry,
    title = stringResource(R.string.error_unreachable).takeIf { failure == ConsoleFailure.Unreachable },
    modifier = modifier,
)
