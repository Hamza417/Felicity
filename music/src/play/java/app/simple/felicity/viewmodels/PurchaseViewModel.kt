package app.simple.felicity.viewmodels

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.TrialPreferences
import com.android.billingclient.api.AcknowledgePurchaseParams
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Manages the entire Google Play Billing lifecycle for the one-time purchase of Felicity.
 *
 * This ViewModel keeps the [BillingClient] alive as long as the screen is visible, and
 * automatically retries the connection if something goes sideways. It handles querying
 * the product details (so we can show the real price from the Play Store), launching the
 * purchase flow when the user taps the buy button, and acknowledging the purchase so the
 * Play Store doesn't refund it after a few days — because that would be awkward.
 *
 * @author Hamza417
 */
@HiltViewModel
class PurchaseViewModel @Inject constructor(
        application: Application
) : WrappedViewModel(application) {

    /**
     * The product ID that matches the one you created in Google Play Console.
     * Make sure this matches exactly, or the billing query will return nothing
     * and everyone will be confused.
     */
    private val productId = "felicity_full_version"

    /**
     * The offer IDs we recognize. We check both and apply whichever gives the
     * lowest price — we want users to always get the best deal available.
     */
    private val offers = listOf("early-access", "sale-discount")

    /**
     * All the possible states our purchase screen can be in, so the UI
     * always knows what to show without us having to guess.
     */
    sealed class BillingState {
        /** Still trying to connect to the Play Store — hold tight. */
        data object Connecting : BillingState()

        /** Connected and we have product details ready to show. */
        data class Ready(
                val productDetails: ProductDetails,
                /**
                 * The normal price before any offer is applied.
                 * Always present — this is what you'd pay without any deal.
                 */
                val basePrice: String,
                /**
                 * The discounted price if any of our recognized offers are active, or
                 * null when the full price is the only option. Absence = no deal today.
                 */
                val offerPrice: String?,
                /**
                 * The offer token needed to actually apply the deal when launching the
                 * billing flow. Null when no offer is available.
                 */
                val offerToken: String?
        ) : BillingState()

        /** The user already owns the app — we just need to acknowledge it. */
        data object AlreadyPurchased : BillingState()

        /** Something went wrong. The message explains what. */
        data class Error(val message: String) : BillingState()

        /** The billing service is not available on this device. */
        data object Unavailable : BillingState()
    }

    private val _billingState = MutableLiveData<BillingState>(BillingState.Connecting)
    val billingState: LiveData<BillingState> = _billingState

    /** Fires a one-time event when a purchase completes successfully. */
    private val _purchaseSuccess = MutableLiveData<Boolean>()
    val purchaseSuccess: LiveData<Boolean> = _purchaseSuccess

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null

    /**
     * Listens for purchase updates that happen while the billing flow is open.
     * This is the callback that tells us whether the user actually paid or hit "Back."
     */
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK if !purchases.isNullOrEmpty() -> {
                purchases.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled the purchase flow — maybe next time!")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // They already own it — let's make sure the app knows that.
                restoreExistingPurchases()
            }
            else -> {
                Log.w(TAG, "Billing update failed: ${billingResult.debugMessage}")
                _billingState.postValue(BillingState.Error(billingResult.debugMessage))
            }
        }
    }

    init {
        connectToBillingService()
    }

    /**
     * Kicks off the connection to the Play Store billing service.
     * Once connected, we immediately ask for the product details so the
     * UI can show the real price — no hard-coded numbers here.
     */
    private fun connectToBillingService() {
        _billingState.postValue(BillingState.Connecting)

        billingClient = BillingClient.newBuilder(applicationContext())
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing service connected — let's go shopping!")
                    queryProductDetails()
                    restoreExistingPurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _billingState.postValue(BillingState.Error(billingResult.debugMessage))
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected — will try again on next action.")
            }
        })
    }

    /**
     * Asks the Play Store about our product so we can display the correct local price.
     * If this returns nothing, the product ID is wrong or not set up in Play Console yet.
     */
    private fun queryProductDetails() {
        val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val details: ProductDetails? = productDetailsList.productDetailsList.firstOrNull()
                if (details != null) {
                    productDetails = details

                    // The regular price — always available, no strings attached.
                    val basePrice = details.oneTimePurchaseOfferDetails?.formattedPrice ?: ""

                    // Look for the best (lowest) offer among the ones we recognize.
                    val bestOffer = details.oneTimePurchaseOfferDetailsList
                        ?.filter { it.offerId in offers }
                        ?.minByOrNull { it.priceAmountMicros }

                    val offerPrice = bestOffer?.formattedPrice
                    val offerToken = bestOffer?.offerToken

                    if (offerPrice != null) {
                        Log.d(TAG, "Active offer found: '$offerPrice' (offer: ${bestOffer.offerId})")
                    } else {
                        Log.d(TAG, "No recognized offer active — showing base price.")
                    }

                    // Only switch to Ready if the user hasn't already purchased.
                    if (_billingState.value !is BillingState.AlreadyPurchased) {
                        _billingState.postValue(BillingState.Ready(details, basePrice, offerPrice, offerToken))
                    }
                } else {
                    Log.w(TAG, "No product details found for '$productId'. Check Play Console.")
                    _billingState.postValue(BillingState.Error("Product not found. Please check back later."))
                }
            } else {
                Log.w(TAG, "Product details query failed: ${billingResult.debugMessage}")
                _billingState.postValue(BillingState.Error(billingResult.debugMessage))
            }
        }
    }

    /**
     * Fires a one-time event when a manual restore attempt finds no purchase.
     * The UI uses this to show the "wrong account" warning dialog.
     */
    private val _restoreFailed = MutableLiveData<Boolean>()
    val restoreFailed: LiveData<Boolean> = _restoreFailed

    /**
     * Called when the user taps the "Restore Purchase" button.
     * Reconnects to billing if needed, then queries the Play Store for existing
     * purchases on the current account. If nothing is found we let the user know
     * that their purchase might be sitting on a different Google account.
     */
    fun restorePurchase() {
        val client = billingClient
        if (client == null || !client.isReady) {
            // Not connected yet — reconnect first and let the normal restore
            // that runs after connection handle the result.
            connectToBillingService()
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(params) { _, purchases ->
            val ownedPurchase = purchases.firstOrNull { it.products.contains(productId) }
            if (ownedPurchase != null) {
                // Found it — handle it the same way a fresh purchase would be handled.
                handlePurchase(ownedPurchase)
            } else {
                // Nothing found on this account — the user might have used a different one.
                Log.w(TAG, "Restore failed — no matching purchase on this account.")
                _restoreFailed.postValue(true)
            }
        }
    }

    /**
     * Checks the Play Store for any purchases the user already completed —
     * useful if they reinstalled the app, and we need to restore their access.
     */
    private fun restoreExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(params) { _, purchases ->
            val ownedPurchase = purchases.firstOrNull { purchase ->
                purchase.products.contains(productId)
            }

            if (ownedPurchase != null) {
                handlePurchase(ownedPurchase)
            }
        }
    }

    /**
     * Takes a completed [Purchase] and does the important things: acknowledges it so the
     * Play Store knows we received it, and marks the app as the full version in preferences.
     *
     * @param purchase The purchase object returned by the billing flow or restore query.
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Mark the app as full version right away — the user paid, they deserve it!
            TrialPreferences.setFullVersion(true)
            _billingState.postValue(BillingState.AlreadyPurchased)
            _purchaseSuccess.postValue(true)

            // Acknowledge the purchase so Google doesn't auto-refund it after 3 days.
            if (!purchase.isAcknowledged) {
                val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient?.acknowledgePurchase(acknowledgeParams) { ackResult ->
                    if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Purchase acknowledged — the user is officially a full version owner!")
                    } else {
                        Log.w(TAG, "Acknowledgement failed: ${ackResult.debugMessage}")
                    }
                }
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.d(TAG, "Purchase is pending — waiting for payment to clear.")
        }
    }

    /**
     * Kicks off the Play Store purchase sheet for the user to complete their purchase.
     * Call this when the big buy button is tapped.
     *
     * If an offer is currently active, the offer token is attached automatically so the
     * user gets the discounted price — no extra taps needed on their end.
     *
     * @param activity The currently visible Activity — the billing sheet needs it to attach to.
     */
    fun launchPurchaseFlow(activity: Activity) {
        val details = productDetails
        if (details == null) {
            Log.w(TAG, "Cannot launch purchase flow — product details not loaded yet.")
            connectToBillingService()
            return
        }

        val client = billingClient
        if (client == null || !client.isReady) {
            Log.w(TAG, "Billing client not ready — reconnecting.")
            connectToBillingService()
            return
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)

        // Grab the offer token we already computed when the product details came in.
        // If the Ready state has one, use it; otherwise fall back to the first available offer.
        val readyState = _billingState.value as? BillingState.Ready
        val offerToken = readyState?.offerToken
            ?: details.oneTimePurchaseOfferDetailsList
                ?.filter { it.offerId in offers }
                ?.minByOrNull { it.priceAmountMicros }
                ?.offerToken

        if (!offerToken.isNullOrEmpty()) {
            Log.d(TAG, "Attaching offer token to purchase flow.")
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val billingResult = client.launchBillingFlow(activity, billingFlowParams)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "Launch billing flow failed: ${billingResult.debugMessage}")
            _billingState.postValue(BillingState.Error(billingResult.debugMessage))
        }
    }

    override fun onCleared() {
        super.onCleared()
        billingClient?.endConnection()
        billingClient = null
    }

    companion object {
        private const val TAG = "PurchaseViewModel"
    }
}

