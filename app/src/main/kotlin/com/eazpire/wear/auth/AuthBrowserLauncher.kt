package com.eazpire.wear.auth

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import androidx.browser.customtabs.CustomTabsIntent
import com.eazpire.wear.core.auth.AuthConfig

object AuthBrowserLauncher {
    private const val EXTRA_ENABLE_EPHEMERAL_BROWSING =
        "androidx.browser.customtabs.extra.ENABLE_EPHEMERAL_BROWSING"

    fun launchOAuth(context: Context, url: String) {
        val tabsIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
        tabsIntent.intent.putExtra(EXTRA_ENABLE_EPHEMERAL_BROWSING, true)
        tabsIntent.intent.putExtra(
            Browser.EXTRA_HEADERS,
            Bundle().apply { putString("Accept", AuthConfig.SHOPIFY_HTML_ACCEPT) },
        )
        tabsIntent.launchUrl(context, Uri.parse(url))
    }
}
