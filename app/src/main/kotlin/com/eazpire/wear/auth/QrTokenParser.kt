package com.eazpire.wear.auth

import android.net.Uri

object QrTokenParser {
    fun parse(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null
        return runCatching {
            val uri = when {
                s.startsWith("eazpire://") ->
                    Uri.parse(s.replace("eazpire://", "https://eazpire.local/"))
                s.contains("://") -> Uri.parse(s)
                else -> null
            }
            uri?.getQueryParameter("t")
                ?: uri?.getQueryParameter("token")
        }.getOrNull()
            ?: Regex("[?&](?:t|token)=([A-Za-z0-9_-]+)").find(s)?.groupValues?.getOrNull(1)
            ?: s.takeIf { it.length >= 16 && !it.contains(" ") }
    }
}
