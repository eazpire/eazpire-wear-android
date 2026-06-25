package com.eazpire.wear.discovery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.eazpire.wear.core.model.ArDrawing
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Session

/** Canvas workflow: draw on screen, then place in AR world, then save. */
internal enum class CanvasWorkflow {
    None,
    Drawing,
    Placing,
}

/** Nearby saved drawing resolved (or resolving) in the AR session. */
internal data class ResolvedArDrawing(
    val drawing: ArDrawing,
    val anchor: Anchor?,
    val bitmap: Bitmap?,
    val resolvingCloud: Boolean = false,
    val resolveFailed: Boolean = false,
    val cloudResolveAnchor: Anchor? = null,
)

internal fun decodePngBytes(bytes: ByteArray): Bitmap? =
    runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { decoded ->
            if (decoded.config == Bitmap.Config.ARGB_8888 && !decoded.isRecycled) decoded
            else decoded.copy(Bitmap.Config.ARGB_8888, false).also {
                if (decoded !== it) decoded.recycle()
            }
        }
    }.getOrNull()

internal fun encodeBitmapToPng(bitmap: Bitmap): ByteArray {
    val stream = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}

internal fun resolveDrawingAnchor(
    session: Session,
    resolved: ResolvedArDrawing,
): ResolvedArDrawing {
    val cloudAnchor = resolved.cloudResolveAnchor
    if (cloudAnchor != null && !ArCloudAnchorHelper.isResolveTerminal(cloudAnchor)) {
        val polled = ArCloudAnchorHelper.pollResolveState(cloudAnchor)
        if (polled != null) {
            cloudAnchor.detach()
            return resolved.copy(
                anchor = polled,
                cloudResolveAnchor = null,
                resolvingCloud = false,
            )
        }
        if (ArCloudAnchorHelper.isResolveTerminal(cloudAnchor)) {
            cloudAnchor.detach()
            return resolved.copy(
                cloudResolveAnchor = null,
                resolvingCloud = false,
                resolveFailed = true,
            )
        }
        return resolved
    }

    if (resolved.anchor != null || resolved.resolvingCloud) return resolved

    val cloudId = resolved.drawing.cloudAnchorId
    if (!cloudId.isNullOrBlank()) {
        val cloud = ArCloudAnchorHelper.beginResolve(session, cloudId)
        return if (cloud != null) {
            resolved.copy(cloudResolveAnchor = cloud, resolvingCloud = true)
        } else {
            resolved.copy(resolveFailed = true)
        }
    }

    val pose = ArPoseSnapshot(
        resolved.drawing.poseTx,
        resolved.drawing.poseTy,
        resolved.drawing.poseTz,
        resolved.drawing.poseQx,
        resolved.drawing.poseQy,
        resolved.drawing.poseQz,
        resolved.drawing.poseQw,
    )
    return resolved.copy(
        anchor = session.createAnchor(ArPoseSnapshot.toPose(pose)),
        resolveFailed = false,
    )
}

internal fun projectDrawingScreenPoint(
    frame: Frame,
    anchor: Anchor,
    screenWidthPx: Float,
    screenHeightPx: Float,
): Pair<Float, Float>? {
    val t = anchor.pose.translation
    return projectWorldToScreen(frame, t[0], t[1], t[2], screenWidthPx, screenHeightPx)
}
