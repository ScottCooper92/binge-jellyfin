package com.cooper.binge.companion.jellyfin.tip

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase.PurchaseState

/** How Play's answer to a purchase reads to the user. A purchase that is not complete yet is [TipOutcome.Pending]. */
internal fun outcomeOf(
    responseCode: Int,
    purchaseStates: List<Int>,
): TipOutcome =
    when {
        responseCode == BillingResponseCode.USER_CANCELED -> TipOutcome.Cancelled
        responseCode != BillingResponseCode.OK -> TipOutcome.Failed
        PurchaseState.PURCHASED in purchaseStates -> TipOutcome.Thanks
        PurchaseState.PENDING in purchaseStates -> TipOutcome.Pending
        else -> TipOutcome.Failed
    }

/**
 * The price of each step from the formatted prices Play returned by product id, or null unless **every** step has one.
 * A slider with a gap in it is worse than no slider, so a half-live set of products reads as unavailable.
 */
internal fun pricesOf(formattedByProductId: Map<String, String?>): Map<Int, String>? {
    val prices = TIP_STEPS.associateWith { formattedByProductId[tipProductId(it)]?.takeIf(String::isNotBlank) }
    return prices.takeIf { it.values.all { price -> price != null } }?.mapValues { it.value.orEmpty() }
}

/** The step a Play product id stands for, or null for a product that is not a tip. */
internal fun stepOf(productId: String): Int? = TIP_STEPS.firstOrNull { tipProductId(it) == productId }
