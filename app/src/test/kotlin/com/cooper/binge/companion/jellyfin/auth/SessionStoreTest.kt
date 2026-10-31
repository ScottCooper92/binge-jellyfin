package com.cooper.binge.companion.jellyfin.auth

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.PlainCipher
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStoreTest {
    private val session = FakeJellyfin().run { session().also { close() } }

    @Test
    fun `a saved session reads back, and a sign-out keeps the device id`() =
        runBlocking {
            val store = testSessionStore()
            assertNull(store.current())
            val deviceId = store.deviceId()
            assertEquals(deviceId, store.deviceId())
            store.save(session)
            assertEquals(session, store.current())
            store.clear()
            assertNull(store.current())
            assertEquals(deviceId, store.deviceId())
        }

    @Test
    fun `a token that will not decrypt reads as signed out`() =
        runBlocking {
            val forgetful =
                object : SecretCipher by PlainCipher {
                    override fun decrypt(ciphertext: String): String? = null
                }
            val store = testSessionStore(forgetful)
            store.save(session)
            assertNull(store.current())
        }

    @Test
    fun `a Keystore that refuses to encrypt saves nothing`() =
        runBlocking {
            val broken =
                object : SecretCipher by PlainCipher {
                    override fun encrypt(plaintext: String): String = throw IllegalStateException("keymaster")
                }
            val store = testSessionStore(broken)
            assertFalse(store.save(session))
            assertNull(store.current())
        }

    @Test
    fun `the Keystore work runs on the store's dispatcher, never the caller's thread`() {
        val threads = java.util.Collections.synchronizedList(mutableListOf<String>())
        val recording =
            object : SecretCipher {
                override fun encrypt(plaintext: String): String =
                    PlainCipher.encrypt(plaintext).also {
                        threads +=
                            Thread.currentThread().name
                    }

                override fun decrypt(ciphertext: String): String? =
                    PlainCipher.decrypt(ciphertext).also {
                        threads +=
                            Thread.currentThread().name
                    }
            }
        val keystoreThread =
            java.util.concurrent.Executors
                .newSingleThreadExecutor { Thread(it, "keystore-io") }
        try {
            val store = testSessionStore(recording, keystoreThread.asCoroutineDispatcher())
            runBlocking {
                store.save(session)
                store.current()
            }
            // Coroutine debugging may append " @coroutine#n" to the name, so compare the thread, not the suffix.
            assertEquals(2, threads.size)
            assertTrue(threads.all { it.startsWith("keystore-io") })
        } finally {
            keystoreThread.shutdownNow()
        }
    }
}
