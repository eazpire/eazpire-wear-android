package com.eazpire.wear.discovery

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Display
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.google.android.filament.Engine
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import io.github.sceneview.drainFramePipeline
import java.util.concurrent.atomic.AtomicReference

/**
 * SceneRenderer fork: upstream [io.github.sceneview.SceneRenderer] sets [TextureView.isOpaque]
 * but never [UiHelper.isOpaque], so the Filament swap chain stays opaque (black background).
 */
internal class MapTransparentSceneRenderer(
    private val engine: Engine,
    val view: View,
    private val renderer: Renderer,
) {
    private val swapChainRef = AtomicReference<SwapChain?>(null)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var displayHelper: DisplayHelper? = null
    private var display: Display? = null

    var onSurfaceResized: ((width: Int, height: Int) -> Unit)? = null
    var onSurfaceReady: ((viewHeight: () -> Int) -> Unit)? = null
    var onSurfaceDestroyed: (() -> Unit)? = null

    fun attachToTextureView(
        textureView: TextureView,
        isOpaque: Boolean,
        context: Context,
        display: Display,
        onTouch: ((MotionEvent) -> Unit)? = null,
    ) {
        this.display = display
        this.displayHelper = DisplayHelper(context)

        textureView.isOpaque = isOpaque
        textureView.setBackgroundColor(Color.TRANSPARENT)
        uiHelper.isOpaque = isOpaque

        uiHelper.renderCallback = makeRendererCallback(viewHeight = { textureView.height })
        uiHelper.attachTo(textureView)

        onTouch?.let { dispatch ->
            textureView.setOnTouchListener { _, event -> dispatch(event); true }
        }
    }

    @Suppress("unused")
    fun attachToSurfaceView(
        surfaceView: SurfaceView,
        isOpaque: Boolean,
        context: Context,
        display: Display,
        onTouch: ((MotionEvent) -> Unit)? = null,
    ) {
        this.display = display
        this.displayHelper = DisplayHelper(context)

        if (!isOpaque) {
            surfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)
            surfaceView.setZOrderOnTop(true)
            surfaceView.setBackgroundColor(Color.TRANSPARENT)
        }
        uiHelper.isOpaque = isOpaque

        uiHelper.renderCallback = makeRendererCallback(viewHeight = { surfaceView.height })
        uiHelper.attachTo(surfaceView)

        onTouch?.let { dispatch ->
            surfaceView.setOnTouchListener { _, event -> dispatch(event); true }
        }
    }

    fun renderFrame(frameTimeNanos: Long, onBeforeRender: () -> Unit) {
        val sc = swapChainRef.get() ?: return
        onBeforeRender()
        if (renderer.beginFrame(sc, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    fun applyResize(width: Int, height: Int) {
        view.viewport = Viewport(0, 0, width, height)
        onSurfaceResized?.invoke(width, height)
    }

    fun destroy() {
        uiHelper.detach()
        swapChainRef.getAndSet(null)?.let {
            runCatching { engine.destroySwapChain(it) }
        }
        displayHelper?.detach()
        displayHelper = null
        display = null
    }

    private fun makeRendererCallback(viewHeight: () -> Int) = object : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChainRef.getAndSet(
                engine.createSwapChain(surface, uiHelper.swapChainFlags),
            )?.let { engine.destroySwapChain(it) }

            displayHelper?.let { dh ->
                display?.let { d -> dh.attach(renderer, d) }
            }

            onSurfaceReady?.invoke(viewHeight)
        }

        override fun onDetachedFromSurface() {
            onSurfaceDestroyed?.invoke()
            swapChainRef.getAndSet(null)?.let { engine.destroySwapChain(it) }
            engine.flushAndWait()
            displayHelper?.detach()
        }

        override fun onResized(width: Int, height: Int) {
            applyResize(width, height)
            engine.drainFramePipeline()
        }
    }
}
