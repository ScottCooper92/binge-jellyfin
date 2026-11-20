package com.cooper.homelab.ephyra.ui

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/** Main as on a device: work runs on the main thread only; `immediate` runs inline when already there. */
@OptIn(ExperimentalCoroutinesApi::class)
internal object MainLooperDispatcher : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher = Immediate

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        Handler(Looper.getMainLooper()).post(block)
    }

    private object Immediate : MainCoroutineDispatcher() {
        override val immediate: MainCoroutineDispatcher get() = this

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = Looper.myLooper() != Looper.getMainLooper()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            Handler(Looper.getMainLooper()).post(block)
        }
    }
}
