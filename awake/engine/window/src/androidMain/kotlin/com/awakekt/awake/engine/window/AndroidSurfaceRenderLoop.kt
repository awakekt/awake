/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import android.os.Process
import com.awakekt.awake.core.host.AndroidFrameLoop
import com.awakekt.awake.core.host.FrameRateMode
import com.awakekt.awake.engine.platform.WindowLifecycle
import com.awakekt.awake.engine.platform.lifecycle.DisplayInsets
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Drives [WindowLifecycle] exclusively on a dedicated render thread (`AwakeSurfaceView-Render`) so
 * Android's UI thread never blocks inside GPU backend startup (`create` / `restoreSurface` /
 * `vkCreateGraphicsPipelines`), preventing cold-start ANRs on slow mobile shader compilers.
 *
 * All surface attachment, display metrics/inset updates, resizes, frame ticks, surface releases,
 * and final disposal run on the single render thread in strict order. [onSurfaceDestroyed] waits
 * until [WindowLifecycle.releaseSurface] completes so Android never invalidates the underlying
 * `Surface` while the backend is still presenting to it.
 */
internal class AndroidSurfaceRenderLoop(
    private val lifecycle: WindowLifecycle,
    private val tickFrame: (FrameRateMode, (Double) -> Unit) -> Unit = AndroidFrameLoop::tick,
    private val onFrameRendered: () -> Unit = {},
    private val threadName: String = DEFAULT_THREAD_NAME,
    private val configureThreadPriority: () -> Unit = ::promoteRenderThreadPriority,
) {
    private val lock = ReentrantLock()
    private val stateChanged = lock.newCondition()

    private var renderThread: Thread? = null
    private var pendingSurface: Any? = null
    private var attachInProgress = false
    private var surfaceAttached = false
    private var engineCreated = false
    private var releaseRequested = false
    private var disposeRequested = false
    private var disposed = false

    private var pendingDensity: Float = Float.NaN
    private var pendingDpi: Float = Float.NaN
    private var pendingSafeArea: DisplayInsets? = null
    private var pendingSystemGestureInsets: DisplayInsets? = null
    private var pendingImeInsets: DisplayInsets? = null
    private var hasPendingResize = false
    private var pendingWidth = 0
    private var pendingHeight = 0

    /** Queues [surface] for `create` / `restoreSurface` on the render thread without blocking the UI thread. */
    fun onSurfaceCreated(surface: Any) {
        lock.withLock {
            if (disposeRequested || disposed) return
            pendingSurface = surface
            releaseRequested = false
            if (renderThread == null) {
                renderThread = Thread(::runLoop, threadName).apply { start() }
            }
            stateChanged.signalAll()
        }
    }

    /** Records the latest display metrics and viewport dimensions to apply on the render thread. */
    fun onSurfaceChanged(density: Float, dpi: Float, width: Int, height: Int) {
        lock.withLock {
            if (disposeRequested || disposed) return
            pendingDensity = density
            pendingDpi = dpi
            pendingWidth = width
            pendingHeight = height
            hasPendingResize = true
            stateChanged.signalAll()
        }
    }

    /** Records the latest window insets to apply on the render thread. */
    fun onInsetsChanged(
        safeArea: DisplayInsets,
        systemGestures: DisplayInsets,
        ime: DisplayInsets,
    ) {
        lock.withLock {
            if (disposeRequested || disposed) return
            pendingSafeArea = safeArea
            pendingSystemGestureInsets = systemGestures
            pendingImeInsets = ime
            stateChanged.signalAll()
        }
    }

    /**
     * Stops rendering to the current surface and waits until [WindowLifecycle.releaseSurface]
     * finishes on the render thread before returning to Android's `SurfaceHolder.Callback`.
     */
    fun onSurfaceDestroyed() {
        var interrupted = false
        lock.withLock {
            if (renderThread == null || disposed) {
                pendingSurface = null
                return
            }
            pendingSurface = null
            if (!surfaceAttached && !attachInProgress) return
            releaseRequested = true
            stateChanged.signalAll()
            while ((surfaceAttached || attachInProgress) && !disposed) {
                try {
                    stateChanged.await()
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    /** Disposes the engine on the render thread (if created) and shuts down the render thread. */
    fun onDetachedFromWindow() {
        val thread: Thread? = lock.withLock {
            disposeRequested = true
            pendingSurface = null
            if (surfaceAttached || attachInProgress) releaseRequested = true
            stateChanged.signalAll()
            renderThread
        }
        if (thread != null && thread !== Thread.currentThread()) {
            var interrupted = false
            while (thread.isAlive) {
                try {
                    thread.join()
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
        lock.withLock {
            renderThread = null
        }
    }

    private fun runLoop() {
        configureThreadPriority()
        try {
            var alive = true
            while (alive) {
                when (val action = nextAction()) {
                    is LoopAction.Attach -> handleAttach(action.surface)
                    LoopAction.Release -> handleRelease()
                    LoopAction.Dispose -> alive = false
                    LoopAction.Tick -> handleTick()
                }
            }
            if (engineCreated) {
                engineCreated = false
                lifecycle.dispose()
            }
        } finally {
            lock.withLock {
                attachInProgress = false
                surfaceAttached = false
                releaseRequested = false
                disposed = true
                stateChanged.signalAll()
            }
        }
    }

    private fun nextAction(): LoopAction = lock.withLock {
        var action: LoopAction? = null
        while (action == null) {
            action = when {
                releaseRequested && surfaceAttached -> LoopAction.Release
                disposeRequested -> LoopAction.Dispose
                !surfaceAttached && pendingSurface != null && !releaseRequested -> {
                    val surface = requireNotNull(pendingSurface)
                    pendingSurface = null
                    attachInProgress = true
                    LoopAction.Attach(surface)
                }
                surfaceAttached && !releaseRequested -> LoopAction.Tick
                else -> {
                    stateChanged.awaitUninterruptibly()
                    null
                }
            }
        }
        action
    }

    private fun handleAttach(surface: Any) {
        var attached = false
        try {
            if (engineCreated) {
                lifecycle.restoreSurface(surface)
            } else {
                applyPendingDisplayState(includeResize = false)
                lifecycle.create(surface)
                engineCreated = true
            }
            attached = true
        } finally {
            lock.withLock {
                attachInProgress = false
                surfaceAttached = attached
                stateChanged.signalAll()
            }
        }
        if (attached && !lock.withLock { releaseRequested || disposeRequested }) {
            applyPendingDisplayState(includeResize = true)
        }
    }

    private fun handleRelease() {
        try {
            lifecycle.releaseSurface()
        } finally {
            lock.withLock {
                surfaceAttached = false
                releaseRequested = false
                stateChanged.signalAll()
            }
        }
    }

    private fun handleTick() {
        applyPendingDisplayState(includeResize = true)
        val mode = lifecycle.windowConfig?.frameRateMode ?: FrameRateMode.Auto
        tickFrame(mode) { deltaTime ->
            if (!lock.withLock { releaseRequested || disposeRequested }) {
                applyPendingDisplayState(includeResize = true)
                lifecycle.update(deltaTime.toFloat())
                onFrameRendered()
            }
        }
    }

    private fun applyPendingDisplayState(includeResize: Boolean) {
        var density = Float.NaN
        var dpi = Float.NaN
        var safeArea: DisplayInsets? = null
        var gestures: DisplayInsets? = null
        var ime: DisplayInsets? = null
        var resize = false
        var width = 0
        var height = 0

        lock.withLock {
            density = pendingDensity
            pendingDensity = Float.NaN
            dpi = pendingDpi
            pendingDpi = Float.NaN
            safeArea = pendingSafeArea
            pendingSafeArea = null
            gestures = pendingSystemGestureInsets
            pendingSystemGestureInsets = null
            ime = pendingImeInsets
            pendingImeInsets = null
            if (includeResize && hasPendingResize) {
                resize = true
                width = pendingWidth
                height = pendingHeight
                hasPendingResize = false
            }
        }

        if (!density.isNaN()) lifecycle.setDensity(density)
        if (!dpi.isNaN()) lifecycle.setDpi(dpi)
        safeArea?.let(lifecycle::setSafeArea)
        gestures?.let(lifecycle::setSystemGestureInsets)
        ime?.let(lifecycle::setImeInsets)
        if (resize) lifecycle.resize(0, 0, width, height)
    }

    private sealed interface LoopAction {
        data class Attach(val surface: Any) : LoopAction
        data object Release : LoopAction
        data object Dispose : LoopAction
        data object Tick : LoopAction
    }

    internal companion object {
        const val DEFAULT_THREAD_NAME = "AwakeSurfaceView-Render"

        private fun promoteRenderThreadPriority() {
            // Keep the dedicated frame thread above default-priority Dispatchers.IO / decode pools
            // so background asset streaming and crypto do not starve loading or gameplay frames.
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY) }.let {}
        }
    }
}
