package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * The view models of each open page, kept by its opening number. A page and the items open in it (a task, a log, a user)
 * are separate entries on the stack, but they share one store: the list and the item read and change the same state.
 *
 * Held by a view model of the Activity, so the stores outlive a rotation or a fold, and go when their opening leaves the
 * stack ([retain]) or the Activity does.
 */
class ConsolePageStores : ViewModel() {
    private val stores = mutableMapOf<Int, ViewModelStore>()
    private val owners = mutableMapOf<Int, ViewModelStoreOwner>()

    /** The store owner for [opening], made on first use. */
    fun owner(opening: Int): ViewModelStoreOwner =
        owners.getOrPut(opening) {
            val store = stores.getOrPut(opening) { ViewModelStore() }
            object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = store
            }
        }

    /** Clears the stores of every opening not in [openings]: those pages have left the stack. */
    fun retain(openings: Set<Int>) {
        (stores.keys - openings).forEach { opening ->
            stores.remove(opening)?.clear()
            owners.remove(opening)
        }
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
        owners.clear()
    }
}
