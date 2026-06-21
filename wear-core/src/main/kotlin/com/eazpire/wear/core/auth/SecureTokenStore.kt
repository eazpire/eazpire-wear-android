package com.eazpire.wear.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted JWT + owner_id for Wear Player phone and watch apps. */
class SecureTokenStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = openEncryptedPrefs(appContext)

    private fun openEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            createEncryptedPrefs(context)
        } catch (_: Exception) {
            context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
            createEncryptedPrefs(context)
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getJwt(): String? = prefs.getString(KEY_JWT, null)?.takeIf { it.isNotBlank() }

    fun getOwnerId(): String? = prefs.getString(KEY_OWNER_ID, null)?.takeIf { it.isNotBlank() }

    fun saveJwt(jwt: String, ownerId: String) {
        prefs.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, ownerId)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = !getJwt().isNullOrBlank() && !getOwnerId().isNullOrBlank()

    companion object {
        private const val ENCRYPTED_PREFS_NAME = "eazpire_wear_player_auth_prefs"
        private const val KEY_JWT = "jwt"
        private const val KEY_OWNER_ID = "owner_id"

        @Volatile
        private var instance: SecureTokenStore? = null

        fun get(context: Context): SecureTokenStore {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: SecureTokenStore(app).also { instance = it }
            }
        }
    }
}
