package com.eazpire.wear.discovery

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.eazpire.wear.R
import com.eazpire.wear.core.model.ArDrawing
import com.eazpire.wear.theme.EazWearColors
import com.google.android.filament.LightManager
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.SurfaceType
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberAREnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LOG_TAG = "ArDrawingResolve"
private const val DEFAULT_MAIN_LIGHT = 120_000f
private const val BOOSTED_MAIN_LIGHT = 380_000f

/**
 * Opens a saved AR drawing from the map — resolves cloud anchor when available,
 * otherwise falls back to stored pose at GPS location.
 */
@Composable
fun ArDrawingResolveScreen(
    drawing: ArDrawing,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val arLifecycleOwner = remember { DeferredArLifecycleOwner() }
    var arSessionFailed by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    var keepArSceneMounted by remember { mutableStateOf(true) }
    var arSessionResumed by remember { mutableStateOf(false) }
    var resolveAnchor by remember { mutableStateOf<Anchor?>(null) }
    var resolvingCloud by remember { mutableStateOf(false) }
    var resolveFailed by remember { mutableStateOf(false) }
    var artworkBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var virtualLightOn by remember { mutableStateOf(true) }
    var arSession by remember { mutableStateOf<Session?>(null) }
    var cloudResolveAnchor by remember { mutableStateOf<Anchor?>(null) }

    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        arLifecycleOwner.resumeAr()
        arSessionResumed = true
    }

    LaunchedEffect(drawing.imageUrl) {
        val url = drawing.imageUrl ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            context.imageLoader.execute(
                ImageRequest.Builder(context).data(url).allowHardware(false).build(),
            )
        }
        artworkBitmap = (result as? SuccessResult)?.drawable?.let { d ->
            if (d is android.graphics.drawable.BitmapDrawable) d.bitmap else null
        }
    }

    DisposableEffect(arLifecycleOwner) {
        onDispose { if (!isClosing) arLifecycleOwner.destroy() }
    }

    fun requestClose() {
        if (isClosing) return
        isClosing = true
    }

    LaunchedEffect(isClosing) {
        if (!isClosing) return@LaunchedEffect
        keepArSceneMounted = false
        repeat(20) { withFrameNanos { } }
        arLifecycleOwner.destroy()
        cloudResolveAnchor?.detach()
        resolveAnchor?.detach()
        onClose()
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
    val environment = rememberAREnvironment(engine)
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = if (virtualLightOn) BOOSTED_MAIN_LIGHT else DEFAULT_MAIN_LIGHT
    }

    SideEffect {
        mainLightNode.intensity = if (virtualLightOn) BOOSTED_MAIN_LIGHT else DEFAULT_MAIN_LIGHT
    }

    val displayAnchor = resolveAnchor ?: cloudResolveAnchor

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
                planeRenderer = displayAnchor == null,
                lifecycle = arLifecycleOwner.lifecycle,
                sessionConfiguration = { _, config ->
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    ArCloudAnchorHelper.enableInConfig(config)
                },
                onSessionFailed = { if (!isClosing) arSessionFailed = true },
                onSessionUpdated = { session: Session, frame: Frame ->
                    if (isClosing) return@ARScene
                    arSession = session

                    if (cloudResolveAnchor != null && !ArCloudAnchorHelper.isResolveTerminal(cloudResolveAnchor)) {
                        ArCloudAnchorHelper.pollResolveState(cloudResolveAnchor)?.let { resolved ->
                            resolveAnchor = resolved
                            cloudResolveAnchor = null
                            resolvingCloud = false
                            Log.d(LOG_TAG, "cloud anchor resolved")
                        }
                    }

                    if (resolveAnchor == null && cloudResolveAnchor == null && !resolvingCloud && !resolveFailed) {
                        val cloudId = drawing.cloudAnchorId
                        if (!cloudId.isNullOrBlank()) {
                            resolvingCloud = true
                            cloudResolveAnchor = ArCloudAnchorHelper.beginResolve(session, cloudId)
                            if (cloudResolveAnchor == null) {
                                resolvingCloud = false
                                resolveFailed = true
                            }
                        } else {
                            resolveFailed = true
                        }
                    }

                    if (resolveFailed && resolveAnchor == null && displayAnchor == null) {
                        val pose = ArPoseSnapshot(
                            drawing.poseTx, drawing.poseTy, drawing.poseTz,
                            drawing.poseQx, drawing.poseQy, drawing.poseQz, drawing.poseQw,
                        )
                        resolveAnchor = session.createAnchor(ArPoseSnapshot.toPose(pose))
                        Log.d(LOG_TAG, "using local pose fallback anchor")
                    }
                },
            ) {
                displayAnchor?.let { anchor ->
                    artworkBitmap?.let { bitmap ->
                        AnchorNode(anchor = anchor) {
                            ImageNode(
                                bitmap = bitmap,
                                size = Size(x = drawing.widthM, y = drawing.widthM),
                            )
                            LightNode(
                                type = LightManager.Type.POINT,
                                intensity = 650_000f,
                                position = Position(y = 0.2f, z = 0.1f),
                            )
                        }
                    }
                }
            }
        }

        if (displayAnchor == null || artworkBitmap == null) {
            Box(
                modifier = Modifier.fillMaxSize().zIndex(2f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = EazWearColors.HubOrange)
                    Text(
                        text = stringResource(R.string.artifact_ar_drawing_resolving),
                        color = EazWearColors.HubText,
                        modifier = Modifier.padding(top = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .zIndex(3f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = drawing.title ?: stringResource(R.string.artifact_ar_drawing_untitled),
                style = MaterialTheme.typography.titleMedium,
                color = EazWearColors.HubText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(EazWearColors.HubPanel.copy(alpha = 0.85f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .zIndex(3f),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { virtualLightOn = !virtualLightOn }) {
                Icon(
                    imageVector = if (virtualLightOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                    contentDescription = stringResource(R.string.artifact_ar_light_cd),
                    tint = if (virtualLightOn) EazWearColors.HubOrange else EazWearColors.HubText,
                    modifier = Modifier.size(32.dp),
                )
            }
            TextButton(onClick = { requestClose() }) {
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
            Text(message, color = EazWearColors.HubText, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))
                androidx.compose.material3.Button(onClick = onAction) { Text(actionLabel) }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.artifact_ar_close), color = EazWearColors.HubOrange)
            }
        }
    }
}
