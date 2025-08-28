package com.goodstadt.john.language.exams

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class IAPBillingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val PREMIUM_PRODUCT_ID = "unlock_premium_features_v1"
        private const val TAG = "IAPBillingRepository"
    }

    private var billingClient: BillingClient? = null
    private val _isPremiumUnlocked = MutableStateFlow(false)
    val isPremiumUnlocked: StateFlow<Boolean> = _isPremiumUnlocked.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    sealed class PurchaseState {
        object Idle : PurchaseState()
        object Loading : PurchaseState()
        object Success : PurchaseState()
        data class Error(val message: String) : PurchaseState()
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { handlePurchases(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseState.value = PurchaseState.Error("Purchase cancelled by user")
            }
            else -> {
                _purchaseState.value = PurchaseState.Error("Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }

    suspend fun initializeBilling(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            billingClient = BillingClient.newBuilder(context)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build()

            billingClient?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        continuation.resume(true)
                        // Check existing purchases and load product details
                        checkExistingPurchases()
                        loadProductDetails()
                    } else {
                        continuation.resume(false)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // Handle disconnection if needed
                }
            })
        }
    }

    private fun checkExistingPurchases() {
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.products.contains(PREMIUM_PRODUCT_ID)) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                    _isPremiumUnlocked.value = true
                    _purchaseState.value = PurchaseState.Success
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Purchase acknowledged successfully
            }
        }
    }

    private fun loadProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = productDetailsList.firstOrNull {
                    it.productId == PREMIUM_PRODUCT_ID
                }
            }
        }
    }

    suspend fun purchasePremium(activity: Activity): Boolean {
        val productDetails = _productDetails.value
        if (productDetails == null) {
            _purchaseState.value = PurchaseState.Error("Product details not loaded")
            return false
        }

        _purchaseState.value = PurchaseState.Loading

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient?.launchBillingFlow(activity, billingFlowParams)

        return billingResult?.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun resetPurchaseState() {
        _purchaseState.value = PurchaseState.Idle
    }

    fun disconnect() {
        billingClient?.endConnection()
    }

    fun logCurrentStatus() {
        Log.d(TAG, "=== IAP Billing Status ===")

        // Billing Client Status
        val isReady = billingClient?.isReady ?: false
        Log.d(TAG, "Billing Client Ready: $isReady")

        if (billingClient == null) {
            Log.d(TAG, "Billing Client: NULL")
            return
        }

        // Premium Status
        Log.d(TAG, "Premium Unlocked: ${_isPremiumUnlocked.value}")

        // Purchase State
        when (val state = _purchaseState.value) {
            is PurchaseState.Idle -> Log.d(TAG, "Purchase State: Idle")
            is PurchaseState.Loading -> Log.d(TAG, "Purchase State: Loading")
            is PurchaseState.Success -> Log.d(TAG, "Purchase State: Success")
            is PurchaseState.Error -> Log.d(TAG, "Purchase State: Error - ${state.message}")
        }

        // Product Details
        val productDetails = _productDetails.value
        if (productDetails != null) {
            Log.d(TAG, "Product Details:")
            Log.d(TAG, "  - Product ID: ${productDetails.productId}")
            Log.d(TAG, "  - Name: ${productDetails.name}")
            Log.d(TAG, "  - Description: ${productDetails.description}")
            Log.d(TAG, "  - Product Type: ${productDetails.productType}")

            // One time purchase details
            productDetails.oneTimePurchaseOfferDetails?.let { offerDetails ->
                Log.d(TAG, "  - Price: ${offerDetails.formattedPrice}")
                Log.d(TAG, "  - Currency: ${offerDetails.priceCurrencyCode}")
                Log.d(TAG, "  - Price Amount (micros): ${offerDetails.priceAmountMicros}")
            } ?: Log.d(TAG, "  - No one-time purchase offer details available")
        } else {
            Log.d(TAG, "Product Details: NULL - not loaded yet")
        }

        // Check current purchases asynchronously
        if (isReady) {
            billingClient?.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ) { billingResult, purchases ->
                Log.d(TAG, "Current Purchases Query Response: ${billingResult.responseCode}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Total Purchases Found: ${purchases.size}")
                    purchases.forEach { purchase ->
                        if (purchase.products.contains(PREMIUM_PRODUCT_ID)) {
                            Log.d(TAG, "Premium Purchase Found:")
                            Log.d(TAG, "  - Purchase State: ${purchase.purchaseState}")
                            Log.d(TAG, "  - Acknowledged: ${purchase.isAcknowledged}")
                            Log.d(TAG, "  - Purchase Time: ${purchase.purchaseTime}")
                            Log.d(TAG, "  - Order ID: ${purchase.orderId}")
                        }
                    }
                    if (purchases.none { it.products.contains(PREMIUM_PRODUCT_ID) }) {
                        Log.d(TAG, "No premium purchases found")
                    }
                } else {
                    Log.d(TAG, "Failed to query purchases: ${billingResult.debugMessage}")
                }
            }
        } else {
            Log.d(TAG, "Cannot query purchases - billing client not ready")
        }

        Log.d(TAG, "=== End IAP Status ===")
    }
}