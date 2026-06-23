package com.eazpire.wear.discovery

import android.content.Context

/** Persists GPS/trail consent before first Explore session. */
object DiscoveryConsentStore {
    private const val PREFS = "discovery_consent_prefs"
    private const val KEY_ACCEPTED = "gps_trail_consent_v1"

    fun hasConsent(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCEPTED, false)

    fun setConsent(context: Context, accepted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCEPTED, accepted)
            .apply()
    }
}
