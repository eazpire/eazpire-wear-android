package com.eazpire.wear.core.api

import com.eazpire.wear.core.auth.AuthConfig
import com.eazpire.wear.core.model.ArDrawing
import com.eazpire.wear.core.model.ArtifactItem
import com.eazpire.wear.core.model.MapArtifactDefaults
import com.eazpire.wear.core.model.MapArtifactProduct
import com.eazpire.wear.core.model.BalanceSnapshot
import com.eazpire.wear.core.model.FeedPost
import com.eazpire.wear.core.model.MoveToEarnStatus
import com.eazpire.wear.core.model.MoveToEarnWallet
import com.eazpire.wear.core.model.MoveSession
import com.eazpire.wear.core.model.DiscoveryStatus
import com.eazpire.wear.core.model.NetworkMember
import com.eazpire.wear.core.model.VerifyItem
import com.eazpire.wear.core.model.WearEarnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Wear Player API — mirrors wear-web/js/api.js via creator-engine dispatch.
 */
class WearPlayerApi(
    private val jwt: String?,
    private val baseUrl: String = AuthConfig.CREATOR_ENGINE_URL,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun dispatch(
        op: String,
        query: Map<String, String> = emptyMap(),
        method: String = "GET",
        body: JSONObject? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$baseUrl/apps/creator-dispatch?op=")
            append(java.net.URLEncoder.encode(op, "UTF-8"))
            query.forEach { (k, v) ->
                if (v.isNotBlank()) {
                    append("&")
                    append(java.net.URLEncoder.encode(k, "UTF-8"))
                    append("=")
                    append(java.net.URLEncoder.encode(v, "UTF-8"))
                }
            }
        }
        // Include op in POST JSON body — some proxies strip query strings on POST to creator-dispatch.
        val requestBody = when {
            method == "POST" && body != null -> {
                if (!body.has("op")) body.put("op", op)
                body.toString().toRequestBody("application/json".toMediaType())
            }
            method == "POST" ->
                JSONObject().put("op", op).toString()
                    .toRequestBody("application/json".toMediaType())
            else -> null
        }
        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        val response = client.newCall(request).execute()
        val raw = response.body?.string().orEmpty()
        val json = runCatching { JSONObject(if (raw.isBlank()) "{}" else raw) }.getOrElse {
            JSONObject().put("ok", false).put("error", "invalid_json")
        }
        if (!response.isSuccessful && !json.has("error")) {
            json.put("error", "request_failed")
        }
        json.put("_status", response.code)
        json
    }

    // --- Session / account ---

    suspend fun me(ownerId: String): JSONObject =
        getCustomerAccountProfile(ownerId)

    suspend fun referralCode(ownerId: String): JSONObject =
        dispatch("get-referral-code", query = mapOf("owner_id" to ownerId))

    suspend fun network(ownerId: String): JSONObject =
        dispatch("list-community-network", query = mapOf("owner_id" to ownerId))

    suspend fun analyticsOverview(ownerId: String): JSONObject =
        dispatch(
            "get-community-analytics-overview",
            query = mapOf("owner_id" to ownerId, "range" to "30d"),
        )

    suspend fun balance(ownerId: String): JSONObject =
        dispatch("get-balance", query = mapOf("owner_id" to ownerId))

    suspend fun balanceSnapshot(ownerId: String): BalanceSnapshot {
        val json = balance(ownerId)
        val eaz = json.optJSONObject("balance")?.optLong("eaz")
            ?: json.optLong("eaz", 0L)
        return BalanceSnapshot(eazBalance = eaz, rawJson = json.toString())
    }

    suspend fun shopCredits(ownerId: String): JSONObject =
        dispatch("get-shop-credits-summary", query = mapOf("owner_id" to ownerId))

    suspend fun accountUsername(ownerId: String): JSONObject =
        dispatch("get-account-username", query = mapOf("owner_id" to ownerId))

    suspend fun getCustomerAccountProfile(ownerId: String): JSONObject =
        dispatch("get-customer-account-profile", query = mapOf("owner_id" to ownerId))

    suspend fun customerEmail(ownerId: String): JSONObject =
        dispatch("get-customer-email", query = mapOf("customer_id" to ownerId))

    // --- Artifacts / vault ---

    suspend fun artifactsInventory(ownerId: String): JSONObject =
        dispatch("artifacts-inventory-list", query = mapOf("owner_id" to ownerId))

    suspend fun artifactsLoadoutGet(ownerId: String): JSONObject =
        dispatch("artifacts-loadout-get", query = mapOf("owner_id" to ownerId))

    suspend fun artifactsClaimQr(token: String, ownerId: String): JSONObject =
        dispatch(
            "artifacts-claim-qr",
            method = "POST",
            body = JSONObject()
                .put("owner_id", ownerId)
                .put("qr_token", token)
                .put("token", token),
        )

    fun countMintedArtifacts(inventoryJson: JSONObject): Int {
        val slots = inventoryJson.optJSONArray("slots") ?: return 0
        var count = 0
        for (i in 0 until slots.length()) {
            val slot = slots.optJSONObject(i) ?: continue
            val status = slot.optString("generation_status", slot.optString("status", ""))
            if (status.equals("failed", ignoreCase = true)) continue
            count += 1
        }
        return count
    }

    suspend fun artifactsShowcaseRecent(limit: Int = 24): JSONObject =
        dispatch("artifacts-showcase-recent", query = mapOf("limit" to limit.toString()))

    /** First showcase item with artwork, else static demo shop product. */
    suspend fun resolveMapArtifactProduct(limit: Int = 8): MapArtifactProduct {
        runCatching {
            val json = artifactsShowcaseRecent(limit)
            val arr = json.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val imageUrl = o.optString("artwork_url", "").trim()
                if (imageUrl.isBlank()) continue
                val remoteModelUrl = o.optString("model_url", o.optString("glb_url", "")).trim()
                return MapArtifactProduct(
                    id = o.optString("id", o.optString("instance_id", "")),
                    name = o.optString("product_title", o.optString("name", "Artifact")),
                    imageUrl = imageUrl,
                    modelUrl = remoteModelUrl.ifBlank { MapArtifactDefaults.DEMO_GLB_ASSET },
                    slotType = o.optString("slot_type", ""),
                )
            }
        }
        return MapArtifactDefaults.demoFallback()
    }

    /** Primary showcase artifact plus bundled animated shop-card on the map. */
    suspend fun resolveMapArtifactProducts(limit: Int = 8): List<MapArtifactProduct> {
        val primary = resolveMapArtifactProduct(limit)
        val animated = MapArtifactDefaults.shopCardAnimatedFallback()
        return if (primary.id == animated.id) {
            MapArtifactDefaults.demoMapArtifacts()
        } else {
            listOf(primary, animated)
        }
    }

    suspend fun parseArtifacts(json: JSONObject): List<ArtifactItem> {
        val arr = json.optJSONArray("items") ?: json.optJSONArray("artifacts") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            ArtifactItem(
                instanceId = o.optString("instance_id", o.optString("id", "")),
                name = o.optString("name", o.optString("title", "Artifact")),
                rarity = o.optString("rarity", ""),
            )
        }
    }

    suspend fun characterEquipment(characterId: String): JSONObject =
        dispatch("artifacts-character-equipment-get", query = mapOf("character_id" to characterId))

    suspend fun characterSwapPrepare(characterId: String, instanceId: String): JSONObject =
        dispatch(
            "artifacts-character-swap-prepare",
            method = "POST",
            body = JSONObject().put("character_id", characterId).put("instance_id", instanceId),
        )

    suspend fun characterSwapCommit(
        characterId: String,
        instanceId: String,
        clientNonce: String = System.currentTimeMillis().toString(),
    ): JSONObject = dispatch(
        "artifacts-character-swap-commit",
        method = "POST",
        body = JSONObject()
            .put("character_id", characterId)
            .put("instance_id", instanceId)
            .put("client_nonce", clientNonce),
    )

    suspend fun rerenderQuestGet(characterId: String? = null): JSONObject {
        val q = if (!characterId.isNullOrBlank()) mapOf("character_id" to characterId) else emptyMap()
        return dispatch("artifacts-rerender-quest-get", query = q)
    }

    suspend fun rerenderQuestProgress(questId: String, stepsDelta: Long): JSONObject =
        dispatch(
            "artifacts-rerender-quest-progress",
            method = "POST",
            body = JSONObject().put("quest_id", questId).put("steps_delta", stepsDelta),
        )

    // --- Verify ---

    suspend fun verifyCompleted(ownerId: String): JSONObject =
        dispatch("verify-completed-list", query = mapOf("owner_id" to ownerId, "outcome" to "all"))

    suspend fun verifyBootstrap(entityType: String = "ownership"): JSONObject =
        dispatch("verify-bootstrap", query = mapOf("entity_type" to entityType))

    suspend fun verifySubmitVote(body: JSONObject): JSONObject =
        dispatch("verify-submit-vote", method = "POST", body = body)

    suspend fun parseVerifyItems(json: JSONObject): List<VerifyItem> {
        val arr = json.optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            VerifyItem(
                id = o.optString("id", ""),
                title = o.optString("title", o.optString("name", "Verify")),
                outcome = o.optString("outcome", ""),
            )
        }
    }

    // --- Wear earn / move to earn ---

    suspend fun wearEarnStatus(): JSONObject = dispatch("community-wear-earn-status")

    suspend fun wearEarnStatusModel(): WearEarnStatus {
        val json = wearEarnStatus()
        val actions = json.optJSONArray("actions")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
        } ?: emptyList()
        return WearEarnStatus(
            actions = actions,
            claimedToday = json.optInt("claimed_today", 0),
        )
    }

    suspend fun wearEarnClaim(actionKey: String, postId: String? = null): JSONObject {
        val body = JSONObject().put("action_key", actionKey)
        if (!postId.isNullOrBlank()) body.put("post_id", postId)
        return dispatch("community-wear-earn-claim", method = "POST", body = body)
    }

    suspend fun moveToEarnStatus(): JSONObject = dispatch("move-to-earn-status")

    suspend fun moveToEarnStatusModel(): MoveToEarnStatus {
        val json = moveToEarnStatus()
        val walletJson = json.optJSONObject("move_to_earn_wallet")
        val wallet = walletJson?.let {
            MoveToEarnWallet(
                balanceEazcAvailable = it.optDouble("balance_eazc_available", 0.0),
                balanceEazcLocked = it.optDouble("balance_eazc_locked", 0.0),
                minConvertEaz = it.optDouble("min_convert_eaz", 1.0),
            )
        }
        return MoveToEarnStatus(
            stepsToday = json.optLong("steps_today", json.optLong("steps", 0L)),
            eazEarnedToday = json.optLong("eaz_earned_today", json.optLong("eaz_today", 0L)),
            dailyClaimAvailable = json.optBoolean("daily_claim_available", false),
            wallet = wallet,
        )
    }

    suspend fun moveToEarnWallet(): JSONObject = dispatch("move-to-earn-wallet")

    suspend fun moveToEarnWalletModel(): MoveToEarnWallet {
        val json = moveToEarnWallet()
        val w = json.optJSONObject("wallet") ?: JSONObject()
        return MoveToEarnWallet(
            balanceEazcAvailable = w.optDouble("balance_eazc_available", 0.0),
            balanceEazcLocked = w.optDouble("balance_eazc_locked", 0.0),
            minConvertEaz = w.optDouble("min_convert_eaz", 1.0),
        )
    }

    suspend fun moveToEarnConvertToShopCredit(
        amountEaz: Double,
        channel: String = "wear_android",
    ): JSONObject =
        dispatch(
            "move-to-earn-convert-to-shop-credit",
            method = "POST",
            body = JSONObject()
                .put("amount_eaz", amountEaz)
                .put("channel", channel),
        )

    suspend fun moveToEarnSyncSteps(stepsDelta: Long): JSONObject =
        dispatch(
            "move-to-earn-sync-steps",
            method = "POST",
            body = JSONObject().put("steps_delta", stepsDelta),
        )

    // --- World Discovery ---

    suspend fun discoveryConfig(): JSONObject = dispatch("discovery-config")

    suspend fun discoveryStatus(): JSONObject = dispatch("discovery-status")

    suspend fun discoveryStatusModel(): DiscoveryStatus {
        val json = discoveryStatus()
        val sessionJson = json.optJSONObject("session")
        val session = sessionJson?.let {
            MoveSession(
                id = it.optString("id", ""),
                characterId = it.optLong("character_id", 0L),
                energyMinutes = it.optInt("energy_minutes", 0),
                startedAt = it.optLong("started_at", 0L),
                endsAt = it.optLong("ends_at", 0L),
                remainingMs = it.optLong("remaining_ms", 0L),
                status = it.optString("status", ""),
            )
        }
        val home = json.optJSONObject("home")
        return DiscoveryStatus(
            unlocked = json.optBoolean("unlocked", false),
            totalCellsDiscovered = json.optLong("total_cells_discovered", 0L),
            session = session,
            homeCityId = home?.optString("city_id", "") ?: "",
        )
    }

    suspend fun moveSessionStart(): JSONObject =
        dispatch("move-session-start", method = "POST", body = JSONObject())

    suspend fun moveSessionEnd(): JSONObject =
        dispatch("move-session-end", method = "POST", body = JSONObject())

    suspend fun discoverySyncTrack(batchId: String, points: JSONArray): JSONObject {
        val body = JSONObject()
            .put("batch_id", batchId)
            .put("points", points)
            .put(
                "client",
                JSONObject()
                    .put("platform", "wear-android")
                    .put("session_id", batchId),
            )
        return dispatch("discovery-sync-track", method = "POST", body = body)
    }

    suspend fun discoveryLootNearby(): JSONObject = dispatch("discovery-loot-nearby")

    suspend fun discoveryLootClaim(lootId: String): JSONObject =
        dispatch(
            "discovery-loot-claim",
            method = "POST",
            body = JSONObject().put("loot_id", lootId),
        )

    suspend fun discoveryRankingsGlobal(limit: Int = 50): JSONObject =
        dispatch("discovery-rankings-global", query = mapOf("limit" to limit.toString()))

    // --- AR canvas drawings ---

    suspend fun arDrawingsList(
        minLat: Double? = null,
        maxLat: Double? = null,
        minLng: Double? = null,
        maxLng: Double? = null,
    ): JSONObject {
        val q = mutableMapOf<String, String>()
        if (minLat != null) q["min_lat"] = minLat.toString()
        if (maxLat != null) q["max_lat"] = maxLat.toString()
        if (minLng != null) q["min_lng"] = minLng.toString()
        if (maxLng != null) q["max_lng"] = maxLng.toString()
        return dispatch("ar-drawings-list", query = q)
    }

    suspend fun arDrawingsSave(body: JSONObject): JSONObject =
        dispatch("ar-drawings-save", method = "POST", body = body)

    suspend fun arDrawingsDelete(id: String): JSONObject =
        dispatch("ar-drawings-delete", method = "POST", body = JSONObject().put("id", id))

    fun parseArDrawings(json: JSONObject): List<ArDrawing> {
        val arr = json.optJSONArray("drawings") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val pose = o.optJSONObject("pose") ?: JSONObject()
            ArDrawing(
                id = o.optString("id", ""),
                cloudAnchorId = o.optString("cloud_anchor_id", "").takeIf { it.isNotBlank() },
                poseTx = pose.optDouble("tx", 0.0).toFloat(),
                poseTy = pose.optDouble("ty", 0.0).toFloat(),
                poseTz = pose.optDouble("tz", 0.0).toFloat(),
                poseQx = pose.optDouble("qx", 0.0).toFloat(),
                poseQy = pose.optDouble("qy", 0.0).toFloat(),
                poseQz = pose.optDouble("qz", 0.0).toFloat(),
                poseQw = pose.optDouble("qw", 1.0).toFloat(),
                lat = o.optDouble("lat", 0.0),
                lng = o.optDouble("lng", 0.0),
                widthM = o.optDouble("width_m", 0.5).toFloat(),
                imageR2Key = o.optString("image_r2_key", ""),
                imageUrl = o.optString("image_url", "").takeIf { it.isNotBlank() },
                title = o.optString("title", "").takeIf { it.isNotBlank() },
                createdAt = o.optLong("created_at", 0L),
            )
        }
    }

    // --- Feed ---

    suspend fun feedList(limit: Int = 20, cursor: String? = null): JSONObject {
        val q = mutableMapOf("limit" to limit.toString())
        if (!cursor.isNullOrBlank()) q["cursor"] = cursor
        return dispatch("community-feed-list", query = q)
    }

    suspend fun parseFeedPosts(json: JSONObject): List<FeedPost> {
        val arr = json.optJSONArray("posts") ?: json.optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            FeedPost(
                id = o.optString("id", o.optString("post_id", "")),
                authorName = o.optString("author_name", o.optString("author", "Player")),
                bodyText = o.optString("body_text", o.optString("body", "")),
                likeCount = o.optInt("like_count", 0),
            )
        }
    }

    suspend fun feedCreateMultipart(parts: List<Pair<String, String>>): JSONObject =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/apps/creator-dispatch?op=community-feed-create"
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            parts.forEach { (k, v) -> builder.addFormDataPart(k, v) }
            val request = Request.Builder()
                .url(url)
                .post(builder.build())
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            val raw = response.body?.string().orEmpty()
            runCatching { JSONObject(if (raw.isBlank()) "{}" else raw) }.getOrElse {
                JSONObject().put("ok", false).put("error", "request_failed")
            }
        }

    suspend fun feedLike(postId: String): JSONObject =
        dispatch("community-feed-like", method = "POST", body = JSONObject().put("post_id", postId))

    suspend fun feedComments(postId: String): JSONObject =
        dispatch("community-feed-comments", query = mapOf("post_id" to postId))

    suspend fun feedComment(postId: String, text: String): JSONObject =
        dispatch(
            "community-feed-comment",
            method = "POST",
            body = JSONObject().put("post_id", postId).put("body_text", text),
        )

    suspend fun getPublishedByDesign(designId: String, ownerId: String = ""): JSONObject =
        dispatch("get-published", query = mapOf("design_id" to designId, "owner_id" to ownerId))

    suspend fun parseNetwork(json: JSONObject): List<NetworkMember> {
        val arr = json.optJSONArray("members") ?: json.optJSONArray("network") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            NetworkMember(
                ownerId = o.optString("owner_id", ""),
                displayName = o.optString("display_name", o.optString("name", "Member")),
                level = o.optInt("level", 0),
            )
        }
    }
}
