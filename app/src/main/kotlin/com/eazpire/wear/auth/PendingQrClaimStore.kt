package com.eazpire.wear.auth

import android.content.Context

object PendingQrClaimStore {
    private const val PREFS = "wear_pending_qr_claim"
    private const val KEY_TOKEN = "qr_token"

    fun save(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    fun consume(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)?.trim()?.takeIf { it.isNotBlank() }
        if (token != null) prefs.edit().remove(KEY_TOKEN).apply()
        return token
    }

    fun peek(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TOKEN)
            .apply()
    }
}
