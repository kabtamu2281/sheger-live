package et.ethiolive.app.data

import io.github.jan.supabase.postgrest.from
import java.util.UUID

class WalletRepository(private val backend: Backend) {
    suspend fun gifts() = backend.rpc<List<Gift>>("list_gifts")

    /**
     * One idempotency key per user tap. A retry after a timeout reuses the same key, so the server applies the
     * debit at most once even if the first request actually succeeded.
     */
    suspend fun sendGift(roomId: String, recipientId: String, giftId: String, quantity: Int, idempotencyKey: String = UUID.randomUUID().toString()) =
        backend.rpc<GiftResult>(
            "send_gift",
            params("p_room" to roomId, "p_recipient" to recipientId, "p_gift" to giftId, "p_quantity" to quantity, "p_idempotency_key" to idempotencyKey),
        )

    suspend fun wallet() = backend.rpc<Wallet>("my_wallet")
    suspend fun ledger(before: Long? = null) = backend.rpc<List<LedgerEntry>>("my_ledger", params("p_limit" to 30, "p_before" to before))
    suspend fun packages() = backend.rpc<List<CoinPackage>>("list_coin_packages")

    /** Chapa (Telebirr, CBE Birr, cards) hosted checkout. Coins are credited only by the verified webhook. */
    suspend fun chapaCheckout(packageId: String) = backend.function<ChapaCheckout>("payments-chapa", params("package_id" to packageId))

    /** Creates a pending Google Play order; its id is passed to Play as obfuscatedAccountId and verified server-side. */
    suspend fun createPlayOrder(packageId: String) =
        backend.rpc<PaymentOrder>("create_payment_order", params("p_package" to packageId, "p_provider" to "google_play"))

    suspend fun verifyPlayPurchase(orderId: String, productId: String, purchaseToken: String) =
        backend.function<kotlinx.serialization.json.JsonObject>("play-verify", params("order_id" to orderId, "product_id" to productId, "purchase_token" to purchaseToken))

    suspend fun orderStatus(orderId: String): String? = backend.call {
        backend.client.from("payment_orders").select { filter { eq("id", orderId) } }.decodeSingleOrNull<PaymentOrder>()?.status
    }

    suspend fun payoutTerms() = backend.rpc<PayoutTerms>("payout_terms")
    suspend fun requestPayout(beans: Long, method: String, account: String, accountName: String) =
        backend.rpc<PayoutRequest>("request_payout", params("p_beans" to beans, "p_method" to mapOf("type" to method, "account" to account, "account_name" to accountName)))
    suspend fun payouts() = backend.rpc<List<PayoutRequest>>("my_payouts")
    suspend fun creatorDashboard() = backend.rpc<CreatorDashboard>("creator_dashboard")
}
