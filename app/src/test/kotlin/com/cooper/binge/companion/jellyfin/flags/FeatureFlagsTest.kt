package com.cooper.binge.companion.jellyfin.flags

import com.cooper.binge.companion.jellyfin.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagsTest {
    @Test
    fun `the app names no Binge unless asked`() {
        assertFalse(Flags().bingePromotion)
        assertFalse(Flags()[Flag.BingePromotion])
        // A build without -PbingePromotion=true, as CI and a release are, leaves it off.
        assertFalse(BuildConfig.BINGE_PROMOTION)
    }

    @Test
    fun `fixed flags hold what they were given`() {
        assertTrue(FixedFeatureFlags(Flags.AllOn).flags.value[Flag.BingePromotion])
        assertFalse(FixedFeatureFlags(Flags()).flags.value.bingePromotion)
    }
}
