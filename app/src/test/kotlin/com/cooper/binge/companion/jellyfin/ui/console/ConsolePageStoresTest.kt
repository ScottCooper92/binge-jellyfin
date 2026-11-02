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
    fun `a page that left the stack has its view model cleared, and the ones still on it keep theirs`() {
        val stores = ConsolePageStores()
        val first = stores.storeFor(1).page()
        val second = stores.storeFor(2).page()
        stores.retain(setOf(1))
        assertTrue(second.cleared)
        assertFalse(first.cleared)
        assertSame(first, stores.storeFor(1).page())
        // The same opening number again after it left is a new page, not the old one back.
        val again = stores.storeFor(2).page()
        assertNotSame(second, again)
    }

    @Test
    fun `after a process death there are no view models, and the restored pages load afresh`() {
        val before = ConsolePageStores()
        val page = before.storeFor(1).page()
        // The new process has a new store holder; the saved stack still names opening 1.
        val after = ConsolePageStores()
        assertNotSame(page, after.storeFor(1).page())
    }

    @Test
    fun `closing the page clears its view model, and the Activity finishing clears an open one`() {
        val stores = ConsolePageStores()
        val closed = stores.storeFor(1).page()
        val other = stores.storeFor(2).page()
        stores.close()
        assertTrue(closed.cleared)
        assertTrue(other.cleared)
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
