package com.cooper.binge.companion.jellyfin.telemetry

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric, for a real Context with no Firebase app in it: a build without google-services.json. */
@RunWith(RobolectricTestRunner::class)
class FirebaseCrashCollectionTest {
    @Test
    fun `with no Firebase app, switching collection is a no-op rather than a crash`() {
        val collection = FirebaseCrashCollection(ApplicationProvider.getApplicationContext())
        collection.setEnabled(true)
        collection.setEnabled(false)
    }
}
