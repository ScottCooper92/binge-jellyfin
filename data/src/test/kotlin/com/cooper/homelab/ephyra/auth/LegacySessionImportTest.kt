package com.cooper.homelab.ephyra.auth

import androidx.datastore.preferences.core.stringPreferencesKey
import com.binge.companion.kit.SecretCipher
import com.cooper.homelab.core.foundation.LogField
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.PlainCipher
import com.cooper.homelab.ephyra.session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The one-time import, each test starting from a file the released version wrote: [LegacySessionStore] is that version's own
 * store, so the file is exactly the shape it left on a user's phone.
 */
class LegacySessionImportTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val session = FakeJellyfin().run { session().also { close() } }

    @Test
    fun `a session the released version wrote leaves the user signed in to the same server and user`() =
        runBlocking {
            val phone = Phone(folder.root)
            phone.released.save(session)

            val imported = phone.store.current()

            assertEquals(session, imported)
            assertEquals(
                1,
                phone.registry
                    .current()
                    .servers.size,
            )
            phone.stop()
        }

    @Test
    fun `the import entry says whether the user is signed in and carries no server field`() =
        runBlocking {
            val phone = Phone(folder.root)
            phone.released.save(session)

            phone.store.current()

            val fields = phone.logged.single { it.first == "legacy-session-imported" }.second
            assertEquals(listOf(LogField.Flag("signedIn", true)), fields)
            phone.stop()
        }

    @Test
    fun `the token is copied as the blob it is and nothing is decrypted`() =
        runBlocking {
            var decrypts = 0
            val counting =
                object : SecretCipher by PlainCipher {
                    override fun decrypt(ciphertext: String): String? {
                        decrypts++
                        return PlainCipher.decrypt(ciphertext)
                    }
                }
            val phone = Phone(folder.root, counting)
            phone.released.save(session)

            phone.importer.importOnce()

            assertEquals(0, decrypts)
            val blob =
                phone.loginsFile.data
                    .first()
                    .asMap()
                    .filterKeys { it.name.endsWith("/secret") }
                    .values
                    .single()
            assertEquals(phone.legacyFile.data.first()[stringPreferencesKey("token")], blob)
            phone.stop()
        }

    @Test
    fun `the device id moves with the session and a second import does not replace it`() =
        runBlocking {
            val phone = Phone(folder.root)
            phone.released.save(session)
            val released = phone.released.deviceId()

            assertEquals(released, phone.store.deviceId())
            phone.importer.importOnce()
            assertEquals(released, phone.store.deviceId())
            phone.stop()
        }

    @Test
    fun `a user with a device id and no session keeps the device id`() =
        runBlocking {
            val phone = Phone(folder.root)
            val released = phone.released.deviceId()

            assertEquals(released, phone.store.deviceId())
            assertNull(phone.store.current())
            phone.stop()
        }

    @Test
    fun `running the import twice, even after a restart, saves one server`() =
        runBlocking {
            val phone = Phone(folder.root)
            phone.released.save(session)
            phone.importer.importOnce()
            phone.importer.importOnce()
            phone.stop()

            val restarted = Phone(folder.root)
            restarted.importer.importOnce()

            assertEquals(
                1,
                restarted.registry
                    .current()
                    .servers.size,
            )
            restarted.stop()
        }

    @Test
    fun `an old file whose token does not decrypt imports as a saved server that is signed out`() =
        runBlocking {
            var lost = false
            val forgetful =
                object : SecretCipher by PlainCipher {
                    override fun decrypt(ciphertext: String): String? = if (lost) null else PlainCipher.decrypt(ciphertext)
                }
            val phone = Phone(folder.root, forgetful)
            phone.released.save(session)
            lost = true

            assertNull(phone.store.current())
            assertEquals(
                1,
                phone.registry
                    .current()
                    .servers.size,
            )
            phone.stop()
        }

    @Test
    fun `an empty old file imports nothing`() =
        runBlocking {
            val phone = Phone(folder.root)

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
    fun `the old file is only read and is unchanged afterwards`() =
        runBlocking {
            val phone = Phone(folder.root)
            phone.released.save(session)
            val before =
                phone.legacyFile.data
                    .first()
                    .asMap()

            phone.store.current()
            phone.store.deviceId()
            phone.store.clear()

            assertEquals(
                before,
                phone.legacyFile.data
                    .first()
                    .asMap(),
            )
            phone.stop()
        }

    @Test
    fun `a user who signs out under the new stores is not signed back in from the old file at the next start`() =
        runBlocking {
            val phone = Phone(folder.root)
            phone.released.save(session)
            phone.store.current()
            phone.store.clear()
            phone.stop()

            val restarted = Phone(folder.root)

            assertNull(restarted.store.current())
            assertTrue(
                restarted.registry
                    .current()
                    .servers
                    .isEmpty(),
            )
            restarted.stop()
        }

    @Test
    fun `the imported server and a server saved later both have the one server id`() =
        runBlocking {
            val phone = Phone(folder.root)
            phone.released.save(session)
            phone.store.current()
            val imported =
                phone.registry
                    .current()
                    .servers
                    .single()
                    .id
            phone.store.clear()

            phone.store.save(session)

            assertEquals(
                imported,
                phone.registry
                    .current()
                    .servers
                    .single()
                    .id,
            )
            assertEquals(phone.serverIdentity.currentServerId(), imported)
            phone.stop()
        }

    @Test
    fun `an address the shared library cannot read is not imported and is logged`() =
        runBlocking {
            val phone = Phone(folder.root)
            phone.legacyFile.updateData { it.toMutablePreferences().also { p -> p[stringPreferencesKey("base_url")] = "ftp://nas/" } }

            assertNull(phone.store.current())
            assertEquals(listOf("legacy-address-unreadable"), phone.warnings)
            assertNotNull(phone.legacyFile.data.first()[stringPreferencesKey("base_url")])
            phone.stop()
        }
}
