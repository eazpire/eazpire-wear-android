package com.eazpire.wear.discovery

import android.content.Context.WINDOW_SERVICE
import android.view.MotionEvent
import android.view.TextureView
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.Engine
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.View
import io.github.sceneview.SceneNodeManager
import io.github.sceneview.SceneScope
import io.github.sceneview.collision.CollisionSystem
import io.github.sceneview.collision.HitResult
import io.github.sceneview.environment.Environment
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.Node
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * TextureView-based SceneView with correct [MapTransparentSceneRenderer] transparency wiring.
 */
@Composable
internal fun TransparentMapSceneView(
    modifier: Modifier = Modifier,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    environmentLoader: EnvironmentLoader,
    view: View,
    renderer: Renderer,
    scene: Scene,
    environment: Environment,
    mainLightNode: LightNode?,
    cameraNode: CameraNode,
    collisionSystem: CollisionSystem,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    onTouchEvent: (MotionEvent, HitResult?) -> Boolean,
    content: @Composable SceneScope.() -> Unit,
) {
    if (LocalInspectionMode.current) return

    val context = LocalContext.current
    val scopeChildNodes: SnapshotStateList<Node> = remember { mutableStateListOf() }
    val nodeManager = remember(scene, collisionSystem) { SceneNodeManager(scene, collisionSystem) }

    SideEffect {
        scene.indirectLight = environment.indirectLight
        scene.skybox = null
        view.scene = scene
        view.camera = cameraNode.camera
        cameraNode.collisionSystem = collisionSystem
        cameraNode.setView(view)
    }

    val prevCameraNodeRef = remember { AtomicReference<CameraNode?>(null) }
    SideEffect {
        val prev = prevCameraNodeRef.get()
        if (prev != cameraNode) {
            prev?.let { nodeManager.removeNode(it) }
            nodeManager.addNode(cameraNode)
            prevCameraNodeRef.set(cameraNode)
        }
    }

    val prevMainLightRef = remember { AtomicReference<LightNode?>(null) }
    SideEffect {
        val prev = prevMainLightRef.get()
        if (prev != mainLightNode) {
            prev?.let { nodeManager.removeNode(it) }
            mainLightNode?.let { nodeManager.addNode(it) }
            prevMainLightRef.set(mainLightNode)
        }
    }

    val childNodesRef = remember { AtomicReference(emptyList<Node>()) }
    LaunchedEffect(nodeManager) {
        var prevNodes = emptyList<Node>()
        snapshotFlow { scopeChildNodes.toList() }.collect { newNodes ->
            (prevNodes - newNodes.toSet()).forEach { nodeManager.removeNode(it) }
            (newNodes - prevNodes.toSet()).forEach { nodeManager.addNode(it) }
            prevNodes = newNodes
            childNodesRef.set(newNodes)
        }
    }

    val isResumed = remember {
        AtomicBoolean(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { isResumed.set(true) }
            override fun onPause(owner: LifecycleOwner) { isResumed.set(false) }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val touchDispatcher: (MotionEvent) -> Unit = { event ->
        val hitResult = collisionSystem.hitTest(event).firstOrNull { it.node.isTouchable }
        if (onTouchEvent(event, hitResult) != true &&
            hitResult?.node?.onTouchEvent(event, hitResult) != true
        ) {
            // Map preview: tap handled via onTouchEvent only.
        }
    }

    @Suppress("DEPRECATION")
    val display = remember(context) {
        (context.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
    }

    val sceneRenderer = remember(engine, view, renderer) {
        MapTransparentSceneRenderer(engine, view, renderer)
    }

    SideEffect {
        sceneRenderer.onSurfaceResized = { _, _ ->
            cameraNode.updateProjection()
        }
    }

    DisposableEffect(sceneRenderer) {
        onDispose { sceneRenderer.destroy() }
    }

    LaunchedEffect(engine, renderer, view, scene) {
        while (true) {
            if (!isResumed.get()) {
                delay(100)
                continue
            }
            withFrameNanos { frameTimeNanos ->
                sceneRenderer.renderFrame(frameTimeNanos) {
                    modelLoader.updateLoad()
                    childNodesRef.get().forEach { it.onFrame(frameTimeNanos) }
                }
            }
        }
    }

    AndroidView(
        modifier = modifier.graphicsLayer { alpha = 1f },
        factory = { ctx ->
            TextureView(ctx).also { tv ->
                tv.isOpaque = false
                sceneRenderer.attachToTextureView(tv, isOpaque = false, ctx, display, touchDispatcher)
            }
        },
        update = { tv ->
            tv.isOpaque = false
        },
    )

    val scope = remember(engine, modelLoader, materialLoader, environmentLoader, nodeManager) {
        SceneScope(
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            _nodes = scopeChildNodes,
            nodeRemover = nodeManager::removeNode,
        )
    }
    scope.content()
}
