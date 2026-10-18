package et.ethiolive.app.ui.screens

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.billingclient.api.ProductDetails
import et.ethiolive.app.AppContainer
import et.ethiolive.app.MainActivity
import et.ethiolive.app.R
import et.ethiolive.app.billing.PlayPurchaseEvent
import et.ethiolive.app.data.BackendConfig
import et.ethiolive.app.data.CoinPackage
import et.ethiolive.app.data.LedgerEntry
import et.ethiolive.app.data.Wallet
import et.ethiolive.app.ui.components.BackTopBar
import et.ethiolive.app.ui.components.EmptyState
import et.ethiolive.app.ui.components.ErrorState
import et.ethiolive.app.ui.components.LoadContent
import et.ethiolive.app.ui.components.Loading
import et.ethiolive.app.ui.components.NotConfiguredNotice
import et.ethiolive.app.ui.components.SectionHeader
import et.ethiolive.app.ui.components.errorMessage
import et.ethiolive.app.ui.components.rememberLoad
import et.ethiolive.app.ui.nav.Nav
import et.ethiolive.app.ui.nav.Routes
import et.ethiolive.app.ui.theme.Brand
import et.ethiolive.core.AppException
import et.ethiolive.core.Money
import et.ethiolive.core.ServerError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PurchaseStatus { IDLE, WORKING, PENDING, SUCCESS, FAILED }

data class WalletState(
    val wallet: Wallet? = null,
    val packages: List<CoinPackage> = emptyList(),
    val playProducts: Map<String, ProductDetails> = emptyMap(),
    val loading: Boolean = true,
    val error: Throwable? = null,
    val purchase: PurchaseStatus = PurchaseStatus.IDLE,
    val purchaseError: Throwable? = null,
    val awaitingChapaOrder: String? = null,
)

class WalletViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(WalletState())
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            c.billing.events.collect { e ->
                when (e) {
                    is PlayPurchaseEvent.Purchased -> verifyPlay(e)
                    PlayPurchaseEvent.Pending -> _state.value = _state.value.copy(purchase = PurchaseStatus.PENDING)
                    PlayPurchaseEvent.Cancelled -> _state.value = _state.value.copy(purchase = PurchaseStatus.IDLE)
                    is PlayPurchaseEvent.Failed -> _state.value = _state.value.copy(purchase = PurchaseStatus.FAILED,
                        purchaseError = AppException(ServerError.PAYMENT_PROVIDER_UNAVAILABLE))
                }
            }
        }
        // Purchases that completed while the app was closed are verified now (the server ignores duplicates).
        if ("google_play" in BackendConfig.paymentProviders) viewModelScope.launch {
            runCatching { c.billing.unconsumedPurchases() }.getOrDefault(emptyList()).forEach { verifyPlay(it) }
        }
    }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = _state.value.wallet == null, error = null)
        try {
            val wallet = c.wallet.wallet()
            val packages = c.wallet.packages()
            val products = if ("google_play" in BackendConfig.paymentProviders) {
                runCatching { c.billing.productDetails(packages.mapNotNull { it.playProductId }) }.getOrDefault(emptyMap())
            } else emptyMap()
            _state.value = _state.value.copy(wallet = wallet, packages = packages, playProducts = products, loading = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, error = e)
        }
    }

    fun buyWithPlay(activity: MainActivity, pkg: CoinPackage) = viewModelScope.launch {
        val details = pkg.playProductId?.let { _state.value.playProducts[it] }
        if (details == null) {
            _state.value = _state.value.copy(purchase = PurchaseStatus.FAILED, purchaseError = AppException(ServerError.PAYMENT_PROVIDER_UNAVAILABLE))
            return@launch
        }
        _state.value = _state.value.copy(purchase = PurchaseStatus.WORKING, purchaseError = null)
        try {
            val order = c.wallet.createPlayOrder(pkg.id)
            if (!c.billing.launch(activity, details, order.id)) throw AppException(ServerError.PAYMENT_PROVIDER_UNAVAILABLE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(purchase = PurchaseStatus.FAILED, purchaseError = e)
        }
    }

    private suspend fun verifyPlay(e: PlayPurchaseEvent.Purchased) {
        _state.value = _state.value.copy(purchase = PurchaseStatus.WORKING)
        try {
            c.wallet.verifyPlayPurchase(e.orderId, e.productId, e.token)
            _state.value = _state.value.copy(purchase = PurchaseStatus.SUCCESS)
            load()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            _state.value = _state.value.copy(purchase = PurchaseStatus.FAILED, purchaseError = ex)
        }
    }

    fun buyWithChapa(pkg: CoinPackage, open: (String) -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(purchase = PurchaseStatus.WORKING, purchaseError = null)
        try {
            val checkout = c.wallet.chapaCheckout(pkg.id)
            _state.value = _state.value.copy(purchase = PurchaseStatus.PENDING, awaitingChapaOrder = checkout.orderId)
            open(checkout.checkoutUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(purchase = PurchaseStatus.FAILED, purchaseError = e)
        }
    }

    /** Coins are credited only by the verified Chapa webhook; after returning we poll the order for up to a minute. */
    fun checkChapaOrder() {
        val orderId = _state.value.awaitingChapaOrder ?: return
        viewModelScope.launch {
            repeat(12) {
                when (runCatching { c.wallet.orderStatus(orderId) }.getOrNull()) {
                    "paid" -> {
                        _state.value = _state.value.copy(purchase = PurchaseStatus.SUCCESS, awaitingChapaOrder = null)
                        load()
                        return@launch
                    }
                    "failed", "cancelled" -> {
                        _state.value = _state.value.copy(purchase = PurchaseStatus.FAILED, awaitingChapaOrder = null,
                            purchaseError = AppException(ServerError.PAYMENT_PROVIDER_UNAVAILABLE))
                        return@launch
                    }
                }
                delay(5_000)
            }
        }
    }

    fun dismissPurchase() { _state.value = _state.value.copy(purchase = PurchaseStatus.IDLE, purchaseError = null) }
}

@Composable
fun WalletScreen(container: AppContainer, nav: Nav, activity: MainActivity) {
    val vm: WalletViewModel = viewModel { WalletViewModel(container) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val account by container.account.collectAsState()
    val adult = account?.private?.isAdult == true
    var choosing by remember { mutableStateOf<CoinPackage?>(null) }
    val language = currentLanguage()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.checkChapaOrder() }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.wallet), nav::back) {
            IconButton(onClick = { nav.to(Routes.LEDGER) }) { Icon(Icons.AutoMirrored.Filled.ReceiptLong, stringResource(R.string.transactions)) }
        }
        when {
            state.loading -> Loading()
            state.wallet == null -> ErrorState(state.error, onRetry = { vm.load() })
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val w = state.wallet!!
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.background(Brand.heroGradient).padding(20.dp)) {
                            Text(stringResource(R.string.coins), color = Color.White.copy(alpha = 0.85f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CoinIcon(28.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(Money.grouped(w.coins), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                            }
                            if (w.promoCoins > 0) Text(stringResource(R.string.promo_coins_line, Money.grouped(w.promoCoins)), color = Color.White.copy(alpha = 0.85f))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.coins_explainer), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.beans_title)) },
                        supportingContent = { Text(stringResource(R.string.beans_line, Money.grouped(w.beans), Money.grouped(w.beansPending))) },
                        trailingContent = { TextButton(onClick = { nav.to(Routes.CREATOR) }) { Text(stringResource(R.string.creator_dashboard)) } },
                    )
                }
                item { SectionHeader(stringResource(R.string.recharge)) }
                if (!adult) item { NotConfiguredNotice(R.string.err_purchase_age_restricted) }
                if (BackendConfig.paymentProviders.isEmpty()) item { NotConfiguredNotice(R.string.payments_not_configured) }
                items(state.packages, key = { it.id }) { pkg ->
                    val playPrice = pkg.playProductId?.let { state.playProducts[it] }?.oneTimePurchaseOfferDetails?.formattedPrice
                    Surface(
                        onClick = { if (adult) choosing = pkg },
                        enabled = adult && state.purchase != PurchaseStatus.WORKING,
                        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CoinIcon(24.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Money.grouped(pkg.coins.toLong()), style = MaterialTheme.typography.titleMedium)
                                if (pkg.bonusPromoCoins > 0) Text(stringResource(R.string.bonus_promo, pkg.bonusPromoCoins), color = Brand.Emerald, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(playPrice ?: Money.format(pkg.priceMinor, pkg.currency, language), fontWeight = FontWeight.Bold, color = Brand.Gold)
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.purchase_terms), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    choosing?.let { pkg ->
        val providers = BackendConfig.paymentProviders
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(stringResource(R.string.choose_payment)) },
            text = {
                Column {
                    Text(stringResource(R.string.buying_coins, Money.grouped(pkg.coins.toLong()), Money.format(pkg.priceMinor, pkg.currency, language)))
                    if ("google_play" in providers) TextButton(onClick = { choosing = null; vm.buyWithPlay(activity, pkg) }) { Text(stringResource(R.string.pay_google_play)) }
                    if ("chapa" in providers) TextButton(onClick = {
                        choosing = null
                        vm.buyWithChapa(pkg) { url -> CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) }
                    }) { Text(stringResource(R.string.pay_chapa)) }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    when (state.purchase) {
        PurchaseStatus.SUCCESS -> AlertDialog(
            onDismissRequest = vm::dismissPurchase,
            title = { Text(stringResource(R.string.purchase_success)) },
            text = { Text(stringResource(R.string.purchase_success_body)) },
            confirmButton = { TextButton(onClick = vm::dismissPurchase) { Text(stringResource(R.string.ok)) } },
        )
        PurchaseStatus.FAILED -> AlertDialog(
            onDismissRequest = vm::dismissPurchase,
            title = { Text(stringResource(R.string.purchase_failed)) },
            text = { Text(errorMessage(state.purchaseError)) },
            confirmButton = { TextButton(onClick = vm::dismissPurchase) { Text(stringResource(R.string.ok)) } },
        )
        PurchaseStatus.PENDING -> AlertDialog(
            onDismissRequest = vm::dismissPurchase,
            title = { Text(stringResource(R.string.purchase_pending)) },
            text = { Text(stringResource(R.string.purchase_pending_body)) },
            confirmButton = { TextButton(onClick = vm::dismissPurchase) { Text(stringResource(R.string.ok)) } },
        )
        else -> Unit
    }
}

@Composable
private fun ledgerLabel(kind: String): String = stringResource(
    when (kind) {
        "purchase" -> R.string.ledger_purchase
        "gift" -> R.string.ledger_gift
        "settlement" -> R.string.ledger_settlement
        "payout_request", "payout_paid" -> R.string.ledger_payout
        "payout_rejected" -> R.string.ledger_payout_release
        "refund" -> R.string.ledger_refund
        "promo_grant" -> R.string.ledger_promo
        "adjustment" -> R.string.ledger_adjustment
        else -> R.string.ledger_other
    },
)

@Composable
fun LedgerScreen(container: AppContainer, nav: Nav) {
    val vm = rememberLoad("ledger") { container.wallet.ledger() }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.transactions), nav::back)
        LoadContent(vm) { list: List<LedgerEntry> ->
            if (list.isEmpty()) EmptyState(R.string.no_transactions)
            else LazyColumn {
                items(list, key = { it.entryId }) { e ->
                    ListItem(
                        headlineContent = { Text(ledgerLabel(e.kind)) },
                        supportingContent = { Text(relativeTime(e.createdAt) + " · " + stringResource(R.string.balance_after, Money.grouped(e.balanceAfter), e.unit)) },
                        trailingContent = {
                            Text((if (e.amount > 0) "+" else "") + Money.grouped(e.amount) + " " + e.unit,
                                color = if (e.amount >= 0) Brand.Emerald else Brand.Coral, fontWeight = FontWeight.Bold)
                        },
                    )
                }
            }
        }
    }
}

