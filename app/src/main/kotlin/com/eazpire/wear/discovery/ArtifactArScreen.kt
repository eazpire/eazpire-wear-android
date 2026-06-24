package com.eazpire.wear.discovery

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.utils.readBuffer

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

/** Prefer bundled GLB assets in AR — remote URLs often load meshes without embedded textures. */
private fun resolveArModelAssetPath(modelUrl: String?): String {
    val trimmed = modelUrl?.trim().orEmpty()
    if (trimmed.isNotBlank() && !trimmed.contains("://")) return trimmed
    return MapArtifactDefaults.DEMO_GLB_ASSET
}

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

    LaunchedEffect(isClosing) {
        if (!isClosing) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        onClose()
    }

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
        intensity = 120_000f
    }

    var placementAnchor by remember { mutableStateOf<Anchor?>(null) }
    var previewAnchor by remember { mutableStateOf<Anchor?>(null) }
    var lastPreviewPose by remember { mutableStateOf<com.google.ar.core.Pose?>(null) }
    var lastPlaneHit by remember { mutableStateOf<HitResult?>(null) }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }
    var hasValidSurface by remember { mutableStateOf(false) }
    var artworkBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var placementMode by remember { mutableStateOf(ArtifactArPlacementMode.Default) }
    var modelRotationY by remember { mutableFloatStateOf(0f) }

    val modelAssetPath = remember(artifact.modelUrl) { resolveArModelAssetPath(artifact.modelUrl) }
    val glbInstance = rememberModelInstance(modelLoader, modelAssetPath)
    val isArtifactPlaced = placementAnchor != null

    fun detachPlacementAnchor() {
        placementAnchor?.detach()
        placementAnchor = null
    }

    fun clearPreviewAnchor() {
        previewAnchor?.detach()
        previewAnchor = null
        lastPreviewPose = null
    }

    fun placeArtifactAtScreenPoint(screenX: Float, screenY: Float) {
        val frame = latestFrame ?: return
        val hit = findArtifactPlaneHitAtScreenPoint(frame, screenX, screenY)
            ?.takeIf { it.isValidPlaneHit() }
            ?: lastPlaneHit?.takeIf { it.isValidPlaneHit() }
            ?: return
        detachPlacementAnchor()
        placementAnchor = hit.createAnchor()
        clearPreviewAnchor()
        modelRotationY = 0f
    }

    fun enterSpecialPlacement() {
        detachPlacementAnchor()
        clearPreviewAnchor()
        placementMode = ArtifactArPlacementMode.SpecialManual
    }

    fun restoreDefaultPlacement() {
        placementMode = ArtifactArPlacementMode.Default
        detachPlacementAnchor()
        clearPreviewAnchor()
    }

    fun requestClose() {
        if (isClosing) return
        isClosing = true
        clearPreviewAnchor()
        detachPlacementAnchor()
        arLifecycleOwner.destroy()
    }

    DisposableEffect(Unit) {
        onDispose {
            clearPreviewAnchor()
            detachPlacementAnchor()
        }
    }

    LaunchedEffect(artifact.imageUrl) {
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(artifact.imageUrl)
                .allowHardware(false)
                .build(),
        )
        artworkBitmap = (result as? SuccessResult)?.drawable?.toBitmap()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isClosing) {
            ARScene(
                modifier = Modifier.fillMaxSize(),
                surfaceType = SurfaceType.TextureSurface,
                engine = engine,
                modelLoader = modelLoader,
                environment = environment,
                mainLightNode = mainLightNode,
                planeRenderer = !isArtifactPlaced,
                lifecycle = arLifecycleOwner.lifecycle,
                sessionConfiguration = { _, config ->
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
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
                    hasValidSurface = planeHit != null
                    lastPlaneHit = planeHit

                    if (placementAnchor == null && planeHit != null) {
                        val hitPose = planeHit.hitPose
                        if (shouldUpdatePreviewAnchor(lastPreviewPose, hitPose)) {
                            previewAnchor?.detach()
                            previewAnchor = planeHit.createAnchor()
                            lastPreviewPose = hitPose
                        }
                    } else if (placementAnchor != null) {
                        clearPreviewAnchor()
                    } else {
                        clearPreviewAnchor()
                    }
                },
            ) {
                if (placementAnchor == null) {
                    previewAnchor?.let { preview ->
                        AnchorNode(anchor = preview) {
                            if (glbInstance != null) {
                                ModelNode(
                                    modelInstance = glbInstance,
                                    scaleToUnits = 0.65f,
                                )
                            } else {
                                artworkBitmap?.let { bitmap ->
                                    Node(
                                        position = Position(y = ARTIFACT_AR_PLACEMENT_HEIGHT_M),
                                        rotation = Rotation(x = -90f),
                                    ) {
                                        ImageNode(
                                            bitmap = bitmap,
                                            size = Size(x = 0.65f, y = 0.65f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                placementAnchor?.let { worldAnchor ->
                    AnchorNode(anchor = worldAnchor) {
                        if (glbInstance != null) {
                            ModelNode(
                                modelInstance = glbInstance,
                                scaleToUnits = 0.75f,
                                rotation = Rotation(y = modelRotationY),
                            )
                        } else {
                            artworkBitmap?.let { bitmap ->
                                Node(
                                    position = Position(y = ARTIFACT_AR_PLACEMENT_HEIGHT_M),
                                    rotation = Rotation(x = -90f),
                                ) {
                                    ImageNode(
                                        bitmap = bitmap,
                                        size = Size(x = 0.75f, y = 0.75f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isArtifactPlaced) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            modelRotationY -= dragAmount * 0.35f
                        }
                    },
            )
        }

        if (!isArtifactPlaced) {
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
                hasValidSurface = hasValidSurface,
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
                Spacer(modifier = Modifier.height(8.dp))
            }
            TextButton(onClick = { requestClose() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.artifact_ar_close), color = EazWearColors.HubOrange)
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
