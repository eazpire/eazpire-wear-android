package com.eazpire.wear.discovery

import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.filament.View as FilamentView
import com.google.android.filament.utils.KTX1Loader
import io.github.sceneview.createEnvironment
import io.github.sceneview.createRenderer
import io.github.sceneview.createView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView
import io.github.sceneview.utils.readBuffer
import kotlinx.coroutines.isActive

/** Small auto-rotating GLB preview for the discovery map (no AR session). */
@Composable
fun ArtifactMapModelPreview(
    modelUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    inRange: Boolean = true,
    onClick: () -> Unit = {},
    onOutOfRangeClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, modelUrl)
    val scene = rememberScene(engine)
    val filamentView = rememberView(engine) {
        createView(engine).apply {
            blendMode = FilamentView.BlendMode.TRANSLUCENT
        }
    }
    val collisionSystem = rememberCollisionSystem(filamentView)
    val filamentRenderer = rememberRenderer(engine) {
        createRenderer(engine).apply {
            clearOptions = clearOptions.apply {
                clear = true
                clearColor = floatArrayOf(0f, 0f, 0f, 0f)
            }
        }
    }
    val transparentEnvironment = remember(environmentLoader, context) {
        val indirectLight = KTX1Loader.createIndirectLight(
            engine,
            context.assets.readBuffer("environments/neutral/neutral_ibl.ktx"),
        ).indirectLight
        createEnvironment(
            engine = engine,
            isOpaque = false,
            indirectLight = indirectLight,
            skybox = null,
        )
    }
    val environment = rememberEnvironment(environmentLoader, isOpaque = false) {
        transparentEnvironment
    }
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 90_000f
    }
    val cameraNode = rememberCameraNode(engine) {
        position = Position(x = 0f, y = 0.15f, z = 1.35f)
        lookAt(Position(y = 0.05f))
    }
    var rotationY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(filamentView, filamentRenderer, scene) {
        scene.skybox = null
        filamentView.blendMode = FilamentView.BlendMode.TRANSLUCENT
        filamentRenderer.clearOptions = filamentRenderer.clearOptions.apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
    }

    LaunchedEffect(Unit) {
        var lastFrame = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameTime ->
                val dtSec = (frameTime - lastFrame) / 1_000_000_000f
                lastFrame = frameTime
                rotationY = (rotationY + dtSec * 48f) % 360f
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clipToBounds()
            .graphicsLayer {
                alpha = 1f
                clip = true
            },
    ) {
        if (modelInstance != null) {
            TransparentMapSceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                environmentLoader = environmentLoader,
                view = filamentView,
                renderer = filamentRenderer,
                scene = scene,
                environment = environment,
                mainLightNode = mainLightNode,
                cameraNode = cameraNode,
                collisionSystem = collisionSystem,
                onTouchEvent = { event, _ ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        if (inRange) onClick() else onOutOfRangeClick()
                    }
                    true
                },
            ) {
                ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits = 0.55f,
                    rotation = Rotation(y = rotationY),
                )
            }
        }
    }
}
