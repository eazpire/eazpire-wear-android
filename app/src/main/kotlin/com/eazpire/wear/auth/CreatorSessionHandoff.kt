package com.eazpire.wear.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts

/** Requests JWT session from the Creator app via silent activity handoff. */
object CreatorSessionHandoff {
    const val CREATOR_PACKAGE = "com.eazpire.creator"
    const val ACTION = "com.eazpire.creator.action.WEAR_PLAYER_SESSION"
    const val EXTRA_JWT = "jwt"
    const val EXTRA_OWNER_ID = "owner_id"

    val contract = ActivityResultContracts.StartActivityForResult()

    fun createIntent(): Intent =
        Intent(ACTION).setPackage(CREATOR_PACKAGE)

    fun parseResult(result: ActivityResult): Pair<String, String>? {
        if (result.resultCode != Activity.RESULT_OK) return null
        val data = result.data ?: return null
        val jwt = data.getStringExtra(EXTRA_JWT)?.trim().orEmpty()
        val ownerId = data.getStringExtra(EXTRA_OWNER_ID)?.trim().orEmpty()
        if (jwt.isBlank() || ownerId.isBlank()) return null
        return jwt to ownerId
    }
}
