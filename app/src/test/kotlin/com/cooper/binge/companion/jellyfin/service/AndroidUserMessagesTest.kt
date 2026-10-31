package com.cooper.binge.companion.jellyfin.service

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidUserMessagesTest {
    @Test
    @Config(qualifiers = "es")
    fun `the sentences come from the app's resources in the device's language`() {
        val messages = AndroidUserMessages(ApplicationProvider.getApplicationContext())
        assertEquals("Inicia sesión en Jellyfin para ver tu biblioteca", messages.signedOut())
        assertEquals("No se ha podido conectar con tu servidor de Jellyfin", messages.unreachable())
        assertEquals("es", messages.locale())
    }
}
