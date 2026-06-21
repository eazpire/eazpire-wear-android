package com.eazpire.wear.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Requests JWT session from the Creator phone app.
 * Minimal stub until [com.eazpire.creator] ships a dedicated handoff activity.
 */
class CreatorSessionHandoff(private val activity: ComponentActivity) {
    private var pending: ((CreatorSession?) -> Unit)? = null

    private val launcher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pending
            pending = null
            val parsed = WearPlayerSessionHandoff.parseResult(result)
            callback?.invoke(
                parsed?.let { (jwt, ownerId) -> CreatorSession(jwt, ownerId) },
            )
        }

    suspend fun requestSession(): CreatorSession? {
        val intent = WearPlayerSessionHandoff.createIntent()
        return suspendCancellableCoroutine { cont ->
            pending = { session ->
                if (cont.isActive) cont.resume(session)
            }
            runCatching { launcher.launch(intent) }
                .onFailure {
                    pending = null
                    if (cont.isActive) cont.resume(null)
                }
        }
    }
}

object WearPlayerSessionHandoff {
    const val CREATOR_PACKAGE = "com.eazpire.creator"
    const val ACTION = "com.eazpire.creator.action.WEAR_PLAYER_SESSION"
    const val EXTRA_JWT = "jwt"
    const val EXTRA_OWNER_ID = "owner_id"

    fun createIntent(): Intent =
        Intent(ACTION).setPackage(CREATOR_PACKAGE)

    fun parseResult(result: androidx.activity.result.ActivityResult): Pair<String, String>? {
        if (result.resultCode != Activity.RESULT_OK) return null
        val data = result.data ?: return null
        val jwt = data.getStringExtra(EXTRA_JWT)?.trim().orEmpty()
        val ownerId = data.getStringExtra(EXTRA_OWNER_ID)?.trim().orEmpty()
        if (jwt.isBlank() || ownerId.isBlank()) return null
        return jwt to ownerId
    }
}
