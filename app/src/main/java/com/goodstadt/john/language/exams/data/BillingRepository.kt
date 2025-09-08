package com.goodstadt.john.language.exams.data

import android.content.Context
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ConnectionState
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.goodstadt.john.language.exams.BuildConfig
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


object ConnectionState {
    const val CONNECTED = 0
    const val DISCONNECTED = 1
}

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestoreRepository: FirestoreRepository,
    private val connectivityRepository: ConnectivityRepository,
) {

    private val PRODUCT_ID = "unlock_premium_features_v1"
    private val tag = "BillingRepository" // Timber will use the class name, but this is fine

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<Int> = _connectionState.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _isPurchased = MutableStateFlow(false)
    val isPurchased: StateFlow<Boolean> = _isPurchased.asStateFlow()

    val _billingError = MutableStateFlow<String?>(null)
    val billingError: StateFlow<String?> = _billingError.asStateFlow()

    // A dedicated scope for billing operations that won't be cancelled with a ViewModel.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        // ... (Your existing listener logic is fine)
    }

    // Modern builder for Billing Library v7.0.0+
    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()  // Required for non-consumables in v8+
                .build()
        )
        .build()

    /**
     * A robust wrapper that ensures the billing client is connected before executing
     * a suspending action. If not connected, it will attempt to reconnect.
     *
     * @param action A suspend lambda block containing the billing operation to perform.
     */
    private suspend fun executeSuspendingBillingAction(action: suspend () -> Unit) {
        if (billingClient.isReady) {
            action()
        } else {
            Timber.w(tag, "BillingClient not ready. Attempting to reconnect before action.")
            // Attempt to connect first
            connect()

            // After attempting to connect, check again if it's ready.
            if (billingClient.isReady) {
                action()
            } else {
                Timber.e(tag, "Billing action aborted. Could not connect to the billing service.")
                _billingError.value = "Could not connect to the billing service."
            }
        }
    }
    // Your regular, non-suspending actions can use a simpler version
    private fun executeBillingAction(action: () -> Unit) {
        if (billingClient.isReady) {
            action()
        } else {
            Timber.e(tag, "Billing action aborted. Client is not ready.")
            _billingError.value = "Billing service is not connected."
            connect() // Attempt to reconnect for the next time
        }
    }
    /**
     * Public connect function to be called from a ViewModel.
     * This replaces the need for an init block to handle connections.
     */
    fun connect() {
        // --- THIS IS THE START OF THE REFACTORED LOGIC ---
        // We no longer use suspendCancellableCoroutine for the connection.

        if (billingClient.isReady) {
            Timber.d("BillingClient is already connected.")
            scope.launch { checkPurchases() } // If already connected, it's a good time to refresh purchases.
            return
        }

        if (!connectivityRepository.isCurrentlyOnline()) {
            Timber.e("⚠️ No internet connection. Billing service connection aborted.")
            _connectionState.value = ConnectionState.DISCONNECTED
            _billingError.value = "No internet connection."
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Timber.d("Setup finished: Response code=${billingResult.responseCode}")
                    _connectionState.value = ConnectionState.CONNECTED
                    // Launch queries in the background after connecting
                    scope.launch {
                        queryProductDetails()
                        checkPurchases()
                    }
                } else {
                    _billingError.value = billingResult.debugMessage
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }

            override fun onBillingServiceDisconnected() {
                Timber.d("Service disconnected")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }

    // --- THIS FUNCTION IS NOW ROBUST AGAINST CRASHES ---
    private suspend fun queryProductDetails() {
        executeSuspendingBillingAction {
            val params = QueryProductDetailsParams.newBuilder().setProductList(
                listOf(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP).build()
                )
            ).build()

            try {
                // Use the modern suspend function directly
                val result = billingClient.queryProductDetails(params)
                val billingResult = result.billingResult
                val detailsList = result.productDetailsList ?: emptyList()

                Timber.d("Product details query: Response code=${billingResult.responseCode}, Debug=${billingResult.debugMessage}")

                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && detailsList.isNotEmpty()) {
                    _productDetails.value = detailsList.firstOrNull()
                } else {
                    _billingError.value = billingResult.debugMessage ?: "No product details returned for $PRODUCT_ID"
                }
            } catch (e: Exception) {
                // THIS CATCH BLOCK PREVENTS THE CRASH
                Timber.e(e, "An exception occurred during queryProductDetails. This is expected without internet.")
                _billingError.value = "Failed to query products. Please check your connection."
            }
        }

    }

    // --- THIS FUNCTION IS ALSO NOW ROBUST AGAINST CRASHES ---
    suspend fun checkPurchases() {
        executeSuspendingBillingAction {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()

            try {
                // We must wrap the callback-based API in a coroutine
                val purchases = suspendCancellableCoroutine<List<Purchase>> { continuation ->
                    billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            continuation.resume(purchasesList) // Success: return the list
                        } else {
                            // Failure: throw an exception
                            continuation.resumeWithException(Exception(billingResult.debugMessage))
                        }
                    }
                }
                // If the coroutine completes successfully, 'purchases' will have the list
                val purchased = purchases.any { it.products.contains(PRODUCT_ID) && it.isAcknowledged }
                _isPurchased.value = purchased

            } catch (e: Exception) {
                // This will catch the exception from the coroutine or any other error
                Timber.e(e, "An exception occurred during checkPurchases.")
                _billingError.value = "Failed to check purchases: ${e.message}"
            }
        }
    }
    /**
     * Launch the purchase flow. Call from UI with an Activity context.
     */
    fun launchPurchase(activity: android.app.Activity) {
        executeBillingAction {
            val productDetails = _productDetails.value ?: run {
                Timber.e("No product details available")
                return@executeBillingAction
            }

            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()

            val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
            Timber.d("Launch billing flow: Response code=${billingResult.responseCode}, Debug=${billingResult.debugMessage}")
        }

    }
    // --- This function can also be made safer ---
    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.products.contains(PRODUCT_ID) &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            !purchase.isAcknowledged
        ) {
            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken).build()

            try {
                // Wrap the callback-based acknowledge API
                val billingResult = suspendCancellableCoroutine<BillingResult> { continuation ->
                    billingClient.acknowledgePurchase(acknowledgeParams) { result ->
                        continuation.resume(result)
                    }
                }

                // Now check the result from the coroutine
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isPurchased.value = true
                } else {
                    _billingError.value = billingResult.debugMessage
                }
            } catch (e: Exception) {
                _billingError.value = "Failed to acknowledge purchase: ${e.message}"
            }
        }
    }

    /**
     * Logs current billing status for debugging.
     */
    fun logCurrentStatus() {

        val apiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = apiAvailability.isGooglePlayServicesAvailable(context)
        if (resultCode != ConnectionResult.SUCCESS) {
            Timber.w("Google Play Services not available: $resultCode")
        }else{
            Timber.w("Google Play Services IS available: $resultCode")
        }

        Timber.w("Connection State: ${connectionState.value}")
        Timber.w("Product Details: ${_productDetails.value?.let { "${it.name} - ${it.oneTimePurchaseOfferDetails?.formattedPrice}" } ?: "None"}")
        Timber.w("Is Purchased: ${_isPurchased.value}")
        Timber.w("Last Error: ${_billingError.value ?: "None"}")
    }
    fun logGmsState(ctx: Context) {
        val gmsAvail = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(ctx)
        Timber.d("availabilityCode=$gmsAvail") // 0 == SUCCESS

        fun pkgInfo(p: String) = try {
            val pm = ctx.packageManager
            val pi = pm.getPackageInfo(p, 0)
            "v=${pi.versionName} (${pi.longVersionCode}) updated=${pi.lastUpdateTime}"
        } catch (e: Exception) { "not installed" }

        Timber.d("com.google.android.gms: ${pkgInfo("com.google.android.gms")}")
        Timber.d("com.android.vending: ${pkgInfo("com.android.vending")}")
    }
    fun initializeGoogleServices(ctx: Context) {
        Timber.d("initializeGoogleServices()")
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(ctx)

        when (resultCode) {
            ConnectionResult.SUCCESS -> {}
            ConnectionResult.SERVICE_MISSING, ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, ConnectionResult.SERVICE_DISABLED ->
                Timber.d("initializeGoogleServices() resultCode:$resultCode")
                //availability.getErrorDialog(this, resultCode, 0).show()

            else ->             // Disable GMS features gracefully
                Timber.w("Google Play Services unavailable: $resultCode")
        }
    }

    /**
     * DEBUG ONLY. Queries all owned non-consumable products and consumes them.
     * This effectively "resets" the user's premium status for re-testing.
     */
    fun debugResetAllPurchases() {
       // if (!BuildConfig.DEBUG) return // Safety check

        Timber.i("Debug reset triggered. Querying all owned items...")
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Timber.i("Found ${purchases.size} items to reset.")
                for (purchase in purchases) {
                    // We only care about our specific product
                    if (purchase.products.contains(PRODUCT_ID)) {
                        Timber.i("Found ${PRODUCT_ID} consuming.")
                        consumeTestPurchase(purchase)
                    }
                }
            }
        }
    }
    /**
     * Consumes a purchase for a license tester. This makes a non-consumable product
     * available to be purchased again. THIS SHOULD ONLY BE CALLED IN DEBUG BUILDS.
     */
    private fun consumeTestPurchase(purchase: Purchase) {
        Timber.d("Debug build detected. Consuming test purchase to allow re-testing...")

        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, purchaseToken ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Timber.i("✅ Test purchase consumed successfully. The item can be bought again.")
                // After consuming, the user is no longer a premium member.
                // We reset the status back to NotPremium.
                // We need to re-query the product details to do this.
                //queryProductDetails()
                CoroutineScope(Dispatchers.IO).launch {
                    queryProductDetails()
                }
            } else {
                Timber.e("❌ Failed to consume test purchase. Code: ${billingResult.responseCode}")
            }
        }
    }
}