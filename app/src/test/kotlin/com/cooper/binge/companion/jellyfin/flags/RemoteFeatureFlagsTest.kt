package com.cooper.binge.companion.jellyfin.flags

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteFeatureFlagsTest {
    private class FakeSource(
        var current: Flags = Flags(),
        var fetched: Flags? = null,
    ) : RemoteFlagSource {
        override fun current(): Flags = current

        override suspend fun fetch(): Flags? = fetched
    }

    @Test
    fun `starts off, and a fetch replaces the values`() {
        val source = FakeSource(current = Flags(bingePromotion = false), fetched = Flags.AllOn)
        val flags = RemoteFeatureFlags(source, io = Dispatchers.Unconfined)
        assertFalse(flags.flags.value.bingePromotion)
        runBlocking { flags.refresh() }
        assertTrue(flags.flags.value.bingePromotion)
    }

    @Test
    fun `a failed fetch keeps the last activated values, read on refresh`() {
        val flags = RemoteFeatureFlags(FakeSource(current = Flags.AllOn, fetched = null), io = Dispatchers.Unconfined)
        assertFalse(flags.flags.value.bingePromotion)
        runBlocking { flags.refresh() }
        assertTrue(flags.flags.value.bingePromotion)
    }

    @Test
    fun `a build's override wins over the remote, whichever way the remote goes`() {
        val source = FakeSource(current = Flags(), fetched = Flags())
        val flags = RemoteFeatureFlags(source, overrides = mapOf(Flag.BingePromotion to true), io = Dispatchers.Unconfined)
        assertTrue(flags.flags.value.bingePromotion)
        runBlocking { flags.refresh() }
        assertTrue(flags.flags.value.bingePromotion)
    }

    @Test
    fun `without a Firebase app, the Firebase source answers every flag off and fetches nothing`() {
        val source = FirebaseRemoteFlagSource(ApplicationProvider.getApplicationContext())
        assertFalse(source.current().bingePromotion)
        assertNull(runBlocking { source.fetch() })
    }
}
