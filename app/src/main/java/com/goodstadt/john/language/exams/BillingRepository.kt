package com.goodstadt.john.language.exams.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.ProductDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

// Data class to hold product details for the UI
data class InAppProduct(
    val id: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    // Internal detail needed to launch the purchase flow
    internal val productDetails: ProductDetails
)

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // --- State Flows to expose billing status to the rest of the app ---
    private val _premiumProduct = MutableStateFlow<InAppProduct?>(null)
    val premiumProduct = _premiumProduct.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                // This is where you process a successful purchase
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    handlePurchase(purchase)
                }
            }
        } else {
            // Handle an error from the purchase flow.
            Log.e("BillingRepo", "Purchase failed with code: ${billingResult.responseCode}")
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    init {
        connectToBillingService()
    }

    private fun connectToBillingService() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingRepo", "Billing service connected.")
                    // Query for products and check for existing purchases
                    queryPremiumProductDetails()
                    checkCurrentUserPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w("BillingRepo", "Billing service disconnected. Retrying...")
                connectToBillingService() // Retry connection
            }
        })
    }

    private fun queryPremiumProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("premium_upgrade_unlock") // The ID you created in Play Console
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList)

        billingClient.queryProductDetailsAsync(params.build()) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val offerDetails = productDetails.oneTimePurchaseOfferDetails
                if (offerDetails != null) {
                    _premiumProduct.value = InAppProduct(
                        id = productDetails.productId,
                        title = productDetails.name,
                        description = productDetails.description,
                        formattedPrice = offerDetails.formattedPrice,
                        productDetails = productDetails
                    )
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        val product = _premiumProduct.value ?: return
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product.productDetails)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        // Here you would typically verify the purchase on your backend server.
        // For a simple unlock, we can proceed if the purchase is not acknowledged yet.
        if (!purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Purchase is successful and acknowledged! Grant entitlement.
                    Log.d("BillingRepo", "Purchase acknowledged. User is now premium.")
                    _isPremiumUser.value = true
                    // You might want to save this to Firestore or UserPreferences as well
                }
            }
        }
    }

    private fun checkCurrentUserPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)

        billingClient.queryPurchasesAsync(params.build()) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPremium = purchases.any { it.products.contains("premium_upgrade_unlock") && it.isAcknowledged }
                _isPremiumUser.value = hasPremium
                Log.d("BillingRepo", "User is premium: $hasPremium")
            }
        }
    }
}