package com.cooper.binge.companion.jellyfin.tip

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase.PurchaseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TipOutcomesTest {
    @Test
    fun `a completed purchase is thanked`() {
        assertEquals(TipOutcome.Thanks, outcomeOf(BillingResponseCode.OK, listOf(PurchaseState.PURCHASED)))
    }

    @Test
    fun `a purchase still waiting on Play is pending`() {
        assertEquals(TipOutcome.Pending, outcomeOf(BillingResponseCode.OK, listOf(PurchaseState.PENDING)))
    }

    @Test
    fun `the user backing out is a cancel, not a failure`() {
        assertEquals(TipOutcome.Cancelled, outcomeOf(BillingResponseCode.USER_CANCELED, emptyList()))
    }

    @Test
    fun `any other response, or an OK with nothing bought, is a failure`() {
        assertEquals(TipOutcome.Failed, outcomeOf(BillingResponseCode.ERROR, emptyList()))
        assertEquals(TipOutcome.Failed, outcomeOf(BillingResponseCode.ITEM_UNAVAILABLE, emptyList()))
        assertEquals(TipOutcome.Failed, outcomeOf(BillingResponseCode.OK, emptyList()))
        assertEquals(TipOutcome.Failed, outcomeOf(BillingResponseCode.OK, listOf(PurchaseState.UNSPECIFIED_STATE)))
    }

    @Test
    fun `prices come out per step when every step is priced`() {
        val formatted = TIP_STEPS.associate { tipProductId(it) to "£$it.00" }
        val prices = pricesOf(formatted)
        assertEquals(TIP_STEPS.toList(), prices?.keys?.toList())
        assertEquals("£5.00", prices?.get(5))
    }

    @Test
    fun `one missing or blank step makes the whole set unavailable`() {
        val formatted = TIP_STEPS.associate { tipProductId(it) to "£$it.00" }
        assertNull(pricesOf(formatted - tipProductId(10)))
        assertNull(pricesOf(formatted + (tipProductId(10) to " ")))
        assertNull(pricesOf(formatted + (tipProductId(10) to null)))
        assertNull(pricesOf(emptyMap()))
    }

    @Test
    fun `a product id maps back to its step, and a stranger maps to none`() {
        assertEquals(10, stepOf(tipProductId(10)))
        assertEquals("donation_10", tipProductId(10))
        assertNull(stepOf("donation_7"))
        assertNull(stepOf("tip_10"))
        assertNull(stepOf("remove_ads"))
    }
}
