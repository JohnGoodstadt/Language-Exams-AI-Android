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
import com.goodstadt.john.language.exams.BuildConfig
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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


//import kotlin.collections.firstOrNull

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val PRODUCT_ID = "unlock_premium_features_v1"

    private val tag = "BillingRepository"

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<Int> = _connectionState.asStateFlow()


    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _isPurchased = MutableStateFlow(false)
    val isPurchased: StateFlow<Boolean> = _isPurchased.asStateFlow()

    val _billingError = MutableStateFlow<String?>(null)
    val billingError: StateFlow<String?> = _billingError.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        Timber.d("Purchases updated: Response code=${billingResult.responseCode}, Debug=${billingResult.debugMessage}")
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            CoroutineScope(Dispatchers.IO).launch {
                purchases.forEach { handlePurchase(it) }
            }
        } else {
            _billingError.value = billingResult.debugMessage
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
     * Connect to BillingClient if not connected. Use in ViewModel init.
     */
    @RequiresApi(30)  // For package visibility; adjust if lower minSdk
    suspend fun connect() {
        if (billingClient.connectionState == ConnectionState.CONNECTED) {
            Timber.d("Already connected")
            return
        }

        suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    Timber.d("Setup finished: Response code=${billingResult.responseCode}, Debug=${billingResult.debugMessage}")
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _connectionState.value = ConnectionState.CONNECTED
                        cont.resume(Unit)
                        CoroutineScope(Dispatchers.IO).launch {
                            queryProductDetails()
                            checkPurchases()
                        }
                    } else {
                        _billingError.value = billingResult.debugMessage
                        cont.resumeWithException(Exception(billingResult.debugMessage))
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Timber.d("Service disconnected")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            })
        }
    }

    private suspend fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
                Timber.d("QueryProductDetailsResult type: ${queryResult.javaClass.name}")
                Timber.d("Product details query: Response code=${billingResult.responseCode}, Debug=${billingResult.debugMessage}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val detailsList = queryResult.productDetailsList
                    if (detailsList.isNotEmpty()) {
                        _productDetails.value = detailsList.firstOrNull()
                        cont.resume(Unit)
                    } else {
                        _billingError.value = "No product details returned"
                        cont.resumeWithException(Exception("No product details for unlock_premium_features_v1"))
                    }
                } else if (billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_TIMEOUT) {
                    _billingError.value = "Timeout communicating with service: ${billingResult.debugMessage}"
                    cont.resumeWithException(Exception("Service timeout: ${billingResult.debugMessage}"))
                } else {
                    _billingError.value = billingResult.debugMessage ?: "Query failed"
                    cont.resumeWithException(Exception(billingResult.debugMessage ?: "Query failed"))
                }
            }
        }
    }

    suspend fun checkPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        suspendCancellableCoroutine { cont ->
            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                Timber.d("Purchases query: Response code=${billingResult.responseCode}, Debug=${billingResult.debugMessage}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val purchased = purchases.any { it.products.contains(PRODUCT_ID) && it.isAcknowledged }
                    _isPurchased.value = purchased
                    cont.resume(Unit)
                } else {
                    _billingError.value = billingResult.debugMessage
                    cont.resumeWithException(Exception(billingResult.debugMessage))
                }
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.products.contains(PRODUCT_ID) &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            !purchase.isAcknowledged
        ) {
            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            suspendCancellableCoroutine { cont ->
                billingClient.acknowledgePurchase(acknowledgeParams) { billingResult ->
                    Timber.d("Acknowledge: Response code=${billingResult.responseCode}, Debug=${billingResult.debugMessage}")
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _isPurchased.value = true
                        cont.resume(Unit)
                    } else {
                        _billingError.value = billingResult.debugMessage
                        cont.resumeWithException(Exception(billingResult.debugMessage))
                    }
                }
            }
        }
    }

    /**
     * Launch the purchase flow. Call from UI with an Activity context.
     */
    fun launchPurchase(activity: android.app.Activity) {
        val productDetails = _productDetails.value ?: run {
            Timber.e("No product details available")
            return
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

    /**
     * Logs current billing status for debugging.
     */
    fun logCurrentStatus() {
        Timber.e("logCurrentStatus")
        val apiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = apiAvailability.isGooglePlayServicesAvailable(context)
        if (resultCode != ConnectionResult.SUCCESS) {
            Timber.e("Google Play Services not available: $resultCode")
        }else{
            Timber.e("Google Play Services IS available: $resultCode")
        }

        Timber.d("Connection State: ${connectionState.value}")
        Timber.d("Product Details: ${_productDetails.value?.let { "${it.name} - ${it.oneTimePurchaseOfferDetails?.formattedPrice}" } ?: "None"}")
        Timber.d("Is Purchased: ${_isPurchased.value}")
        Timber.d("Last Error: ${_billingError.value ?: "None"}")
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