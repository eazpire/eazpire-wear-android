package com.eazpire.wear.auth

import com.eazpire.wear.core.auth.AuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Google Play review login — bypasses Shopify passwordless OTP. */
class PlayReviewAuthService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun exchangeReviewCredentials(email: String, code: String): ShopifyAuthService.JwtResult =
        withContext(Dispatchers.IO) {
            val url = "${AuthConfig.CREATOR_ENGINE_URL}/apps/creator-dispatch?op=play-review-login"
            val bodyJson = JSONObject()
                .put("email", email.trim().lowercase())
                .put("code", code.trim())
                .toString()
            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: "{}"
            val json = JSONObject(respBody)
            if (!response.isSuccessful || !json.optBoolean("ok", false)) {
                throw AuthException("Review login failed: ${json.optString("error", "unknown")}")
            }
            val jwt = json.optString("jwt")
            val ownerId = json.optString("owner_id")
            if (jwt.isBlank()) throw AuthException("No jwt in response")
            ShopifyAuthService.JwtResult(jwt = jwt, ownerId = ownerId)
        }
}
