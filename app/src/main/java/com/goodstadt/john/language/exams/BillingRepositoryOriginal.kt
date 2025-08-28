package com.goodstadt.john.language.exams.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.ProductDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Data class to hold product details for the UI
data class InAppProductOriginal(
    val id: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    // Internal detail needed to launch the purchase flow
    internal val productDetails: ProductDetails
)

sealed interface PremiumStatusOriginal {
    /** The status is still being determined. Show a loading state. */
    data object Checking : PremiumStatusOriginal

    /** The user has purchased the premium unlock. */
    data object IsPremium : PremiumStatusOriginal

    /** The user has not purchased, but the IAP product is available to be bought. */
    data class NotPremium(val product: InAppProductOriginal) : PremiumStatusOriginal

    /** The billing service is unavailable or the product could not be found. */
    data object Unavailable : PremiumStatusOriginal
}

private const val BILLING_TAG = "BillingRepo"
private const val PRODUCT_ID = "unlock_premium_features_v1"

@Singleton
class BillingRepositoryOriginal @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _premiumStatus = MutableStateFlow<PremiumStatusOriginal>(PremiumStatusOriginal.Checking)
    val premiumStatus = _premiumStatus.asStateFlow()

    // --- State Flows to expose billing status to the rest of the app ---
//    private val _premiumProduct = MutableStateFlow<InAppProduct?>(null)
//    val premiumProduct = _premiumProduct.asStateFlow()
//
//    private val _isPremiumUser = MutableStateFlow(false)
//    val isPremiumUser = _isPremiumUser.asStateFlow()



    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
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

    // 2. Now that the listener is initialized, we can safely use it to build the client.
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        // The enablePendingPurchases() method now requires a parameter.
        // We create one to specify that we support pending purchases for
        // one-time products (which is what your IAP is).

        .build()

    // The init block can now safely use the fully constructed billingClient.
    init {
        connectToBillingService()
    }

    private fun connectToBillingService() {
        Log.d(BILLING_TAG, "Attempting to start billing service connection...")

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                // --- THIS LOG TELLS YOU IF THE CONNECTION IS SUCCESSFUL ---
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(BILLING_TAG, "✅ Billing service setup finished successfully.")
                    // Now that connection is successful, query products and purchases.
//                    queryPremiumProductDetails()
                    checkCurrentUserPurchases()
                } else {
                    _premiumStatus.value = PremiumStatusOriginal.Unavailable
                    Log.e(BILLING_TAG, "❌ Billing service setup failed! Response code: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(BILLING_TAG, "⚠️ Billing service disconnected. Will retry connection...")
                // You might want to add a retry with backoff logic here in a real app
                // connectToBillingService()
            }
        })
    }


    private fun queryPremiumProductDetails() {
        Log.d(BILLING_TAG, "Querying for product details...")
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList)

//        billingClient.queryProductDetailsAsync(params.build()) { billingResult, productDetailsList ->
//            // --- THESE LOGS TELL YOU THE RESULT OF THE PRODUCT QUERY ---
//            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
//                if (productDetailsList.isNotEmpty()) {
//                    val productDetails = productDetailsList[0]
//                    Log.i(BILLING_TAG, "✅ Successfully found product details:")
//                    Log.i(BILLING_TAG, "   - ID: ${productDetails.productId}")
//                    Log.i(BILLING_TAG, "   - Name: ${productDetails.name}")
//                    Log.i(BILLING_TAG, "   - Price: ${productDetails.oneTimePurchaseOfferDetails?.formattedPrice}")
//                    Log.i(BILLING_TAG, "   - Description: ${productDetails.description}")
//
//                    // This is where your _premiumProduct StateFlow is updated
//                    // ... your existing logic to create and set _premiumProduct.value ...
//
//                } else {
//                    Log.w(BILLING_TAG, "⚠️ Query successful, but the product list is EMPTY. Check your Product ID ('unlock_premium_features_v1') and ensure the product is 'Active' in the Play Console.")
//                }
//            } else {
//                Log.e(BILLING_TAG, "❌ Failed to query product details! Response code: ${billingResult.responseCode} - ${billingResult.debugMessage}")
//                Log.e(BILLING_TAG, "   - Common causes: App signature mismatch (running a debug build?), incorrect tester account, or Play Store cache issues.")
//            }
//        }

        billingClient.queryProductDetailsAsync(params.build()) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                // Product found! The user is not premium, but the product is available.
                val productDetails = productDetailsList[0]
                Log.i(BILLING_TAG, "✅ Successfully found product details:")
                Log.i(BILLING_TAG, "   - ID: ${productDetails.productId}")
                Log.i(BILLING_TAG, "   - Name: ${productDetails.name}")
                Log.i(BILLING_TAG, "   - Price: ${productDetails.oneTimePurchaseOfferDetails?.formattedPrice}")
                Log.i(BILLING_TAG, "   - Description: ${productDetails.description}")

                val offerDetails = productDetails.oneTimePurchaseOfferDetails
                if (offerDetails != null) {
                    _premiumStatus.value = PremiumStatusOriginal.NotPremium(
                        InAppProductOriginal(
                            id = productDetails.productId,
                            title = productDetails.name,
                            description = productDetails.description,
                            formattedPrice = offerDetails.formattedPrice,
                            productDetails = productDetails
                        )
                    )
                } else {
                    _premiumStatus.value = PremiumStatusOriginal.Unavailable
                }
            } else {
                // If we can't find the product, the IAP system is effectively unavailable.
                _premiumStatus.value = PremiumStatusOriginal.Unavailable
            }
        }

    }

//    fun launchPurchaseFlow(activity: Activity) {
//        val product = _premiumProduct.value ?: return
//        val productDetailsParamsList = listOf(
//            BillingFlowParams.ProductDetailsParams.newBuilder()
//                .setProductDetails(product.productDetails)
//                .build()
//        )
//        val billingFlowParams = BillingFlowParams.newBuilder()
//            .setProductDetailsParamsList(productDetailsParamsList)
//            .build()
//
//        billingClient.launchBillingFlow(activity, billingFlowParams)
//    }
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

        // The actual call to the billing client remains the same.
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
                    _premiumStatus.value = PremiumStatusOriginal.IsPremium
                    // You might want to save this to Firestore or UserPreferences as well
                }
            }
        }
    }



    private fun checkCurrentUserPurchases() {
        Log.d(BILLING_TAG, "Checking for existing user purchases...")
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)



        billingClient.queryPurchasesAsync(params.build()) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPremium = purchases.any { it.products.contains(PRODUCT_ID) && it.isAcknowledged }
                if (hasPremium) {
                    // If the user is premium, we are done. Set the state.
                    _premiumStatus.value = PremiumStatusOriginal.IsPremium
                } else {
                    // If not premium, we now need to query for the product to sell.
                    queryPremiumProductDetails()
                }
            } else {
                // If we can't query purchases, the service is unavailable.
                _premiumStatus.value = PremiumStatusOriginal.Unavailable
            }
        }

    }
    /**
     * A utility function for debugging. It prints the current state of the
     * premiumStatus StateFlow to Logcat. This is useful for calling from a
     * debug-only button in the UI to instantly check the billing state.
     */
    fun logCurrentStatus() {
        // Get the current value from the StateFlow.
        val currentStatus = _premiumStatus.value

        // Use a StringBuilder for clean, multi-line logging.
        val summary = StringBuilder("\n--- BILLING REPOSITORY DEBUG STATUS ---\n")

        // Use a 'when' block to log details specific to the current state.
        when (currentStatus) {
            is PremiumStatusOriginal.Checking -> {
                summary.append("Status: Checking\n")
                summary.append("Details: The repository is currently connecting to the billing service or querying purchases.\n")
            }
            is PremiumStatusOriginal.IsPremium -> {
                summary.append("Status: IsPremium\n")
                summary.append("Details: The user is a confirmed premium member.\n")
            }
            is PremiumStatusOriginal.NotPremium -> {
                summary.append("Status: NotPremium\n")
                summary.append("Details: The user is not premium, but a product is available for purchase.\n")
                summary.append("  Product ID: ${currentStatus.product.id}\n")
                summary.append("  Product Name: ${currentStatus.product.title}\n")
                summary.append("  Formatted Price: ${currentStatus.product.formattedPrice}\n")
            }
            is PremiumStatusOriginal.Unavailable -> {
                summary.append("Status: Unavailable\n")
                summary.append("Details: The billing service could not be reached, or the product was not found. This is expected for standard debug builds.\n")
            }

            else -> {
                summary.append("Status: Unknown\n")
            }
        }

        summary.append("--------------------------------------")

        // Print the entire summary to Logcat using your consistent tag.
        Log.d(BILLING_TAG, summary.toString())
    }

}