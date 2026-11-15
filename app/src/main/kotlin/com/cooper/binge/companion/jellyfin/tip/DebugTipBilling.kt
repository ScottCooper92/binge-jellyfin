package com.cooper.binge.companion.jellyfin.tip

import android.app.Activity
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private const val PURCHASE_DELAY_MILLIS = 600L

/** Prices every step in pounds and "buys" it after a short wait; nothing is charged and no Play client is touched. */
internal class DebugTipBilling : TipBilling {
    override suspend fun prices(): Map<Int, String> {
        val pounds = NumberFormat.getCurrencyInstance(Locale.UK).apply { maximumFractionDigits = 0 }
        return TIP_STEPS.associateWith { pounds.format(it) }
    }

    override suspend fun give(
        activity: Activity,
        step: Int,
    ): TipOutcome {
        delay(PURCHASE_DELAY_MILLIS)
        return TipOutcome.Thanks
    }
}
