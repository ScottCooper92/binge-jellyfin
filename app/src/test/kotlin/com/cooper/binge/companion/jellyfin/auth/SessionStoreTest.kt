package com.cooper.binge.companion.jellyfin.auth

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.PlainCipher
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
}
