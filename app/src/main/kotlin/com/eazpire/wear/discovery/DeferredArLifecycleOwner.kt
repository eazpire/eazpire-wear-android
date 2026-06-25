package com.eazpire.wear.discovery

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * SceneView resumes ARCore when its lifecycle observer is added. If the activity is already
 * RESUMED, that happens before the AR [android.view.SurfaceView] exists — [com.google.ar.core.Session.resume] then crashes.
 * This owner keeps ARCore paused until [resumeAr] is called after the surface is mounted.
 */
internal class DeferredArLifecycleOwner : LifecycleOwner {
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
