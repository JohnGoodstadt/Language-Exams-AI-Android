package com.goodstadt.john.language.exams.data


import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


// --- Use a consistent TAG for easy filtering in Logcat ---
private const val BILLING_TAG = "BillingRepo"

// --- Sealed Interface to represent the user's IAP status ---
sealed interface PremiumStatus {
    data object Checking : PremiumStatus
    data object IsPremium : PremiumStatus
    data class NotPremium(val product: InAppProduct) : PremiumStatus
    data object Unavailable : PremiumStatus
}

// --- Data class to hold product details for the UI ---
data class InAppProduct(
    val id: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    internal val productDetails: ProductDetails
)


@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The single, reactive source of truth for the app's premium status.
    private val _premiumStatus = MutableStateFlow<PremiumStatus>(PremiumStatus.Checking)
    val premiumStatus = _premiumStatus.asStateFlow()

    // 1. Define the listener FIRST to avoid initialization errors.
    private val purchasesUpdatedListener999 = PurchasesUpdatedListener { billingResult, purchases ->
        Log.i(BILLING_TAG, "✅ in purchasesUpdatedListener().")
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            Log.i(BILLING_TAG, "✅ Purchase successful! Processing ${purchases.size} new purchase(s).")
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    handlePurchase(purchase)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.w(BILLING_TAG, "⚠️ User cancelled the purchase flow.")
        } else {
            Log.e(BILLING_TAG, "❌ Purchase flow error! Response code: ${billingResult.responseCode} - ${billingResult.debugMessage}")
        }
    }
    private val purchasesUpdatedListener = object : PurchasesUpdatedListener {

        override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
            // Your existing listener logic goes inside this override method.
            Log.i(BILLING_TAG, "✅ in onPurchasesUpdated().")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                Log.i(BILLING_TAG, "✅ Purchase successful! Processing ${purchases.size} new purchase(s).")
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        handlePurchase(purchase)
                    }
                    // You could also add logic here to handle Purchase.PurchaseState.PENDING
                }
            } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                Log.w(BILLING_TAG, "⚠️ User cancelled the purchase flow.")
            } else {
                Log.e(BILLING_TAG, "❌ Purchase flow error! Response code: ${billingResult.responseCode} - ${billingResult.debugMessage}")
            }
        }
    }
    // 2. Now, build the client using the listener.
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .build()

    init {
        connectToBillingService()
    }

    private fun connectToBillingService() {
        Log.d(BILLING_TAG, "Attempting to start billing service connection...")
        if (billingClient.isReady) {
            Log.d(BILLING_TAG, "BillingClient is already ready.")
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(BILLING_TAG, "✅ Billing service setup finished successfully.")
                    // Start by checking for existing purchases.
                    checkCurrentUserPurchases()
                } else {
                    Log.e(BILLING_TAG, "❌ Billing service setup failed! Response code: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                    _premiumStatus.value = PremiumStatus.Unavailable
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w(BILLING_TAG, "⚠️ Billing service disconnected. Consider retrying connection.")
            }
        })
    }

    private fun checkCurrentUserPurchases() {
        Log.d(BILLING_TAG, "Checking for existing user purchases...")
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        scope.launch {
            // This function is also now a suspend function
            val result = withContext(Dispatchers.IO) {
                billingClient.queryPurchasesAsync(params)
            }

            val billingResult = result.billingResult
            val purchases = result.purchasesList

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPremium = purchases.any { it.products.contains("unlock_premium_features_v1") && it.isAcknowledged }
                if (hasPremium) {
                    _premiumStatus.value = PremiumStatus.IsPremium
                } else {
                    queryPremiumProductDetails() // Still query for products if not premium
                }
            } else {
                _premiumStatus.value = PremiumStatus.Unavailable
            }
        }
    }

    private fun queryPremiumProductDetails() {
        Log.d(BILLING_TAG, "Querying for product details...")
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("unlock_premium_features_v1")
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        // Launch a coroutine to call the new suspend function
        scope.launch {
            // Use withContext to ensure we are on a background thread
            val result = withContext(Dispatchers.IO) {
                // This is the new suspend function. It returns a BillingResult and a List.
                billingClient.queryProductDetails(params)
            }

            val billingResult = result.billingResult
            val productDetailsList = result.productDetailsList ?: emptyList()

            // The rest of the logic is the same as before
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                // ... (update _premiumStatus to NotPremium) ...
                Log.i(BILLING_TAG, "✅ Successfully found product details:")
                Log.i(BILLING_TAG, "   - ID: ${productDetails.productId}")
                Log.i(BILLING_TAG, "   - Name: ${productDetails.name}")
                Log.i(BILLING_TAG, "   - Price: ${productDetails.oneTimePurchaseOfferDetails?.formattedPrice}")
                Log.i(BILLING_TAG, "   - Description: ${productDetails.description}")

            } else {
                Log.e(BILLING_TAG, "❌ Failed to query product details! Response code: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                _premiumStatus.value = PremiumStatus.Unavailable
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(BILLING_TAG, "✅ Purchase acknowledged. User is now premium.")
                    _premiumStatus.value = PremiumStatus.IsPremium
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        Log.d(BILLING_TAG, "Launching purchase flow for product: ${productDetails.productId}")
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    /**
     * A utility function for debugging. Prints the current billing status to Logcat.
     */
    fun logCurrentStatus() {
        val currentStatus = _premiumStatus.value
        val summary = StringBuilder("\n--- BILLING REPOSITORY DEBUG STATUS ---\n")

        when (currentStatus) {
            is PremiumStatus.Checking -> summary.append("Status: Checking\n")
            is PremiumStatus.IsPremium -> summary.append("Status: IsPremium\n")
            is PremiumStatus.NotPremium -> {
                summary.append("Status: NotPremium\n")
                summary.append("  Product ID: ${currentStatus.product.id}\n")
                summary.append("  Formatted Price: ${currentStatus.product.formattedPrice}\n")
            }
            is PremiumStatus.Unavailable -> summary.append("Status: Unavailable\n")
            else -> {summary.append("Status: Non of the above!\n")}
        }

        summary.append("--------------------------------------")
        Log.d(BILLING_TAG, summary.toString())
    }
}