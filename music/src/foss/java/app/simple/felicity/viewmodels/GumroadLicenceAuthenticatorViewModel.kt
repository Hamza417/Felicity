package app.simple.felicity.viewmodels

import android.app.Application
import android.net.TrafficStats
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.preferences.TrialPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONObject

class GumroadLicenceAuthenticatorViewModel(application: Application) : WrappedViewModel(application) {

    private val licenseStatus: MutableLiveData<Boolean> = MutableLiveData()
    private val message: MutableLiveData<String> = MutableLiveData()

    fun getLicenseStatus(): LiveData<Boolean> {
        return licenseStatus
    }

    fun getMessage(): LiveData<String> {
        return message
    }

    /**
     * Authenticates the license key using the Gumroad API
     * @param licence The license key to be verified
     * @return True if the license is valid, false otherwise
     *
     * @see <a href="https://gumroad.com/api#license-verification">Gumroad API Documentation</a>
     * This is the cURL
     * curl https://api.gumroad.com/v2/licenses/verify \
     * -d "product_permalink=your_product_permalink" \
     * -d "license_key=your_license_key" \
     * -u "your_api_token:"
     */
    fun verifyLicence(licence: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                TrafficStats.setThreadStatsTag(0xF00D)
                val httpClient = createHttpClient()
                val request = createRequest(licence)
                val response = executeRequest(httpClient, request)
                handleResponse(response)
                cleanupResources(httpClient, response)
            }.getOrElse {
                handleException(it)
            }
        }
    }

    private fun createHttpClient(): OkHttpClient {
        return OkHttpClient()
    }

    private fun createRequest(licence: String): okhttp3.Request {
        return okhttp3.Request.Builder()
            .url("https://api.gumroad.com/v2/licenses/verify")
            .post(okhttp3.FormBody.Builder()
                      .add("product_id", "4I6yY0Xko2l8um9FEC5f_Q==")
                      .add("license_key", licence)
                      .build())
            .build()
    }

    private fun executeRequest(httpClient: OkHttpClient, request: okhttp3.Request): okhttp3.Response {
        return httpClient.newCall(request).execute()
    }

    private fun handleResponse(response: okhttp3.Response) {
        val responseBodyCopy = response.peekBody(Long.MAX_VALUE)
        val responseBody = responseBodyCopy.string()

        if (response.isSuccessful) {
            processSuccessfulResponse(responseBody)
        } else {
            processErrorResponse(responseBody)
        }
    }

    private fun processSuccessfulResponse(responseBody: String) {
        Log.d("GumroadLicenceAuthenticatorViewModel", responseBody)
        val jsonObject = JSONObject(responseBody)
        val success = jsonObject.getBoolean("success")
        val refunded = jsonObject.getJSONObject("purchase").getBoolean("refunded")
        val disputed = jsonObject.getJSONObject("purchase").getBoolean("disputed")
        val chargebacked = jsonObject.getJSONObject("purchase").getBoolean("chargebacked")
        // Returns date in format: 2021-01-05T19:38:56Z
        val createdAt = jsonObject.getJSONObject("purchase").getString("created_at")
        val variant = jsonObject.getJSONObject("purchase").getString("variants")

        if (success && !refunded && !disputed && !chargebacked) {
            if (createdAt.isNotEmpty()) { // TODO : Set limit after release here
                TrialPreferences.setIsEarlyAccessUser(true)
            }

            if (variant.isNotEmpty() && variant.contains("Support Development")) {
                Log.d("GumroadLicenceAuthenticatorViewModel", "User is a supporter")
                TrialPreferences.setIsSupporter(true)
            }

            updateTrialPreferences(true)

            licenseStatus.postValue(true)
        } else {
            updateTrialPreferences(false)
            licenseStatus.postValue(false)
            postRefundMessage(refunded)
        }
    }

    private fun processErrorResponse(responseBody: String) {
        Log.e("GumroadLicenceAuthenticatorViewModel", responseBody)
        updateTrialPreferences(false)
        licenseStatus.postValue(false)
        val jsonObject = JSONObject(responseBody)
        message.postValue(jsonObject.getString("message"))
    }

    private fun updateTrialPreferences(isValid: Boolean) {
        if (isValid) {
            setPreferencesForValidLicense()
        } else {
            setPreferencesForInvalidLicense()
        }
    }

    private fun setPreferencesForValidLicense() {
        setFullVersion(true)
        setHasLicenceKey(true)
    }

    private fun setPreferencesForInvalidLicense() {
        setFullVersion(false)
        setHasLicenceKey(false)
    }

    private fun setFullVersion(fullVersion: Boolean) {
        TrialPreferences.setFullVersion(fullVersion)
    }

    private fun setHasLicenceKey(hasKey: Boolean) {
        TrialPreferences.setHasLicenceKey(hasKey)
    }

    private fun postRefundMessage(refunded: Boolean) {
        if (refunded) {
            message.postValue("Your purchase has been refunded and the licence key is no longer valid.")
        } else {
            message.postValue("Licence is not valid. Please check the licence key and try again.")
        }
    }

    private fun cleanupResources(httpClient: OkHttpClient, response: okhttp3.Response) {
        Log.d("GumroadLicenceAuthenticatorViewModel", TrafficStats.getThreadStatsTag().toString())
        TrafficStats.clearThreadStatsTag()
        response.close()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    private fun handleException(exception: Throwable) {
        postWarning(exception.message.toString())
        exception.printStackTrace()
    }
}