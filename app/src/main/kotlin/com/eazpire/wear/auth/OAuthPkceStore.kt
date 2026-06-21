package com.eazpire.wear.auth

import android.content.Context

object OAuthPkceStore {
    private const val PREFS = "wear_oauth_pkce_pending"
    private const val KEY_STATE = "state"
    private const val KEY_VERIFIER = "code_verifier"

    fun save(context: Context, state: String, codeVerifier: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, state)
            .putString(KEY_VERIFIER, codeVerifier)
            .apply()
    }

    fun consume(context: Context, stateFromCallback: String): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_STATE, null) ?: return null
        val verifier = prefs.getString(KEY_VERIFIER, null) ?: return null
        if (stored != stateFromCallback) return null
        prefs.edit().clear().apply()
        return verifier
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
