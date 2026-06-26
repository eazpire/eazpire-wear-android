package com.eazpire.wear.discovery

import com.eazpire.wear.core.model.ArDrawing
import com.google.ar.core.Anchor
import com.google.ar.core.Session

/**
 * Resolve order for persisted AR content (matches Persistent AR architecture):
 * 1. Cloud Anchor (shared / indoor, cross-session)
 * 2. Geospatial / VPS anchor (outdoor, GPS+VPS)
 * 3. Local pose fallback (session-relative — least accurate on revisit)
 */
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

    val drawing = resolved.drawing
    val alt = drawing.altitude
    if (drawing.anchorType == "geospatial" && alt != null) {
        val geoAnchor = ArGeospatialManager.createGeospatialAnchor(
            session = session,
            latitude = drawing.lat,
            longitude = drawing.lng,
            altitude = alt,
            headingDegrees = drawing.headingDegrees ?: 0.0,
        )
        if (geoAnchor != null) {
            return resolved.copy(anchor = geoAnchor, resolveFailed = false)
        }
    }

    val storedGeo = ArGeospatialSnapshot.fromJson(drawing.geospatialPoseJson)
    if (storedGeo != null) {
        val geoAnchor = ArGeospatialManager.createGeospatialAnchor(session, storedGeo)
        if (geoAnchor != null) {
            return resolved.copy(anchor = geoAnchor, resolveFailed = false)
        }
    }

    val pose = ArPoseSnapshot(
        drawing.poseTx,
        drawing.poseTy,
        drawing.poseTz,
        drawing.poseQx,
        drawing.poseQy,
        drawing.poseQz,
        drawing.poseQw,
    )
    return resolved.copy(
        anchor = session.createAnchor(ArPoseSnapshot.toPose(pose)),
        resolveFailed = false,
    )
}

internal fun placementQualityToApiValue(quality: ArPlacementQuality): String =
    when (quality) {
        ArPlacementQuality.Poor -> "poor"
        ArPlacementQuality.Medium -> "medium"
        ArPlacementQuality.Good -> "good"
        ArPlacementQuality.Excellent -> "excellent"
    }
