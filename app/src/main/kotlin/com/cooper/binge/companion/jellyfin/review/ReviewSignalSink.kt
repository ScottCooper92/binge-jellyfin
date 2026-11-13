package com.cooper.binge.companion.jellyfin.review

/** Where a screen says the user finished something real. A ViewModel takes one so a test can watch it. */
fun interface ReviewSignalSink {
    fun recordPositiveAction()
}

/** The sink for a ViewModel built without the review prompt, as in a test: it counts nothing. */
object NoReviewSignals : ReviewSignalSink {
    override fun recordPositiveAction() = Unit
}
