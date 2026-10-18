package et.ethiolive.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface PlayPurchaseEvent {
    /** The purchase must still be verified server-side (play-verify); coins are never granted from the client. */
    data class Purchased(val orderId: String, val productId: String, val token: String) : PlayPurchaseEvent
    data object Pending : PlayPurchaseEvent
    data object Cancelled : PlayPurchaseEvent
    data class Failed(val code: Int) : PlayPurchaseEvent
}

/**
 * Google Play Billing for coin packages (consumables). The order id from our backend is attached as the
 * obfuscated account id, which play-verify checks before crediting and consuming the purchase.
 */
class PlayBilling(context: Context) : PurchasesUpdatedListener {
    private val _events = MutableSharedFlow<PlayPurchaseEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    suspend fun productDetails(productIds: List<String>): Map<String, ProductDetails> {
        if (productIds.isEmpty() || !connect()) return emptyMap()
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            productIds.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build() },
        ).build()
        val result = client.queryProductDetails(params)
        return result.productDetailsList.orEmpty().associateBy { it.productId }
    }

    suspend fun launch(activity: Activity, details: ProductDetails, orderId: String): Boolean {
        if (!connect()) return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()))
            .setObfuscatedAccountId(orderId)
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** Purchases completed while the app was closed; each is re-sent to play-verify (idempotent on the server). */
    suspend fun unconsumedPurchases(): List<PlayPurchaseEvent.Purchased> {
        if (!connect()) return emptyList()
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        return result.purchasesList.mapNotNull { it.toEvent() }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { p ->
                if (p.purchaseState == Purchase.PurchaseState.PENDING) _events.tryEmit(PlayPurchaseEvent.Pending)
                else p.toEvent()?.let { _events.tryEmit(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _events.tryEmit(PlayPurchaseEvent.Cancelled)
            else -> _events.tryEmit(PlayPurchaseEvent.Failed(result.responseCode))
        }
    }

    private fun Purchase.toEvent(): PlayPurchaseEvent.Purchased? {
        if (purchaseState != Purchase.PurchaseState.PURCHASED) return null
        val orderId = accountIdentifiers?.obfuscatedAccountId ?: return null
        return PlayPurchaseEvent.Purchased(orderId, products.firstOrNull() ?: return null, purchaseToken)
    }
}
