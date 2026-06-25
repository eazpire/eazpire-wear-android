package com.eazpire.wear.discovery

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val PLACEMENT_LOG_TAG = "ArtifactAr"

/** Distance along the camera look ray where the loot is placed (meters). */
const val ARTIFACT_AR_PLACEMENT_DISTANCE_M = 1.75f

/** Screen-normalized aim point — slightly below center where the hand icon sits. */
const val ARTIFACT_AR_HIT_TEST_X = 0.5f
const val ARTIFACT_AR_HIT_TEST_Y = 0.55f

/** Legacy constant — bitmap/GLB sit on the anchor origin with a small lift in AR screen. */
const val ARTIFACT_AR_PLACEMENT_HEIGHT_M = 0f

/** Horizontal spread factor for off-center screen taps at [ARTIFACT_AR_PLACEMENT_DISTANCE_M]. */
private const val CAMERA_AIM_FOV_SPREAD = 1.15f

/**
 * Returns the best placement hit under the screen center (for UI hints only — not used for tap placement).
 */
fun findArtifactPlacementHit(
    frame: Frame,
    screenWidthPx: Float,
    screenHeightPx: Float,
): HitResult? {
    val screenX = screenWidthPx * ARTIFACT_AR_HIT_TEST_X
    val screenY = screenHeightPx * ARTIFACT_AR_HIT_TEST_Y
    return findArtifactPlacementHitAtScreenPoint(frame, screenX, screenY)
}

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
): HitResult? =
    findArtifactPlacementHitAtScreenPoint(frame, screenX, screenY)
        ?.takeIf { it.isValidPlaneHit() }

fun findArtifactPlacementHitAtScreenPoint(
    frame: Frame,
    screenX: Float,
    screenY: Float,
): HitResult? {
    val camera = frame.camera
    if (camera.trackingState != TrackingState.TRACKING) return null

    return frame.hitTest(screenX, screenY)
        .firstOrNull { hit -> hit.isValidPlacementHit() }
}

/**
 * Places the loot exactly on the camera look ray through [screenX]/[screenY] at a fixed distance.
 * Does not snap to floor planes (avoids wrong floor / far-away placement).
 */
fun createArtifactAnchorAtScreenPoint(
    session: Session,
    frame: Frame,
    screenX: Float,
    screenY: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
    distanceM: Float = ARTIFACT_AR_PLACEMENT_DISTANCE_M,
): Anchor? {
    val pose = createArtifactCameraAimPose(
        frame = frame,
        screenX = screenX,
        screenY = screenY,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        distanceM = distanceM,
    ) ?: return null
    val anchor = session.createAnchor(pose)
    logPlacementRelativeToCamera(frame, pose, distanceM)
    return anchor
}

fun createArtifactWorldAnchor(
    session: Session,
    frame: Frame,
    screenWidthPx: Float,
    screenHeightPx: Float,
): Anchor? =
    createArtifactAnchorAtScreenPoint(
        session = session,
        frame = frame,
        screenX = screenWidthPx * ARTIFACT_AR_HIT_TEST_X,
        screenY = screenHeightPx * ARTIFACT_AR_HIT_TEST_Y,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
    )

/**
 * World pose on the view ray through the screen point, oriented to face the camera (billboard).
 */
fun createArtifactCameraAimPose(
    frame: Frame,
    screenX: Float,
    screenY: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
    distanceM: Float,
): Pose? {
    val camera = frame.camera
    if (camera.trackingState != TrackingState.TRACKING) return null
    if (screenWidthPx <= 0f || screenHeightPx <= 0f) return null

    val displayPose = camera.displayOrientedPose
    val fracX = screenX / screenWidthPx - 0.5f
    val fracY = 0.5f - screenY / screenHeightPx

    val localPoint = floatArrayOf(
        fracX * distanceM * CAMERA_AIM_FOV_SPREAD,
        fracY * distanceM * CAMERA_AIM_FOV_SPREAD,
        -distanceM,
    )
    val worldPoint = displayPose.transformPoint(localPoint)
    return poseFacingCamera(worldPoint, displayPose.translation)
}

private fun poseFacingCamera(worldPoint: FloatArray, cameraTranslation: FloatArray): Pose {
    val dx = cameraTranslation[0] - worldPoint[0]
    val dz = cameraTranslation[2] - worldPoint[2]
    val yawRad = atan2(dx.toDouble(), dz.toDouble()).toFloat()
    val halfYaw = yawRad * 0.5f
    val qy = sin(halfYaw)
    val qw = cos(halfYaw)
    return Pose(worldPoint, floatArrayOf(0f, qy, 0f, qw))
}

private fun logPlacementRelativeToCamera(frame: Frame, pose: Pose, targetDistanceM: Float) {
    val cam = frame.camera.pose.translation
    val p = pose.translation
    val dx = p[0] - cam[0]
    val dy = p[1] - cam[1]
    val dz = p[2] - cam[2]
    val dist = sqrt(dx * dx + dy * dy + dz * dz)
    Log.d(
        PLACEMENT_LOG_TAG,
        "camera-aim place world=(${p[0].format()}, ${p[1].format()}, ${p[2].format()}) " +
            "cam=(${cam[0].format()}, ${cam[1].format()}, ${cam[2].format()}) " +
            "delta=(${dx.format()}, ${dy.format()}, ${dz.format()}) dist=${dist.format()}m target=${targetDistanceM}m",
    )
}

private fun Float.format(): String = String.format("%.2f", this)

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

fun HitResult.isValidPlacementHit(): Boolean {
    if (isValidPlaneHit()) return true
    val trackable = trackable
    if (trackable is InstantPlacementPoint) {
        return trackable.trackingState == TrackingState.TRACKING
    }
    return false
}
