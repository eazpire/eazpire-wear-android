package com.eazpire.wear.discovery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eazpire.wear.R
import com.eazpire.wear.core.model.MapArtifactDefaults
import com.google.ar.core.Frame
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

/** Bundled GLB for the floating Eazy companion in AR. */
const val EAZY_MASCOT_GLB_ASSET = MapArtifactDefaults.EAZY_MASCOT_GLB_ASSET

/** Uniform scale target in meters (view-space companion size). */
const val EAZY_MASCOT_SCALE_UNITS = 0.12f

private const val BOB_PERIOD_SEC = 2.5f
private const val BOB_AMPLITUDE_M = 0.006f
private const val MAX_LEAN_DEG = 22f
private const val GPS_MOVE_THRESHOLD_M = 1.2
private const val ARCORE_MOVE_THRESHOLD_M = 0.012f
private const val ARCORE_STOP_THRESHOLD_M = 0.004f

/** Camera-local offset: top-right HUD companion (meters). */
private const val MASCOT_CAMERA_OFFSET_X = 0.22f
private const val MASCOT_CAMERA_OFFSET_Y = 0.12f
private const val MASCOT_CAMERA_OFFSET_Z = -0.42f

/** Screen-relative movement direction for the floating Eazy mascot. */
data class EazyMascotMovementState(
    val relativeBearingDegrees: Float = 0f,
    val isMoving: Boolean = false,
)

/** World-space pose for the 3D mascot rig (updated each AR frame). */
data class EazyMascot3DTransform(
    val position: Position = Position(0f, 0f, -1f),
    val rotation: Rotation = Rotation(),
    val scaleX: Float = 1f,
)

/**
 * Tracks horizontal camera translation between AR frames (responsive while exploring indoors).
 */
class ArtifactArCameraMovementTracker {
    private var prevTranslation: FloatArray? = null
    var state: EazyMascotMovementState = EazyMascotMovementState()
        private set

    fun updateFromFrame(frame: Frame) {
        val pose = frame.camera.pose
        val t = pose.translation
        val prev = prevTranslation
        prevTranslation = floatArrayOf(t[0], t[1], t[2])
        if (prev == null) return

        val dx = t[0] - prev[0]
        val dz = t[2] - prev[2]
        val magnitude = hypot(dx.toDouble(), dz.toDouble()).toFloat()

        if (magnitude < ARCORE_STOP_THRESHOLD_M) {
            state = EazyMascotMovementState(
                relativeBearingDegrees = state.relativeBearingDegrees * 0.82f,
                isMoving = false,
            )
            return
        }
        if (magnitude < ARCORE_MOVE_THRESHOLD_M) {
            state = state.copy(isMoving = false)
            return
        }

        val zAxis = pose.getZAxis()
        val cameraYawDeg = Math.toDegrees(
            atan2((-zAxis[0]).toDouble(), (-zAxis[2]).toDouble()),
        ).toFloat()
        val moveYawDeg = Math.toDegrees(atan2(dx.toDouble(), (-dz).toDouble())).toFloat()
        val relative = normalizeAngleDegrees(moveYawDeg - cameraYawDeg)

        state = EazyMascotMovementState(
            relativeBearingDegrees = relative,
            isMoving = true,
        )
    }

    fun reset() {
        prevTranslation = null
        state = EazyMascotMovementState()
    }
}

fun resolveEazyMascotMovement(
    camera: EazyMascotMovementState,
    gps: EazyMascotMovementState?,
): EazyMascotMovementState =
    when {
        camera.isMoving -> camera
        gps?.isMoving == true -> gps
        else -> EazyMascotMovementState(isMoving = false)
    }

@Composable
fun rememberEazyMascotGpsMovement(userLocation: GeoPoint?): EazyMascotMovementState? {
    val liveLocation = rememberArLiveLocation(userLocation)
    val deviceAzimuth = rememberDeviceAzimuth()
    var prevLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var gpsState by remember { mutableStateOf<EazyMascotMovementState?>(null) }

    LaunchedEffect(liveLocation, deviceAzimuth) {
        val current = liveLocation
        val previous = prevLocation
        if (current != null) {
            if (previous != null && deviceAzimuth != null) {
                val distance = distanceMeters(previous, current)
                gpsState = if (distance >= GPS_MOVE_THRESHOLD_M) {
                    val moveBearing = bearingDegrees(previous, current)
                    EazyMascotMovementState(
                        relativeBearingDegrees = normalizeAngleDegrees(moveBearing - deviceAzimuth),
                        isMoving = true,
                    )
                } else {
                    EazyMascotMovementState(isMoving = false)
                }
            }
            prevLocation = current
        }
    }

    return gpsState
}

fun computeEazyMascotBobPhaseDelta(frameTimestampNanos: Long, lastTimestampNanos: Long): Float {
    if (lastTimestampNanos <= 0L) return 0f
    val dtSec = (frameTimestampNanos - lastTimestampNanos) / 1_000_000_000f
    return dtSec * (2f * PI.toFloat() / BOB_PERIOD_SEC)
}

/**
 * Positions the mascot in world space from the current camera pose so it stays fixed in the
 * top-right of the view (camera-space offset via [Frame.camera] pose).
 */
fun computeEazyMascot3DTransform(
    frame: Frame,
    movement: EazyMascotMovementState,
    bobPhase: Float,
): EazyMascot3DTransform {
    val pose = frame.camera.pose
    val bobM = sin(bobPhase.toDouble()).toFloat() * BOB_AMPLITUDE_M
    val cameraLocal = floatArrayOf(
        MASCOT_CAMERA_OFFSET_X,
        MASCOT_CAMERA_OFFSET_Y + bobM,
        MASCOT_CAMERA_OFFSET_Z,
    )
    val world = pose.transformPoint(cameraLocal)

    val (faceLeft, lean) = if (movement.isMoving) {
        movementToVisuals(movement.relativeBearingDegrees)
    } else {
        false to 0f
    }

    val zAxis = pose.getZAxis()
    val cameraYawDeg = Math.toDegrees(
        atan2((-zAxis[0]).toDouble(), (-zAxis[2]).toDouble()),
    ).toFloat()

    return EazyMascot3DTransform(
        position = Position(world[0], world[1], world[2]),
        rotation = Rotation(y = cameraYawDeg + 180f, z = lean),
        scaleX = if (faceLeft) -1f else 1f,
    )
}

private fun movementToVisuals(relativeBearing: Float): Pair<Boolean, Float> {
    val lean = (relativeBearing / 90f * MAX_LEAN_DEG).coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
    val faceLeft = relativeBearing < -25f || relativeBearing > 155f
    return faceLeft to lean
}

/**
 * Floating Eazy companion in the top-right — pure Compose overlay (no Filament / no extra GLB load).
 */
@Composable
fun ArtifactArEazyMascotComposeOverlay(
    cameraMovement: EazyMascotMovementState,
    gpsMovement: EazyMascotMovementState?,
    showModeMenu: Boolean = false,
    selectedMode: ArtifactArFeatureMode = ArtifactArFeatureMode.Hand,
    onModeSelected: (ArtifactArFeatureMode) -> Unit = {},
    onMascotTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val movement = resolveEazyMascotMovement(cameraMovement, gpsMovement)
    val infiniteTransition = rememberInfiniteTransition(label = "eazy-mascot-bob")
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eazy-mascot-bob-offset",
    )
    val leanAnim = remember { Animatable(0f) }
    val scaleXAnim = remember { Animatable(1f) }

    LaunchedEffect(movement.isMoving, movement.relativeBearingDegrees) {
        val (faceLeft, lean) = if (movement.isMoving) {
            movementToVisuals(movement.relativeBearingDegrees)
        } else {
            false to 0f
        }
        leanAnim.animateTo(lean, spring())
        scaleXAnim.animateTo(if (faceLeft) -1f else 1f, spring())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(12f),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_eazy_mascot),
                contentDescription = null,
                modifier = Modifier
                    .offset(y = bobOffset.dp)
                    .size(56.dp)
                    .graphicsLayer {
                        rotationZ = leanAnim.value
                        scaleX = scaleXAnim.value
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMascotTap,
                    ),
            )
            if (showModeMenu) {
                ArtifactArFeatureModeMenu(
                    selectedMode = selectedMode,
                    onModeSelected = onModeSelected,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
