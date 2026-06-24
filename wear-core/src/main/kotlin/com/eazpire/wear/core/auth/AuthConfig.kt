package com.eazpire.wear.core.auth

import android.net.Uri

/**
 * Shopify Customer Account OAuth for Eazpire Wear Player (phone app).
 * Register mobile client callback: shop.73952035098.eazpire://callback (shared with Creator app)
 */
object AuthConfig {
    const val SHOP_DOMAIN = "allyoucanpink.myshopify.com"
    const val SHOP_ID = "73952035098"
    const val OIDC_DISCOVERY_URL =
        "https://shopify.com/authentication/$SHOP_ID/.well-known/openid-configuration"
    const val REDIRECT_URI = "shop.73952035098.eazpire://callback"
    const val SHOPIFY_HTML_ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
    const val SCOPE = "openid email customer-account-api:full"

    /** Replace with Customer Account API mobile client ID from Shopify Admin. */
    const val CLIENT_ID = "82087087-a2cc-40a8-91ff-70e29ce275dd"

    const val CREATOR_ENGINE_URL = "https://creator-engine.eazpire.workers.dev"

    fun normalizeOAuthEndpoint(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return trimmed
        val prefix = "https://account.eazpire.com/authentication/"
        if (!trimmed.startsWith(prefix, ignoreCase = true)) return trimmed
        val rest = trimmed.substring(prefix.length).trimStart('/')
        return "https://shopify.com/authentication/$SHOP_ID/$rest"
    }

    fun rewriteAccountHostUri(uri: Uri): Uri? {
        if (uri.host?.equals("account.eazpire.com", ignoreCase = true) != true) return null
        val path = uri.path?.trimStart('/') ?: return null
        val rest = path.removePrefix("authentication/").trimStart('/')
        return uri.buildUpon()
            .scheme("https")
            .authority("shopify.com")
            .path("/authentication/$SHOP_ID/$rest")
            .build()
    }
}
