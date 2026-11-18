package com.cooper.homelab.ephyra.flags

import com.cooper.homelab.core.flags.FixedFeatureFlags
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.SharedFlags
import com.cooper.homelab.ephyra.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagsTest {
    @Test
    fun `the app names no Binge unless asked`() {
        assertFalse(Flags().bingePromotion)
        assertFalse(Flags()[SharedFlags.BingePromotion])
        // A build without -PbingePromotion=true, as CI and a release are, leaves it off.
        assertFalse(BuildConfig.BINGE_PROMOTION)
    }

    @Test
    fun `fixed flags hold what they were given`() {
        assertTrue(FixedFeatureFlags(Flags.of(SharedFlags.BingePromotion to true)).flags.value[SharedFlags.BingePromotion])
        assertFalse(FixedFeatureFlags(Flags()).flags.value.bingePromotion)
    }
}
