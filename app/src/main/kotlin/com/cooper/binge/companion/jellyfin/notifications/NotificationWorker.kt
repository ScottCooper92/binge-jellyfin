package com.cooper.binge.companion.jellyfin.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.binge.companion.sdk.attempt

/** One run of the poll: checks the log and maps the outcome to WorkManager's result. */
class PollRunner(
    private val checker: ActivityChecker,
    private val reactor: PollReactor,
) {
    suspend fun run(): PollOutcome = reactor.react(checker.check())
}

/**
 * The periodic poll. Everything with a decision in it is in [PollRunner]. An exception past the checker's own guards is
 * transient by nature, so it retries rather than fails.
 */
class NotificationWorker(
    context: Context,
    params: WorkerParameters,
    private val runner: PollRunner,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        when (attempt { runner.run() }.getOrDefault(PollOutcome.Retry)) {
            PollOutcome.Succeed -> Result.success()
            PollOutcome.Retry -> Result.retry()
        }
}

/** Builds [NotificationWorker] with the graph's runner; WorkManager's own initialiser is removed in the manifest so this one is used. */
class NotificationWorkerFactory(
    private val runner: () -> PollRunner,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == NotificationWorker::class.java.name) NotificationWorker(appContext, workerParameters, runner()) else null
}
