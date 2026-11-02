package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * The view models of the console pages that are open, and of no other. The console's back stack holds each open
 * page under its opening number, and a page's view models live as long as its entry does: a page that opens
 * another keeps its own while the other is on top, so Back finds it as it was. Without this a page's view model
 * would live in the Activity's store until the Activity finished, one more per opening, each with its scope running.
 *
 * Held in the Activity's store itself, so open pages keep their view models across a rotation. After a process
 * death there are none, and each page loads afresh.
 */
class ConsolePageStores : ViewModel() {
    private val stores = mutableMapOf<Int, ViewModelStore>()

    /** The store for [opening]: the same one across a rotation, and for as long as the page is on the stack. */
    fun storeFor(opening: Int): ViewModelStore = stores.getOrPut(opening) { ViewModelStore() }

    /** Only [openings] are on the stack now: the other pages' view models go, and their scopes with them. */
    fun retain(openings: Set<Int>) {
        (stores.keys - openings).forEach { stores.remove(it)?.clear() }
    }

    /** Every page closed: all view models go. */
    fun close() = retain(emptySet())

    override fun onCleared() = close()
}

/** Shows [content] with [store] as the owner its `viewModel()` calls use. */
@Composable
fun ConsolePageScope(
    store: ViewModelStore,
    content: @Composable () -> Unit,
) {
    val owner =
        remember(store) {
            object : ViewModelStoreOwner {
                override val viewModelStore = store
            }
        }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
