package com.eazpire.wear.discovery

import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlin.math.sqrt

/** Distance in front of the user for the world-locked artifact (meters). */
const val ARTIFACT_AR_PLACEMENT_DISTANCE_M = 2.5f

/** Height above the anchor point for the vertical artwork plane (meters). */
const val ARTIFACT_AR_PLACEMENT_HEIGHT_M = 0.9f

/** Screen-normalized aim point — slightly below center where the hand icon sits. */
const val ARTIFACT_AR_HIT_TEST_X = 0.5f
const val ARTIFACT_AR_HIT_TEST_Y = 0.55f

/** Minimum movement (m) before the preview anchor is recreated. */
private const val PREVIEW_POSE_UPDATE_THRESHOLD_M = 0.025f

/**
 * Returns the best plane hit under the screen center, or null when no tracked surface is found.
 */
fun findArtifactPlaneHit(
    frame: Frame,
    screenWidthPx: Float,
    screenHeightPx: Float,
): HitResult? {
    val screenX = screenWidthPx * ARTIFACT_AR_HIT_TEST_X
    val screenY = screenHeightPx * ARTIFACT_AR_HIT_TEST_Y
    return findArtifactPlaneHitAtScreenPoint(frame, screenX, screenY)
}

fun findArtifactPlaneHitAtScreenPoint(
    frame: Frame,
    screenX: Float,
    screenY: Float,
): HitResult? {
    val camera = frame.camera
    if (camera.trackingState != TrackingState.TRACKING) return null

    return frame.hitTest(screenX, screenY)
        .firstOrNull { hit -> hit.isValidPlaneHit() }
}

/**
 * Default placement: plane under screen center, otherwise a fixed pose in front of the camera.
 */
fun createArtifactWorldAnchor(
    session: Session,
    frame: Frame,
    screenWidthPx: Float,
    screenHeightPx: Float,
): Anchor? {
    val planeHit = findArtifactPlaneHit(frame, screenWidthPx, screenHeightPx)
    if (planeHit != null) {
        return planeHit.createAnchor()
    }
    return createArtifactFallbackAnchor(session, frame)
}

/**
 * Special manual placement at the screen point (hand icon / tap target).
 */
fun createArtifactAnchorAtScreenPoint(
    session: Session,
    frame: Frame,
    screenX: Float,
    screenY: Float,
): Anchor? {
    val planeHit = findArtifactPlaneHitAtScreenPoint(frame, screenX, screenY)
    if (planeHit != null) {
        return planeHit.createAnchor()
    }
    return createArtifactFallbackAnchor(session, frame)
}

private fun createArtifactFallbackAnchor(session: Session, frame: Frame): Anchor? {
    val camera = frame.camera
    if (camera.trackingState != TrackingState.TRACKING) return null

    val cameraPose = camera.displayOrientedPose
    val offset = floatArrayOf(0f, 0f, -ARTIFACT_AR_PLACEMENT_DISTANCE_M)
    val worldPose = cameraPose.compose(Pose(offset, floatArrayOf(0f, 0f, 0f, 1f)))
    return session.createAnchor(worldPose)
}

fun HitResult.isValidPlaneHit(): Boolean {
    val trackable = trackable
    if (trackable is Plane) {
        return trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
            trackable.trackingState == TrackingState.TRACKING &&
            trackable.isPoseInPolygon(hitPose)
    }
    if (trackable is Point) {
        return trackable.trackingState == TrackingState.TRACKING
    }
    return false
}

fun shouldUpdatePreviewAnchor(previousPose: Pose?, nextPose: Pose): Boolean {
    if (previousPose == null) return true
    val dx = previousPose.tx() - nextPose.tx()
    val dy = previousPose.ty() - nextPose.ty()
    val dz = previousPose.tz() - nextPose.tz()
    return sqrt(dx * dx + dy * dy + dz * dz) >= PREVIEW_POSE_UPDATE_THRESHOLD_M
}
