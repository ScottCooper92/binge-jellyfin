package com.cooper.binge.companion.jellyfin.tip

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val CONNECT_TIMEOUT_MILLIS = 10_000L

/** What Play reported for a purchase: the response code and the states of the purchases it named. */
private class PurchaseUpdate(
    val responseCode: Int,
    val purchases: List<Purchase>,
)

/**
 * [TipBilling] over Google Play Billing. One lock holds a lookup or a purchase at a time, so the client's single
 * purchase listener answers the call that is waiting for it. Release builds use this; a debug build cannot reach Play.
 */
internal class PlayTipBilling(
    context: Context,
) : TipBilling {
    private val updates = Channel<PurchaseUpdate>(Channel.UNLIMITED)
    private val lock = Mutex()
    private var products: Map<Int, ProductDetails> = emptyMap()

    private val client =
        BillingClient
            .newBuilder(context)
            .setListener { result, purchases -> updates.trySend(PurchaseUpdate(result.responseCode, purchases.orEmpty())) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()

    override suspend fun prices(): Map<Int, String>? =
        lock.withLock {
            if (!connect()) return null
            val query =
                QueryProductDetailsParams
                    .newBuilder()
                    .setProductList(
                        TIP_STEPS.map {
                            QueryProductDetailsParams.Product
                                .newBuilder()
                                .setProductId(tipProductId(it))
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                        },
                    ).build()
            val found = client.queryProductDetails(query)
            if (found.billingResult.responseCode != BillingResponseCode.OK) return null
            val details = found.productDetailsList.orEmpty()
            val priced = pricesOf(details.associate { it.productId to it.oneTimePurchaseOfferDetails?.formattedPrice })
            products = if (priced == null) emptyMap() else details.mapNotNull { d -> stepOf(d.productId)?.let { it to d } }.toMap()
            if (priced != null) consumeLeftovers()
            priced
        }

    override suspend fun give(
        activity: Activity,
        step: Int,
    ): TipOutcome =
        lock.withLock {
            val product = products[step] ?: return TipOutcome.Failed
            if (!connect()) return TipOutcome.Failed
            // An update that arrived when nobody was waiting is not this purchase's answer.
            while (updates.tryReceive().isSuccess) Unit
            val flow =
                BillingFlowParams
                    .newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams
                                .newBuilder()
                                .setProductDetails(product)
                                .build(),
                        ),
                    ).build()
            val launched = client.launchBillingFlow(activity, flow)
            if (launched.responseCode != BillingResponseCode.OK) return outcomeOf(launched.responseCode, emptyList())
            val update = updates.receive()
            val outcome = outcomeOf(update.responseCode, update.purchases.map(Purchase::getPurchaseState))
            update.purchases.filter { it.purchaseState == PurchaseState.PURCHASED }.forEach { consume(it) }
            outcome
        }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        val ready = CompletableDeferred<Boolean>()
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    ready.complete(result.responseCode == BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() = Unit
            },
        )
        return withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { ready.await() } ?: false
    }

    /** A tip that was paid but never consumed (the app died mid-purchase) would block buying that step again. */
    private suspend fun consumeLeftovers() {
        val owned =
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            )
        if (owned.billingResult.responseCode != BillingResponseCode.OK) return
        owned.purchasesList
            .filter { it.purchaseState == PurchaseState.PURCHASED && it.products.any { id -> stepOf(id) != null } }
            .forEach { consume(it) }
    }

    private suspend fun consume(purchase: Purchase) {
        client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
    }
}
