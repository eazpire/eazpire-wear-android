package com.eazpire.wear.sync

import android.content.Context
import android.net.Uri
import com.eazpire.wear.core.auth.SecureTokenStore
import com.eazpire.wear.core.auth.WearPlayerAuthPaths
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

/** Push JWT session to Wear OS player app via Data Layer. */
object WearPlayerAuthSync {
    fun push(context: Context, tokenStore: SecureTokenStore) {
        val appContext = context.applicationContext
        val jwt = tokenStore.getJwt()?.trim().orEmpty()
        val ownerId = tokenStore.getOwnerId()?.trim().orEmpty()
        if (jwt.isBlank() || ownerId.isBlank()) {
            clear(appContext)
            return
        }
        val payload = JSONObject()
            .put("jwt", jwt)
            .put("owner_id", ownerId)
            .put("updated_at", System.currentTimeMillis())
            .toString()
        val request = PutDataMapRequest.create(WearPlayerAuthPaths.DATA_PATH).apply {
            dataMap.putString("payload", payload)
            dataMap.putLong("updated_at", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(appContext).putDataItem(request)
    }

    fun clear(context: Context) {
        val uri = Uri.Builder().scheme("wear").path(WearPlayerAuthPaths.DATA_PATH).build()
        Wearable.getDataClient(context.applicationContext).deleteDataItems(uri)
    }
}
