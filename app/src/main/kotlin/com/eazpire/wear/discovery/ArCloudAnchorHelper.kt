package com.eazpire.wear.discovery

import android.util.Base64
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.Session
import org.json.JSONObject

private const val LOG_TAG = "ArCloudAnchor"

/** Pose components for server persistence (local fallback when cloud host fails). */
data class ArPoseSnapshot(
    val tx: Float,
    val ty: Float,
    val tz: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("tx", tx.toDouble())
        .put("ty", ty.toDouble())
        .put("tz", tz.toDouble())
        .put("qx", qx.toDouble())
        .put("qy", qy.toDouble())
        .put("qz", qz.toDouble())
        .put("qw", qw.toDouble())

    companion object {
        fun fromPose(pose: Pose): ArPoseSnapshot {
            val t = pose.translation
            val q = pose.rotationQuaternion
            return ArPoseSnapshot(t[0], t[1], t[2], q[0], q[1], q[2], q[3])
        }

        fun toPose(snapshot: ArPoseSnapshot): Pose =
            Pose(
                floatArrayOf(snapshot.tx, snapshot.ty, snapshot.tz),
                floatArrayOf(snapshot.qx, snapshot.qy, snapshot.qz, snapshot.qw),
            )
    }
}

/** ARCore cloud anchor helpers (direct Session API). */
object ArCloudAnchorHelper {
    fun enableInConfig(config: Config) {
        config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
    }

    fun poseFromAnchor(anchor: Anchor): ArPoseSnapshot = ArPoseSnapshot.fromPose(anchor.pose)

    /** Begin hosting [anchor]. Poll [pollHostingState] each frame until terminal state. */
    fun beginHost(session: Session, anchor: Anchor): Anchor? =
        runCatching {
            session.hostCloudAnchor(anchor)
        }.onFailure { error ->
            Log.w(LOG_TAG, "hostCloudAnchor failed", error)
        }.getOrNull()

    fun pollHostingState(hostingAnchor: Anchor?): String? {
        val anchor = hostingAnchor ?: return null
        return when (anchor.cloudAnchorState) {
            Anchor.CloudAnchorState.SUCCESS -> anchor.cloudAnchorId
            Anchor.CloudAnchorState.ERROR_INTERNAL,
            Anchor.CloudAnchorState.ERROR_NOT_AUTHORIZED,
            Anchor.CloudAnchorState.ERROR_SERVICE_UNAVAILABLE,
            Anchor.CloudAnchorState.ERROR_RESOURCE_EXHAUSTED,
            Anchor.CloudAnchorState.ERROR_HOSTING_DATASET_PROCESSING_FAILED,
            Anchor.CloudAnchorState.ERROR_CLOUD_ID_NOT_FOUND,
            Anchor.CloudAnchorState.ERROR_RESOLVING_LOCALIZATION_NO_MATCH,
            -> {
                Log.w(LOG_TAG, "cloud host terminal state=${anchor.cloudAnchorState}")
                null
            }
            else -> null
        }
    }

    fun isHostingTerminal(hostingAnchor: Anchor?): Boolean {
        val anchor = hostingAnchor ?: return true
        return when (anchor.cloudAnchorState) {
            Anchor.CloudAnchorState.NONE,
            Anchor.CloudAnchorState.TASK_IN_PROGRESS,
            -> false
            else -> true
        }
    }

    fun beginResolve(session: Session, cloudAnchorId: String): Anchor? =
        runCatching {
            session.resolveCloudAnchor(cloudAnchorId)
        }.onFailure { error ->
            Log.w(LOG_TAG, "resolveCloudAnchor failed id=$cloudAnchorId", error)
        }.getOrNull()

    fun pollResolveState(resolvingAnchor: Anchor?): Anchor? {
        val anchor = resolvingAnchor ?: return null
        return when (anchor.cloudAnchorState) {
            Anchor.CloudAnchorState.SUCCESS -> anchor
            Anchor.CloudAnchorState.ERROR_INTERNAL,
            Anchor.CloudAnchorState.ERROR_NOT_AUTHORIZED,
            Anchor.CloudAnchorState.ERROR_SERVICE_UNAVAILABLE,
            Anchor.CloudAnchorState.ERROR_RESOURCE_EXHAUSTED,
            Anchor.CloudAnchorState.ERROR_CLOUD_ID_NOT_FOUND,
            Anchor.CloudAnchorState.ERROR_RESOLVING_LOCALIZATION_NO_MATCH,
            -> {
                Log.w(LOG_TAG, "cloud resolve terminal state=${anchor.cloudAnchorState}")
                null
            }
            else -> null
        }
    }

    fun isResolveTerminal(resolvingAnchor: Anchor?): Boolean {
        val anchor = resolvingAnchor ?: return true
        return when (anchor.cloudAnchorState) {
            Anchor.CloudAnchorState.NONE,
            Anchor.CloudAnchorState.TASK_IN_PROGRESS,
            -> false
            else -> true
        }
    }

    fun bitmapToBase64Png(bytes: ByteArray): String =
        "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}
