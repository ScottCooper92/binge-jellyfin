package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsolePageStoresTest {
    private class Page : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    private fun ViewModelStore.page(): Page =
        ViewModelProvider(
            object : ViewModelStoreOwner {
                override val viewModelStore = this@page
            },
            viewModelFactory { initializer { Page() } },
        )[Page::class.java]

    @Test
    fun `an open page keeps its view model, as across a rotation`() {
        val stores = ConsolePageStores()
        val page = stores.storeFor(1).page()
        assertSame(page, stores.storeFor(1).page())
        assertFalse(page.cleared)
    }

    @Test
    fun `a new opening clears the last one's view model and starts a fresh one`() {
        val stores = ConsolePageStores()
        val first = stores.storeFor(1).page()
        val second = stores.storeFor(2).page()
        assertTrue(first.cleared)
        assertNotSame(first, second)
    }

    @Test
    fun `closing the page clears its view model, and the Activity finishing clears an open one`() {
        val stores = ConsolePageStores()
        val closed = stores.storeFor(1).page()
        stores.close()
        assertTrue(closed.cleared)
        // Opening the same number again after a close is a new page, not the old one back.
        val reopened = stores.storeFor(1).page()
        assertNotSame(closed, reopened)

        val activity = ViewModelStore()
        val held =
            ViewModelProvider(
                object : ViewModelStoreOwner {
                    override val viewModelStore = activity
                },
                viewModelFactory { initializer { stores } },
            )[ConsolePageStores::class.java]
        activity.clear()
        assertSame(stores, held)
        assertTrue(reopened.cleared)
    }

    @Test
    fun `a page opened from another leaves the opener's view model alone, so Back finds it as it was`() {
        val stores = ConsolePageStores()
        val opener = stores.storeFor(1).page()
        stores.storeFor(2).page()
        // Back from the second page: the opener is on the stack still, so it is the same view model, not a fresh load.
        assertSame(opener, stores.storeFor(1).page())
        assertFalse(opener.cleared)
    }
}
