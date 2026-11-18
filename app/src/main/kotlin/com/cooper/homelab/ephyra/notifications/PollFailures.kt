package com.cooper.homelab.ephyra.notifications

import com.cooper.homelab.core.telemetry.CrashBreadcrumbs
import com.cooper.homelab.ephyra.OWN_LOG_PACKAGES
import kotlin.coroutines.cancellation.CancellationException

/** What a failure to schedule or cancel the notification poll says about who has to act on it. */
internal enum class PollFailure {
    /** Something to fix here: an invalid work request or constraint, a misused API, or an exception of this app's own code. */
    Ours,

    /** The device or the platform said no: a quota, a refusal to run background work, a full disk. Nothing for this app to fix, so nothing is sent. */
    Environment,

    /** The work was cancelled, which is how a poll ends and not a failure. */
    Cancelled,
}

/** How deep a cause chain is read, so a chain that loops back on itself ends. */
private const val MAX_CAUSE_DEPTH = 10

/** The words of the platform's job-count limit, which is the one [IllegalStateException] that is the device's and not a misuse. */
private val jobLimit = listOf("job limit exceeded", "distinct jobs")

/**
 * Which of the three [failure] is, read from it and its causes. It is a list of what points at this app, so a kind of failure
 * nobody thought of is [PollFailure.Environment] and is not reported until someone decides it should be: an invalid request or
 * constraint ([IllegalArgumentException]), an [IllegalStateException] that is not the job limit (WorkManager not initialised, a
 * misused call), or an exception of this app's own packages or the library's.
 */
internal fun classifyPollFailure(failure: Throwable): PollFailure {
    val chain = generateSequence(failure) { it.cause?.takeIf { cause -> cause !== it } }.take(MAX_CAUSE_DEPTH).toList()
    return when {
        chain.any { it is CancellationException } -> PollFailure.Cancelled
        chain.any(::isJobLimit) -> PollFailure.Environment
        chain.any { it is IllegalArgumentException || it is IllegalStateException || isOwnException(it) } -> PollFailure.Ours
        else -> PollFailure.Environment
    }
}

private fun isJobLimit(failure: Throwable): Boolean =
    failure is IllegalStateException &&
        failure.message.orEmpty().let { message -> jobLimit.any { message.contains(it, ignoreCase = true) } }

private fun isOwnException(failure: Throwable): Boolean = OWN_LOG_PACKAGES.any { failure::class.java.name.startsWith(it) }

/**
 * A scheduling failure as Crashlytics receives it: the original's stack frames and its class name as the message, never the
 * original's message or cause. A platform exception's text is not this app's to vouch for, and the report carries a stack trace and
 * nothing else.
 */
internal class PollSchedulingFailed(
    original: Throwable,
) : Exception(original::class.java.name) {
    init {
        stackTrace = original.stackTrace
    }
}

/**
 * The breadcrumbs the notification planner reports through: a failure that points at this app is recorded as a non-fatal (which the
 * real breadcrumbs only send with the user's consent, and never from a debug build). One the device caused, and a cancellation,
 * record nothing: a breadcrumb would be a log line, and the only one this app sets is the fixed text of the report above.
 */
internal class PollBreadcrumbs(
    private val delegate: CrashBreadcrumbs,
) : CrashBreadcrumbs by delegate {
    override fun nonFatal(
        message: String,
        cause: Throwable,
    ) {
        when (classifyPollFailure(cause)) {
            PollFailure.Ours -> delegate.nonFatal(message, PollSchedulingFailed(cause))
            PollFailure.Environment, PollFailure.Cancelled -> Unit
        }
    }
}
