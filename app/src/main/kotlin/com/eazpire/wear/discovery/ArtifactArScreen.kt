package com.eazpire.wear.discovery

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.eazpire.wear.R
import com.eazpire.wear.core.model.MapArtifactDefaults
import com.eazpire.wear.core.model.MapArtifactProduct
import com.eazpire.wear.theme.EazWearColors
import com.google.android.filament.LightManager
import com.google.android.filament.utils.KTX1Loader
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Session
import io.github.sceneview.SurfaceType
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberAREnvironment
import io.github.sceneview.createEnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.utils.readBuffer
import io.github.sceneview.node.Node as SceneNode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * SceneView resumes ARCore when its lifecycle observer is added. If the activity is already
 * RESUMED, that happens before the AR [SurfaceView] exists — [Session.resume] then crashes.
 * This owner keeps ARCore paused until [resumeAr] is called after the surface is mounted.
 */
private class DeferredArLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    init {
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resumeAr() {
        if (registry.currentState == Lifecycle.State.CREATED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        if (registry.currentState == Lifecycle.State.STARTED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    fun destroy() {
        if (registry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}

private enum class ArCoreSupport {
    Checking,
    Ready,
    Unsupported,
    NeedsInstall,
    InstallInProgress,
    InstallFailed,
}

private fun resolveArCoreSupport(context: android.content.Context): ArCoreSupport =
    when (ArCoreApk.getInstance().checkAvailability(context)) {
        ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArCoreSupport.Ready
        ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
        ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
        -> ArCoreSupport.NeedsInstall
        else -> ArCoreSupport.Unsupported
    }

private fun requestArCoreInstall(activity: Activity): ArCoreSupport =
    runCatching {
        when (ArCoreApk.getInstance().requestInstall(activity, true)) {
            ArCoreApk.InstallStatus.INSTALLED -> ArCoreSupport.Ready
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> ArCoreSupport.InstallInProgress
            else -> ArCoreSupport.InstallFailed
        }
    }.getOrElse { ArCoreSupport.InstallFailed }

private const val ARTIFACT_AR_DEFAULT_MAIN_LIGHT = 120_000f
private const val ARTIFACT_AR_BOOSTED_MAIN_LIGHT = 380_000f
private const val ARTIFACT_AR_POINT_LIGHT_INTENSITY = 650_000f
private const val ARTIFACT_AR_ROTATION_SENSITIVITY = 0.72f
private const val ARTIFACT_AR_PLACED_MODEL_SCALE_UNITS = 0.3f
private const val ARTIFACT_AR_PLACED_BITMAP_SIZE_M = 0.6f
private const val ARTIFACT_AR_PLACED_LIFT_M = 0.02f
private const val ARTIFACT_AR_PLANE_DISABLE_FRAME_WAIT = 8
private const val ARTIFACT_AR_CLOSE_FRAME_WAIT_BEFORE_DESTROY = 14
private const val ARTIFACT_AR_CLOSE_FRAME_WAIT_AFTER_DESTROY = 10
private const val ARTIFACT_AR_LOG_TAG = "ArtifactAr"
/** Bottom chrome (light + close) stays outside the rotation touch overlay. */
private val ARTIFACT_AR_ROTATION_OVERLAY_BOTTOM_INSET = 108.dp

/**
 * Mutable touch state read from Android [View] listeners.
 * ARScene wires [touchDispatcher] only once in [AndroidView.factory], so lambdas that close over
 * Compose state go stale — this holder is updated via [SideEffect] instead.
 */
private class ArtifactArRotationTouchState {
    val isPlaced = AtomicBoolean(false)
    private val dragStartX = AtomicReference(Float.NaN)
    var onRotateDelta: (Float) -> Unit = {}

    fun handleTouch(event: MotionEvent): Boolean {
        if (!isPlaced.get()) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX.set(event.x)
                Log.d(ARTIFACT_AR_LOG_TAG, "rotation overlay DOWN x=${event.x}")
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val startX = dragStartX.get()
                if (!startX.isNaN()) {
                    val deltaX = event.x - startX
                    onRotateDelta(deltaX)
                    dragStartX.set(event.x)
                    Log.d(ARTIFACT_AR_LOG_TAG, "rotation overlay MOVE delta=$deltaX")
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragStartX.set(Float.NaN)
                Log.d(ARTIFACT_AR_LOG_TAG, "rotation overlay UP/CANCEL")
                true
            }
            else -> false
        }
    }
}

/** Prefer bundled GLB assets in AR — remote URLs often load meshes without embedded textures. */
private fun resolveArModelAssetPath(modelUrl: String?): String {
    val trimmed = modelUrl?.trim().orEmpty()
    if (trimmed.isNotBlank() && !trimmed.contains("://")) return trimmed
    return MapArtifactDefaults.DEMO_GLB_ASSET
}

private fun resolveAutoAnimate(modelUrl: String?): Boolean =
    MapArtifactDefaults.isAnimatedGlb(resolveArModelAssetPath(modelUrl))

@Composable
fun ArtifactArScreen(
    artifact: MapArtifactProduct,
    onClose: () -> Unit,
    userLocation: GeoPoint? = null,
    artifactLocation: GeoPoint? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCamera = granted }

    var arCoreSupport by remember { mutableStateOf(ArCoreSupport.Checking) }

    fun refreshArCoreSupport() {
        if (!hasCamera) return
        arCoreSupport = resolveArCoreSupport(context)
    }

    LaunchedEffect(hasCamera) {
        if (!hasCamera) return@LaunchedEffect
        refreshArCoreSupport()
    }

    LaunchedEffect(arCoreSupport) {
        if (arCoreSupport != ArCoreSupport.NeedsInstall) return@LaunchedEffect
        val activity = context as? Activity ?: return@LaunchedEffect
        arCoreSupport = requestArCoreInstall(activity)
    }

    DisposableEffect(lifecycleOwner, hasCamera) {
        if (!hasCamera) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    arCoreSupport = resolveArCoreSupport(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    DisposableEffect(Unit) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !hasCamera -> ArtifactArMessagePanel(
                message = stringResource(R.string.artifact_ar_camera_denied),
                actionLabel = stringResource(R.string.qr_camera_allow),
                onAction = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onClose = onClose,
            )

            arCoreSupport == ArCoreSupport.Checking -> ArtifactArLoadingPanel(onClose)

            arCoreSupport == ArCoreSupport.Unsupported -> ArtifactArMessagePanel(
                message = stringResource(R.string.artifact_ar_unsupported),
                actionLabel = null,
                onAction = null,
                onClose = onClose,
            )

            arCoreSupport == ArCoreSupport.InstallFailed -> ArtifactArMessagePanel(
                message = stringResource(R.string.artifact_ar_install_failed),
                actionLabel = stringResource(R.string.artifact_ar_install_retry),
                onAction = {
                    val activity = context as? Activity
                    if (activity != null) {
                        arCoreSupport = requestArCoreInstall(activity)
                    }
                },
                onClose = onClose,
            )

            arCoreSupport == ArCoreSupport.NeedsInstall ||
                arCoreSupport == ArCoreSupport.InstallInProgress -> {
                val message = if (arCoreSupport == ArCoreSupport.InstallInProgress) {
                    stringResource(R.string.artifact_ar_install_in_progress)
                } else {
                    stringResource(R.string.artifact_ar_installing)
                }
                ArtifactArMessagePanel(
                    message = message,
                    actionLabel = stringResource(R.string.artifact_ar_install_retry),
                    onAction = {
                        val activity = context as? Activity
                        if (activity != null) {
                            arCoreSupport = requestArCoreInstall(activity)
                        }
                    },
                    onClose = onClose,
                )
            }

            else -> {
                DisposableEffect(Unit) {
                    ArCoreSensorWarmup.start(context)
                    onDispose { ArCoreSensorWarmup.stop() }
                }
                ArtifactWorldArScene(
                    artifact = artifact,
                    userLocation = userLocation,
                    artifactLocation = artifactLocation,
                    onClose = onClose,
                )
            }
        }
    }
}

@Composable
private fun ArtifactWorldArScene(
    artifact: MapArtifactProduct,
    userLocation: GeoPoint?,
    artifactLocation: GeoPoint?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val arLifecycleOwner = remember { DeferredArLifecycleOwner() }
    var arSessionFailed by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    var keepArSceneMounted by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Let ARScene mount its SurfaceView first, then resume ARCore on our deferred lifecycle.
        withFrameNanos { }
        withFrameNanos { }
        if (!isClosing) {
            arLifecycleOwner.resumeAr()
        }
    }

    DisposableEffect(arLifecycleOwner) {
        onDispose {
            if (!isClosing) {
                arLifecycleOwner.destroy()
            }
        }
    }

    if (arSessionFailed) {
        ArtifactArMessagePanel(
            message = stringResource(R.string.artifact_ar_session_failed),
            actionLabel = null,
            onAction = null,
            onClose = onClose,
        )
        return
    }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val arEnvironment = remember(engine, context) {
        runCatching {
            val indirectLight = KTX1Loader.createIndirectLight(
                engine,
                context.assets.readBuffer("environments/neutral/neutral_ibl.ktx"),
            ).indirectLight
            createEnvironment(
                engine = engine,
                isOpaque = true,
                indirectLight = indirectLight,
                skybox = null,
            )
        }.getOrNull()
    }
    val environment = if (arEnvironment != null) {
        rememberEnvironment(environmentLoader) { arEnvironment }
    } else {
        rememberAREnvironment(engine)
    }
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = ARTIFACT_AR_DEFAULT_MAIN_LIGHT
    }

    var placementAnchor by remember { mutableStateOf<Anchor?>(null) }
    /** Anchor created on tap but not mounted until plane textures have settled (Filament crash). */
    var stagedPlacementAnchor by remember { mutableStateOf<Anchor?>(null) }
    var lastPlaneHit by remember { mutableStateOf<HitResult?>(null) }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }
    var hasValidSurface by remember { mutableStateOf(false) }
    var artworkBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var placedGlbInstance by remember { mutableStateOf<ModelInstance?>(null) }
    var glbLoadFailed by remember { mutableStateOf(false) }
    var placementMode by remember { mutableStateOf(ArtifactArPlacementMode.Default) }
    var modelRotationY by remember { mutableFloatStateOf(0f) }
    var isRotationDragging by remember { mutableStateOf(false) }
    var virtualLightOn by remember { mutableStateOf(false) }
    /** Keep plane overlay until staged placement finishes — abrupt disable leaves stale
     *  "Transparent Textured" textures bound and Filament aborts on commit. */
    var showPlaneRenderer by remember { mutableStateOf(true) }
    val isPlacementTransition = stagedPlacementAnchor != null

    val rotationTouchState = remember { ArtifactArRotationTouchState() }
    /** Imperative target — rotate a plain [SceneNode] wrapper; [ModelNode] scaleToUnits ignores Y spin. */
    val placedModelNodeRef = remember { AtomicReference<SceneNode?>(null) }

    fun applyPlacedModelRotationY(yDegrees: Float) {
        val node = placedModelNodeRef.get()
        if (node == null) {
            Log.w(ARTIFACT_AR_LOG_TAG, "applyPlacedModelRotationY: node ref is null (y=$yDegrees)")
            return
        }
        node.rotation = Rotation(y = yDegrees)
        Log.d(
            ARTIFACT_AR_LOG_TAG,
            "placed model rotationY=${yDegrees.toInt()} node=${node.rotation.y.toInt()} ref=${node.hashCode()}",
        )
    }

    SideEffect {
        rotationTouchState.isPlaced.set(placementAnchor != null)
        rotationTouchState.onRotateDelta = { delta ->
            modelRotationY -= delta * ARTIFACT_AR_ROTATION_SENSITIVITY
            applyPlacedModelRotationY(modelRotationY)
        }
    }

    SideEffect {
        if (placementAnchor != null) {
            applyPlacedModelRotationY(modelRotationY)
        } else {
            placedModelNodeRef.set(null)
        }
    }

    SideEffect {
        mainLightNode.intensity =
            if (virtualLightOn) ARTIFACT_AR_BOOSTED_MAIN_LIGHT else ARTIFACT_AR_DEFAULT_MAIN_LIGHT
        arEnvironment?.indirectLight?.intensity =
            if (virtualLightOn) 42_000f else 30_000f
    }

    val modelAssetPath = remember(artifact.modelUrl) { resolveArModelAssetPath(artifact.modelUrl) }
    val autoAnimate = remember(artifact.modelUrl) { resolveAutoAnimate(artifact.modelUrl) }
    val glbImportRotation = remember(modelAssetPath) { artifactGlbImportRotation(modelAssetPath) }
    val isDisplayContentReady = placedGlbInstance != null || artworkBitmap != null
    val isArtifactPlaced = placementAnchor != null

    LaunchedEffect(modelLoader, modelAssetPath) {
        placedGlbInstance = null
        glbLoadFailed = false
        Log.d(ARTIFACT_AR_LOG_TAG, "glb load start path=$modelAssetPath")
        // Let the AR surface / EGL settle before allocating a large glTF (logcat: EGL_BAD_ALLOC).
        repeat(4) { withFrameNanos { } }

        suspend fun loadGlb(): ModelInstance? {
            val buffer = withContext(Dispatchers.IO) {
                runCatching { context.assets.readBuffer(modelAssetPath) }.getOrNull()
            }
            if (buffer == null) {
                Log.e(ARTIFACT_AR_LOG_TAG, "glb asset read failed path=$modelAssetPath")
                return null
            }
            Log.d(ARTIFACT_AR_LOG_TAG, "glb buffer bytes=${buffer.remaining()} path=$modelAssetPath")
            return runCatching { modelLoader.createModelInstance(buffer) }
                .onFailure { error ->
                    Log.e(ARTIFACT_AR_LOG_TAG, "glb parse failed path=$modelAssetPath", error)
                }
                .getOrNull()
        }

        var instance = loadGlb()
        if (instance == null) {
            Log.w(ARTIFACT_AR_LOG_TAG, "glb first attempt failed — retry after delay")
            delay(600)
            repeat(4) { withFrameNanos { } }
            instance = loadGlb()
        }
        placedGlbInstance = instance
        glbLoadFailed = instance == null
        if (glbLoadFailed) {
            Log.w(ARTIFACT_AR_LOG_TAG, "glb unavailable — will use artwork bitmap fallback when ready")
        }
        Log.d(
            ARTIFACT_AR_LOG_TAG,
            "glb load done success=${instance != null} path=$modelAssetPath",
        )
    }

    fun cancelStagedPlacement() {
        stagedPlacementAnchor?.detach()
        stagedPlacementAnchor = null
    }

    fun detachPlacementAnchor(reenablePlaneOverlay: Boolean = true) {
        cancelStagedPlacement()
        placementAnchor?.detach()
        placementAnchor = null
        placedModelNodeRef.set(null)
        if (reenablePlaneOverlay && !isClosing) {
            showPlaneRenderer = true
        }
    }

    fun placeArtifactAtScreenPoint(screenX: Float, screenY: Float) {
        if (isPlacementTransition || placementAnchor != null || !isDisplayContentReady) {
            if (!isDisplayContentReady) {
                Log.w(ARTIFACT_AR_LOG_TAG, "place tap ignored — display content not ready yet")
            }
            return
        }
        val frame = latestFrame ?: return
        val hit = findArtifactPlaneHitAtScreenPoint(frame, screenX, screenY)
            ?.takeIf { it.isValidPlaneHit() }
            ?: lastPlaneHit?.takeIf { it.isValidPlaneHit() }
            ?: return
        Log.d(ARTIFACT_AR_LOG_TAG, "place tap — staging anchor (no preview model in scene)")
        stagedPlacementAnchor = hit.createAnchor()
    }

    LaunchedEffect(stagedPlacementAnchor) {
        val staged = stagedPlacementAnchor ?: return@LaunchedEffect
        // Hide plane overlay before mounting the placed model (textures must unbind cleanly).
        showPlaneRenderer = false
        repeat(ARTIFACT_AR_PLANE_DISABLE_FRAME_WAIT) { withFrameNanos { } }
        if (stagedPlacementAnchor !== staged) {
            staged.detach()
            return@LaunchedEffect
        }
        Log.d(
            ARTIFACT_AR_LOG_TAG,
            "staged anchor ready — mounting placed model glb=${placedGlbInstance != null} bitmap=${artworkBitmap != null}",
        )
        modelRotationY = 0f
        placementAnchor = staged
        stagedPlacementAnchor = null
    }

    fun enterSpecialPlacement() {
        detachPlacementAnchor()
        placementMode = ArtifactArPlacementMode.SpecialManual
    }

    fun restoreDefaultPlacement() {
        placementMode = ArtifactArPlacementMode.Default
        detachPlacementAnchor()
    }

    fun requestClose() {
        if (isClosing) return
        Log.d(ARTIFACT_AR_LOG_TAG, "close requested")
        isClosing = true
    }

    LaunchedEffect(isClosing) {
        if (!isClosing) return@LaunchedEffect
        Log.d(ARTIFACT_AR_LOG_TAG, "close: clearing anchors, plane renderer stays off")
        cancelStagedPlacement()
        placementAnchor = null
        stagedPlacementAnchor = null
        placedModelNodeRef.set(null)
        showPlaneRenderer = false
        repeat(ARTIFACT_AR_CLOSE_FRAME_WAIT_BEFORE_DESTROY) { withFrameNanos { } }
        Log.d(ARTIFACT_AR_LOG_TAG, "close: destroying deferred AR lifecycle")
        arLifecycleOwner.destroy()
        repeat(6) { withFrameNanos { } }
        Log.d(ARTIFACT_AR_LOG_TAG, "close: unmounting AR scene")
        keepArSceneMounted = false
        repeat(ARTIFACT_AR_CLOSE_FRAME_WAIT_AFTER_DESTROY) { withFrameNanos { } }
        Log.d(ARTIFACT_AR_LOG_TAG, "close: navigating back")
        onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isClosing) {
                cancelStagedPlacement()
                placementAnchor?.detach()
                placementAnchor = null
            }
        }
    }

    LaunchedEffect(artifact.imageUrl) {
        val imageUrls = listOfNotNull(
            artifact.imageUrl.takeIf { it.isNotBlank() },
            MapArtifactDefaults.demoFallback().imageUrl,
        ).distinct()
        for (url in imageUrls) {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build(),
            )
            val bitmap = (result as? SuccessResult)?.drawable?.toBitmap()
            if (bitmap != null) {
                artworkBitmap = bitmap
                Log.d(
                    ARTIFACT_AR_LOG_TAG,
                    "artwork bitmap loaded ${bitmap.width}x${bitmap.height} from $url",
                )
                break
            }
            Log.w(ARTIFACT_AR_LOG_TAG, "artwork bitmap load failed url=$url")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (keepArSceneMounted) {
            ARScene(
                modifier = Modifier.fillMaxSize(),
                surfaceType = SurfaceType.TextureSurface,
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                environment = environment,
                mainLightNode = mainLightNode,
                planeRenderer = showPlaneRenderer,
                lifecycle = arLifecycleOwner.lifecycle,
                sessionConfiguration = { _, config ->
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                },
                onTouchEvent = { event, _ ->
                    // Backup path: same atomic state as the overlay (ARScene touchDispatcher is stale).
                    val handled = rotationTouchState.handleTouch(event)
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN ->
                            if (handled) isRotationDragging = true
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            isRotationDragging = false
                    }
                    handled
                },
                onSessionFailed = {
                    if (!isClosing) arSessionFailed = true
                },
                onSessionUpdated = { _: Session, frame: Frame ->
                    if (isClosing) return@ARScene
                    latestFrame = frame
                    val planeHit = findArtifactPlaneHit(
                        frame = frame,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                    )
                    // UI-only: hand icon turns orange. No AnchorNode/ModelNode while planeRenderer is active.
                    hasValidSurface = planeHit != null
                    lastPlaneHit = planeHit
                },
            ) {
                placementAnchor?.let { worldAnchor ->
                    AnchorNode(anchor = worldAnchor) {
                        Node(
                            rotation = Rotation(y = modelRotationY),
                            apply = {
                                placedModelNodeRef.set(this)
                                rotation = Rotation(y = modelRotationY)
                            },
                        ) {
                            val glb = placedGlbInstance
                            when {
                                glb != null -> {
                                    ModelNode(
                                        modelInstance = glb,
                                        autoAnimate = autoAnimate,
                                        scaleToUnits = ARTIFACT_AR_PLACED_MODEL_SCALE_UNITS,
                                        rotation = glbImportRotation,
                                        position = Position(y = ARTIFACT_AR_PLACED_LIFT_M),
                                    )
                                }
                                artworkBitmap != null -> {
                                    ImageNode(
                                        bitmap = artworkBitmap!!,
                                        size = Size(
                                            x = ARTIFACT_AR_PLACED_BITMAP_SIZE_M,
                                            y = ARTIFACT_AR_PLACED_BITMAP_SIZE_M,
                                        ),
                                        position = Position(y = ARTIFACT_AR_PLACEMENT_HEIGHT_M),
                                        rotation = Rotation(x = -90f),
                                    )
                                }
                                else -> {
                                    Log.w(
                                        ARTIFACT_AR_LOG_TAG,
                                        "placed anchor mounted but no glb/bitmap content",
                                    )
                                }
                            }
                        }
                        if (virtualLightOn) {
                            LightNode(
                                type = LightManager.Type.POINT,
                                intensity = ARTIFACT_AR_POINT_LIGHT_INTENSITY,
                                position = Position(y = 0.55f, z = 0.25f),
                                apply = {
                                    color(1.0f, 0.94f, 0.82f)
                                    falloff(2.2f)
                                },
                            )
                            LightNode(
                                type = LightManager.Type.POINT,
                                intensity = ARTIFACT_AR_POINT_LIGHT_INTENSITY * 0.35f,
                                position = Position(y = 0.35f, x = -0.45f, z = 0.1f),
                                apply = {
                                    color(1.0f, 0.97f, 0.9f)
                                    falloff(1.8f)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (isArtifactPlaced) {
            // Transparent native overlay above AR TextureView — Compose siblings never receive touches.
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = ARTIFACT_AR_ROTATION_OVERLAY_BOTTOM_INSET)
                    .zIndex(10f),
                factory = { ctx ->
                    View(ctx).apply {
                        isClickable = true
                        isFocusable = false
                    }
                },
                update = { view ->
                    view.setOnTouchListener { _, event ->
                        val handled = rotationTouchState.handleTouch(event)
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN ->
                                if (handled) isRotationDragging = true
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                isRotationDragging = false
                        }
                        handled
                    }
                },
            )

            if (isRotationDragging) {
                Text(
                    text = stringResource(
                        R.string.artifact_ar_rotation_debug,
                        modelRotationY.toInt(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = EazWearColors.HubOrange,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .zIndex(11f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(EazWearColors.HubButtonCharcoal.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        if (isPlacementTransition) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = EazWearColors.HubOrange,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        if (!isArtifactPlaced && !isPlacementTransition && !isDisplayContentReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = EazWearColors.HubOrange,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.artifact_ar_loading_model),
                        style = MaterialTheme.typography.bodySmall,
                        color = EazWearColors.HubText,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (!isArtifactPlaced && !isPlacementTransition) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .zIndex(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = artifact.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = EazWearColors.HubText,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(EazWearColors.HubPanel.copy(alpha = 0.85f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                ArtifactArNavigationOverlay(
                    userLocation = userLocation,
                    artifactLocation = artifactLocation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(EazWearColors.HubPanel.copy(alpha = 0.88f)),
                )
                if (placementMode == ArtifactArPlacementMode.SpecialManual) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.artifact_ar_special_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = EazWearColors.HubOrange,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EazWearColors.HubButtonCharcoal.copy(alpha = 0.92f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                if (!hasValidSurface) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (placementMode) {
                            ArtifactArPlacementMode.SpecialManual ->
                                stringResource(R.string.artifact_ar_special_no_surface)
                            ArtifactArPlacementMode.Default ->
                                stringResource(R.string.artifact_ar_tracking_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = EazWearColors.HubText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EazWearColors.HubButtonCharcoal.copy(alpha = 0.88f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                } else if (placementMode == ArtifactArPlacementMode.SpecialManual) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.artifact_ar_special_place_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = EazWearColors.HubText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EazWearColors.HubButtonCharcoal.copy(alpha = 0.88f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            ArtifactArPlacementHandOverlay(
                hasValidSurface = hasValidSurface && isDisplayContentReady,
                onPlaceTap = { tapX, tapY ->
                    placeArtifactAtScreenPoint(tapX, tapY)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .zIndex(3f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!isArtifactPlaced) {
                Text(
                    text = stringResource(R.string.artifact_ar_place_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = EazWearColors.HubText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EazWearColors.HubPanel.copy(alpha = 0.75f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                if (placementMode == ArtifactArPlacementMode.Default && placementAnchor != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { enterSpecialPlacement() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.artifact_ar_special_enter),
                            color = EazWearColors.HubOrange,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        text = stringResource(R.string.artifact_ar_special_enter_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = EazWearColors.HubText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                if (placementMode == ArtifactArPlacementMode.SpecialManual) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { restoreDefaultPlacement() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.artifact_ar_special_cancel),
                            color = EazWearColors.HubText,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val lightActionLabel = stringResource(
                        if (virtualLightOn) {
                            R.string.artifact_ar_light_off
                        } else {
                            R.string.artifact_ar_light_on
                        },
                    )
                    IconButton(
                        onClick = { virtualLightOn = !virtualLightOn },
                        modifier = Modifier.semantics {
                            contentDescription = lightActionLabel
                        },
                    ) {
                        Icon(
                            imageVector = if (virtualLightOn) {
                                Icons.Filled.Lightbulb
                            } else {
                                Icons.Outlined.Lightbulb
                            },
                            contentDescription = stringResource(R.string.artifact_ar_light_cd),
                            tint = if (virtualLightOn) {
                                EazWearColors.HubOrange
                            } else {
                                EazWearColors.HubText
                            },
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    TextButton(onClick = { requestClose() }) {
                        Text(
                            stringResource(R.string.artifact_ar_close),
                            color = EazWearColors.HubOrange,
                        )
                    }
                }
            }
            if (!isArtifactPlaced) {
                TextButton(onClick = { requestClose() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.artifact_ar_close), color = EazWearColors.HubOrange)
                }
            }
        }
    }
}

@Composable
private fun ArtifactArLoadingPanel(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EazWearColors.HubPanel),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = EazWearColors.HubOrange)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.artifact_ar_loading),
                color = EazWearColors.HubText,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.artifact_ar_close), color = EazWearColors.HubOrange)
            }
        }
    }
}

@Composable
private fun ArtifactArMessagePanel(
    message: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EazWearColors.HubPanel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                message,
                color = EazWearColors.HubText,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.artifact_ar_close), color = EazWearColors.HubOrange)
            }
        }
    }
}

private fun android.graphics.drawable.Drawable.toBitmap(): Bitmap {
    if (this is android.graphics.drawable.BitmapDrawable && bitmap != null) {
        return bitmap
    }
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}
