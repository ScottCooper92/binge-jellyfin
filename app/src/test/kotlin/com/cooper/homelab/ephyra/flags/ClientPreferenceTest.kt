package com.cooper.homelab.ephyra.flags

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** What an install that already ran a shipped build holds in its preferences file, so the user's choice survives the move to the shared class. */
class ClientPreferenceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val shippedKey = booleanPreferencesKey("client")

    private fun TestScope.store() = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("client.preferences_pb") }

    @Test
    fun `an install that never chose reads on, and nothing is written for it`() =
        runTest {
            val store = store()
            assertTrue(clientPreference(store).value.first())
            assertEquals(emptyMap<Any, Any>(), store.data.first().asMap())
        }

    @Test
    fun `a choice a shipped build wrote is read as it was written`() =
        runTest {
            val store = store()
            store.edit { it[shippedKey] = false }
            assertEquals(false, clientPreference(store).value.first())
            store.edit { it[shippedKey] = true }
            assertEquals(true, clientPreference(store).value.first())
        }

    @Test
    fun `a choice made now is a plain boolean under the key a shipped build reads`() =
        runTest {
            val store = store()
            clientPreference(store).set(false)
            assertEquals(false, store.data.first()[shippedKey])
            assertEquals(
                1,
                store.data
                    .first()
                    .asMap()
                    .size,
            )
        }
}
