package com.eazpire.wear.discovery

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Batches GPS points for periodic upload with idempotent batch IDs.
 */
class DiscoveryTrackBuffer(
    private val flushThreshold: Int = 15,
) {
    private val points = CopyOnWriteArrayList<JSONObject>()

    fun add(lat: Double, lng: Double, ts: Long, accuracyM: Double) {
        points.add(
            JSONObject()
                .put("lat", lat)
                .put("lng", lng)
                .put("ts", ts)
                .put("accuracy_m", accuracyM),
        )
    }

    fun shouldFlush(): Boolean = points.size >= flushThreshold

    fun drainForUpload(): Pair<String, JSONArray>? {
        if (points.isEmpty()) return null
        val snapshot = points.toList()
        points.clear()
        val arr = JSONArray()
        snapshot.forEach { arr.put(it) }
        return UUID.randomUUID().toString() to arr
    }

    fun isEmpty(): Boolean = points.isEmpty()
}
