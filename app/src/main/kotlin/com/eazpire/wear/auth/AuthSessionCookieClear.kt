package com.eazpire.wear.auth

import android.webkit.CookieManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Clears Shopify auth cookies (not Google) before a fresh provider login. */
object AuthSessionCookieClear {

    private val SHOPIFY_AUTH_ORIGINS = listOf(
        "https://shopify.com",
        "https://account.eazpire.com",
        "https://www.eazpire.com",
    )

    suspend fun clearShopifyAuthCookies() = suspendCancellableCoroutine { cont ->
        try {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            for (origin in SHOPIFY_AUTH_ORIGINS) {
                clearCookiesForOrigin(cm, origin)
                clearCookiesForOrigin(cm, "$origin/authentication/")
            }
            cm.flush()
            cont.resume(Unit)
        } catch (_: Exception) {
            cont.resume(Unit)
        }
    }

    private fun clearCookiesForOrigin(cm: CookieManager, origin: String) {
        val cookies = cm.getCookie(origin) ?: return
        for (part in cookies.split(";")) {
            val name = part.trim().substringBefore("=").trim()
            if (name.isEmpty()) continue
            cm.setCookie(
                origin,
                "$name=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
            )
        }
    }
}
