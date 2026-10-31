package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * The view models of the console page that is open, and of no other. There is no navigation back
 * stack, so without this a page's view model would live in the Activity's store until the Activity
 * finished: one more per opening, each with its scope still running.
 *
 * Held in the Activity's store itself, so an open page keeps its view model across a rotation. A new
 * opening clears the last one's, and so does closing the page.
 */
class ConsolePageStores : ViewModel() {
    private var opening: Int? = null
    private var store = ViewModelStore()

    /** The store for [opening]: the same one across a rotation, a new one (the last cleared) for a new opening. */
    fun storeFor(opening: Int): ViewModelStore {
        if (this.opening != opening) {
            store.clear()
            store = ViewModelStore()
            this.opening = opening
        }
        return store
    }

    /** The page closed: its view models go, and their scopes with them. */
    fun close() {
        store.clear()
        opening = null
    }

    override fun onCleared() = store.clear()
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
