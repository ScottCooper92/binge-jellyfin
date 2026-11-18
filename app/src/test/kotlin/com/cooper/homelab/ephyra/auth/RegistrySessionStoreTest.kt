package com.cooper.homelab.ephyra.auth

import androidx.datastore.preferences.core.stringPreferencesKey
import com.binge.companion.kit.SecretCipher
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.PlainCipher
import com.cooper.homelab.ephyra.session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The registry-backed store beside the one the released versions shipped, over real DataStores in a temp folder. */
class RegistrySessionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val session = FakeJellyfin().run { session().also { close() } }

    @Test
    fun `a saved session reads back whole`() =
        runBlocking {
            val phone = Phone(folder.root)

            assertTrue(phone.store.save(session))

            assertEquals(session, phone.store.current())
            phone.stop()
        }

    @Test
    fun `saving again replaces the session and keeps one server`() =
        runBlocking {
            val phone = Phone(folder.root)
            phone.store.save(session)

            phone.store.save(session.copy(userId = "user-2", userName = "bo", token = "other"))

            assertEquals("user-2", phone.store.current()?.userId)
            assertEquals(
                1,
                phone.registry
                    .current()
                    .servers.size,
            )
            phone.stop()
        }

    @Test
    fun `a sign out forgets the session and keeps the device id`() =
        runBlocking {
            val phone = Phone(folder.root)
            val deviceId = phone.store.deviceId()
            phone.store.save(session)

            phone.store.clear()

            assertNull(phone.store.current())
            assertTrue(
                phone.registry
                    .current()
                    .servers
                    .isEmpty(),
            )
            assertEquals(deviceId, phone.store.deviceId())
            phone.stop()
        }

    @Test
    fun `a token that will not decrypt reads as signed out`() =
        runBlocking {
            val lost =
                object : SecretCipher by PlainCipher {
                    var lost = false

                    override fun decrypt(ciphertext: String): String? = if (lost) null else PlainCipher.decrypt(ciphertext)
                }
            val phone = Phone(folder.root, lost)
            phone.store.save(session)

            lost.lost = true

            assertNull(phone.store.session.first())
            phone.stop()
        }

    @Test
    fun `a keystore that refuses to encrypt saves nothing and leaves no server`() =
        runBlocking {
            val broken =
                object : SecretCipher by PlainCipher {
                    override fun encrypt(plaintext: String): String = throw IllegalStateException("keymaster")
                }
            val phone = Phone(folder.root, broken)

            assertFalse(phone.store.save(session))

            assertNull(phone.store.current())
            assertTrue(
                phone.registry
                    .current()
                    .servers
                    .isEmpty(),
            )
            phone.stop()
        }

    @Test
    fun `a failed save over a saved server leaves it as it was`() =
        runBlocking {
            var broken = false
            val flaky =
                object : SecretCipher by PlainCipher {
                    override fun encrypt(plaintext: String): String =
                        if (broken) throw IllegalStateException("keymaster") else PlainCipher.encrypt(plaintext)
                }
            val phone = Phone(folder.root, flaky)
            phone.store.save(session)
            broken = true

            assertFalse(phone.store.save(session.copy(baseUrl = "http://other.example.com", userId = "user-2")))

            assertEquals(session, phone.store.current())
            phone.stop()
        }

    @Test
    fun `an address that is not one is not saved`() =
        runBlocking {
            val phone = Phone(folder.root)

            assertFalse(phone.store.save(session.copy(baseUrl = "")))
            phone.stop()
        }

    @Test
    fun `the session survives the app restarting`() =
        runBlocking {
            val first = Phone(folder.root)
            first.store.save(session)
            first.stop()

            val second = Phone(folder.root)

            assertEquals(session, second.store.current())
            second.stop()
        }

    @Test
    fun `the stored fields are the ones the importer writes`() {
        val parts = JellyfinLoginCodec.split(JellyfinLogin("srv", "10.11.6", "u1", "ann", "tok"))

        assertEquals(mapOf("serverId" to "srv", "serverVersion" to "10.11.6", "userId" to "u1", "userName" to "ann"), parts.fields)
        assertEquals("tok", parts.secret)
        assertNull(JellyfinLoginCodec.join(mapOf("serverId" to "srv"), "tok"))
    }

    @Test
    fun `the server id is minted once, is the same after a restart and does not depend on the device id`() =
        runBlocking {
            val first = Phone(folder.root)
            val id = first.serverIdentity.currentServerId()
            val device = first.store.deviceId()
            first.stop()

            val second = Phone(folder.root)

            assertEquals(id, second.serverIdentity.currentServerId())
            assertNotEquals(device, id.value)
            second.stop()
        }

    @Test
    fun `an id already in the file is read as it is`() =
        runBlocking {
            val phone = Phone(folder.root)
            phone.identityFile.updateData { it.toMutablePreferences().also { p -> p[stringPreferencesKey("current_server_id")] = "kept" } }

            assertEquals("kept", phone.serverIdentity.currentServerId().value)
            phone.stop()
        }
}
