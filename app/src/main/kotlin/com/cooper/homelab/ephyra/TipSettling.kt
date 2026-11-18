package com.cooper.homelab.ephyra

import com.cooper.homelab.core.tip.TipBilling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Consumes a tip that was paid for after the app had closed, once, as the app starts. Play refunds a purchase that is not
 * acknowledged within three days, and consuming a tip acknowledges it, so a tip whose payment was slow must not wait for the tip
 * sheet to open. [scope] outlives every screen, so the call is not cancelled with one. Best effort: it throws nothing but
 * cancellation, and a debug build's stand-in does nothing.
 */
internal fun settleTipsAtStart(
    billing: TipBilling,
    scope: CoroutineScope,
) {
    scope.launch { billing.settle() }
}
