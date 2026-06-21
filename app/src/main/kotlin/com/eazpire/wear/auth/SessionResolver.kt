package com.eazpire.wear.auth

import android.content.Context
import com.eazpire.wear.core.auth.SecureTokenStore

enum class SessionProbeResult {
    LoggedIn,
    HasExternalSession,
    NoSession,
}

object SessionResolver {
    private const val CREATOR_PACKAGE = "com.eazpire.creator"

    fun probeLocal(tokenStore: SecureTokenStore): SessionProbeResult {
        if (tokenStore.isLoggedIn()) return SessionProbeResult.LoggedIn
        return SessionProbeResult.NoSession
    }

    suspend fun probeWithCreator(
        context: Context,
        tokenStore: SecureTokenStore,
        handoff: CreatorSessionHandoff,
    ): SessionProbeResult {
        val local = probeLocal(tokenStore)
        if (local == SessionProbeResult.LoggedIn) return local
        if (isCreatorInstalled(context)) return SessionProbeResult.HasExternalSession
        return SessionProbeResult.NoSession
    }

    fun isCreatorInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(CREATOR_PACKAGE, 0)
        true
    }.getOrDefault(false)

    suspend fun tryCreatorHandoff(
        handoff: CreatorSessionHandoff,
        tokenStore: SecureTokenStore,
    ): Boolean {
        val session = handoff.requestSession() ?: return false
        if (session.jwt.isBlank() || session.ownerId.isBlank()) return false
        tokenStore.saveJwt(session.jwt, session.ownerId)
        return true
    }
}

data class CreatorSession(
    val jwt: String,
    val ownerId: String,
)
