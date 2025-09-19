package com.goodstadt.john.language.exams.data

import android.app.Activity
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
import com.android.billingclient.api.PurchasesResult
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- State Flows Exposed to the App ---
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<Int> = _connectionState.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _isPurchased = MutableStateFlow(false)
    val isPurchased: StateFlow<Boolean> = _isPurchased.asStateFlow()

    private val _billingError = MutableStateFlow<String?>(null)
    val billingError: StateFlow<String?> = _billingError.asStateFlow()

    // --- Core Billing Client Setup ---
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            Timber.d("New purchase event received. Processing ${purchases.size} purchase(s).")
            scope.launch {
                processPurchases(purchases)
            }
        } else {
            _billingError.value = "Purchase failed with code: ${billingResult.responseCode}"
            Timber.e("Purchase update error: ${billingResult.debugMessage}")
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()  // Required for non-consumables in v8+
                .build()
        )
        .build()

    /**
     * Public connect function to be called from a ViewModel.
     * Starts the connection to the Google Play Billing service.
     */
    fun connect() {
        if (billingClient.isReady) {
            Timber.d("BillingClient is already connected.")
            scope.launch { checkPurchases() } // Refresh purchases on reconnect
            return
        }

        if (!connectivityRepository.isCurrentlyOnline()) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _billingError.value = "No internet connection."
            Timber.w("Billing connection aborted: No internet.")
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connectionState.value = ConnectionState.CONNECTED
                    Timber.i("✅ Billing service connected successfully.")
                    scope.launch {
                        queryProductDetails()
                        checkPurchases()
                    }
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _billingError.value = "Billing setup failed: ${billingResult.debugMessage}"
                    Timber.e("Billing setup failed. Code: ${billingResult.responseCode}")
                }
            }
            override fun onBillingServiceDisconnected() {
                _connectionState.value = ConnectionState.DISCONNECTED
                Timber.w("Billing service disconnected.")
            }
        })
    }

    /**
     * Queries the details of the premium product from the Play Store.
     */
    private suspend fun queryProductDetails() {
        if (!billingClient.isReady) {
            Timber.e("queryProductDetails failed: BillingClient not ready.")
            return
        }
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP).build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        try {
            val result = billingClient.queryProductDetails(params)
            val detailsList = result.productDetailsList ?: emptyList()
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK && detailsList.isNotEmpty()) {
                _productDetails.value = detailsList.firstOrNull()
                Timber.i("✅ Product details found for $PRODUCT_ID")
            } else {
                _billingError.value = "Product details not found for $PRODUCT_ID"
                Timber.w("Product details query failed or returned empty. Code: ${result.billingResult.responseCode}")
            }
        } catch (e: Exception) {
            _billingError.value = "Failed to query products: ${e.message}"
            Timber.e(e, "Exception during queryProductDetails.")
        }
    }

    /**
     * Checks for any existing purchases the user has made to restore their premium status.
     */
    suspend fun checkPurchases() {
        if (!billingClient.isReady) {
            Timber.e("checkPurchases failed: BillingClient not ready.")
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()

        try {
            // --- THIS IS THE CORRECTED LOGIC ---
            // We must wrap the callback-based API in a coroutine to "await" its result.
            val purchasesResult = suspendCancellableCoroutine<PurchasesResult> { continuation ->
                // The queryPurchasesAsync function takes the params and a listener.
                billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
                    // This listener block is called when the query is complete.
                    // We wrap both results in our own PurchasesResult object.
                    if (continuation.isActive) {
                        continuation.resume(PurchasesResult(billingResult, purchasesList))
                    }
                }
            }
            // --- END OF CORRECTION ---

            // Now we can process the result that the coroutine returned.
            val billingResult = purchasesResult.billingResult
            val purchases = purchasesResult.purchasesList

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Timber.d("Purchases query successful. Found ${purchases.size} items.")
                // Pass the list to the central processor.
                processPurchases(purchases)
            } else {
                _billingError.value = "Failed to query purchases: ${billingResult.debugMessage}"
                Timber.w("checkPurchases query failed. Code: ${billingResult.responseCode}")
            }
        } catch (e: Exception) {
            // This will catch any exceptions from the coroutine bridge.
            _billingError.value = "Failed to check purchases: ${e.message}"
            Timber.e(e, "Exception during checkPurchases.")
        }
    }

    /**
     * Launches the Google Play purchase flow for the premium product.
     */
    fun launchPurchase(activity: Activity) {
        if (!billingClient.isReady) {
            _billingError.value = "Cannot make purchase. Billing service not connected."
            Timber.e("launchPurchase failed: BillingClient not ready.")
            return
        }
        val productDetails = _productDetails.value ?: run {
            _billingError.value = "Product details not available to launch purchase."
            Timber.e("launchPurchase failed: Product details are null.")
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails).build()
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams)).build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    /**
     * Central function to process a list of purchases, either from a new transaction
     * or from a query of existing purchases. It handles acknowledgment and updates the app state.
     */
    private suspend fun processPurchases(purchases: List<Purchase>) {
        val premiumPurchase = purchases.firstOrNull { it.products.contains(PRODUCT_ID) }

        if (premiumPurchase != null && premiumPurchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!premiumPurchase.isAcknowledged) {
                // New purchase that needs to be acknowledged.
                Log.d("tag", "Purchase is new. Acknowledging...")
                val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(premiumPurchase.purchaseToken).build()

                // --- THIS IS THE CORRECTED LOGIC ---
                // We must wrap the callback-based acknowledgePurchase API in a coroutine.
                try {
                    val ackResult = suspendCancellableCoroutine<BillingResult> { continuation ->
                        // The acknowledgePurchase function takes the params and a listener.
                        billingClient.acknowledgePurchase(acknowledgeParams) { billingResult ->
                            // This listener block is called when the acknowledgment is complete.
                            if (continuation.isActive) {
                                continuation.resume(billingResult)
                            }
                        }
                    }

                    // Now we can check the result that the coroutine returned.
                    if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _isPurchased.value = true
                        firestoreRepository.fbUpdateUsePurchasedProperty()
                        Timber.i("✅ Purchase acknowledged and user status set to premium.")
                    } else {
                        _billingError.value = "Failed to acknowledge purchase: ${ackResult.debugMessage}"
                        Timber.e("Acknowledgment failed. Code: ${ackResult.responseCode}")
                    }
                } catch (e: Exception) {
                    // Catch any exceptions from the coroutine bridge itself
                    _billingError.value = "An error occurred during purchase acknowledgment: ${e.message}"
                    Timber.e(e, "Exception during acknowledgePurchase.")
                }
                // --- END OF CORRECTION ---

            } else {
                // Existing, already acknowledged purchase. User is premium.
                _isPurchased.value = true
                Timber.d("Existing premium purchase found.")
            }
        } else {
            // No valid, purchased premium item found. User is not premium.
            _isPurchased.value = false
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
    fun printableCurrentStatus()  : String{

        val apiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = apiAvailability.isGooglePlayServicesAvailable(context)
        var playService = "is available"
        if (resultCode != ConnectionResult.SUCCESS) {
            playService = "is NOT available:${resultCode}"
        }

        var conState = "Unknowe"
        when (connectionState.value) {
            ConnectionState.DISCONNECTED -> { conState = "Disconnected" }
            ConnectionState.CONNECTED ->  { conState = "Connected" }
            ConnectionState.CONNECTING ->  { conState = "Connecting" }
            ConnectionState.CLOSED ->  { conState = "Closed" }
        }



        val report =  """
            +conn:${conState}
            +${_productDetails.value?.let { "${it.name} - ${it.oneTimePurchaseOfferDetails?.formattedPrice}" } ?: "None"}
            +Is Purchased?: ${_isPurchased.value}
            +Play Service: ${playService}
            +Last Error:  ${_billingError.value ?: "None"}
        """.trimIndent()

        return report
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