/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.engine.platform.config.WindowConfig
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.error.AppErrorSource
import com.awakekt.awake.engine.platform.error.appChildScope
import com.awakekt.awake.engine.platform.error.reportError
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.engine.platform.lifecycle.DisplayInsets
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * Backend-neutral render bootstrap shared by `VulkanEngine` (`awake-backend-vulkan`)
 * and `WebGpuEngine` (`awake-backend-webgpu`).
 */
@Suppress("TooManyFunctions") // One override per WindowLifecycle / SurfaceHolder callback.
abstract class GraphicsEngine(
    protected val appLifecycle: AwakeAppLifecycle,
) : WindowLifecycle {

    private val logger = Logger("GraphicsEngine")

    /** The session's input accumulator. */
    override val input: Input get() = appLifecycle.input

    /** The session's window configuration. */
    override val windowConfig: WindowConfig get() = appLifecycle.windowConfig

    /** This engine's app's services. */
    final override val services: AppServiceLookup get() = appLifecycle

    /**
     * Whether the backend is up and the app ready, so [update] plays frames. [create] starts the
     * backend without waiting for it, as the original `VulkanApplication`/`WebGpuApplication` did,
     * and [update] is a no-op until it finishes. It stays false when it fails; see [startupError].
     */
    @Volatile
    var isReady = false
        private set

    /**
     * Why [create] failed to start the backend or the app, or null while it hasn't. The failure is
     * logged and, when the app registered an `AppErrorHandler`, handed to it as `AppErrorSource.Bootstrap`;
     * it is not thrown.
     */
    @Volatile
    var startupError: Throwable? = null
        private set

    private var surfaceReleased = false

    /** Populated by [createBackendResources] -- `protected` (not `private`) so each
     * backend's [destroyBackend] override can still reach it for teardown. */
    protected lateinit var renderer: Renderer
        private set
    private lateinit var viewportSize: () -> Pair<Float, Float>
    private var density: () -> Float = { 1f }
    private var hostDensity: Float? = null

    // Written by a platform's UI thread, read by the frame's thread.
    @Volatile private var hostSafeArea: DisplayInsets = DisplayInsets.None

    @Volatile private var hostSystemGestureInsets: DisplayInsets = DisplayInsets.None

    @Volatile private var hostDpi: Float = 0f

    /** The current swapchain's width/height aspect ratio -- for a subclass computing a
     * [com.awakekt.awake.core.math.Frustum]'s corners to visualize via
     * [drawDebugLines]. */
    protected val aspectRatio: Float
        get() = viewportSize().let { (width, height) -> width / height }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.error(throwable) { "Uncaught exception in graphics engine coroutine" }
        appLifecycle.reportError(AppErrorSource.Coroutine, throwable)
    }

    // A child of the app's AppCoroutineScope, made by create and cancelled by dispose: the start-up, and
    // what the backend runs in the background for as long as this engine lives.
    private var engineScope: CoroutineScope? = null

    /**
     * Where a backend runs its own background work for as long as this engine lives, such as reading
     * GPU timestamps back: a child of the app's
     * [com.awakekt.awake.engine.platform.error.AppCoroutineScope], on [Dispatchers.Unconfined], that
     * [dispose] cancels. [createBackendResources] runs in it. Throws before [create].
     */
    protected val coroutineScope: CoroutineScope
        get() = checkNotNull(engineScope) { "The engine's coroutine scope exists once create() has run." }

    // TooGenericExceptionCaught intentional: native GPU/JNI layers can throw Throwable
    // (e.g. OutOfMemoryError, UnsatisfiedLinkError); catching Exception alone would miss them.
    // SuspendFunSwallowedCancellation: start-up stops quietly only when its own scope is cancelled; a
    // cancellation start-up raised itself, such as a backend's own timeout, is a failure the app hears of.
    @Suppress("TooGenericExceptionCaught", "SuspendFunSwallowedCancellation")
    final override fun create(surface: Any?) {
        // NOT MainScope(): its Dispatchers.Main can resolve to a Swing/AWT dispatcher on
        // desktop, deadlocking against -XstartOnFirstThread (already claimed by GLFW).
        // Dispatchers.Unconfined keeps every call on this calling thread instead.
        surface?.let { window ->
            val scope = appLifecycle.appChildScope(Dispatchers.Unconfined + exceptionHandler).also { engineScope = it }
            scope.launch {
                try {
                    logger.debug { "GraphicsEngine create() starting, initializing backend resources" }
                    setupCommon(window)
                    logger.info { "GraphicsEngine created and ready" }
                } catch (t: Throwable) {
                    // Disposed while starting cancels the engine's scope: ensureActive rethrows, so it is no failure.
                    if (t is CancellationException) ensureActive()
                    startupError = t
                    logger.error(t) { "Failed to initialize graphics engine" }
                    // After the log and startupError, so a handler that looks at either sees them. The backend
                    // is half built, so the engine's own start-up has no retry.
                    appLifecycle.reportError(AppErrorSource.Bootstrap, t, subject = "graphics engine")
                }
            }
        } ?: run {
            logger.error { "GraphicsEngine.create() called with null surface" }
        }
    }

    final override fun update(delta: Float) {
        if (!isReady) return
        val (width, height) = viewportSize()
        appLifecycle.update(
            AppFrame(
                delta = delta,
                viewportWidth = width,
                viewportHeight = height,
                input = input.updateSnapshot(),
                density = hostDensity ?: density(),
                safeArea = hostSafeArea,
                dpi = hostDpi,
                systemGestureInsets = hostSystemGestureInsets,
            ),
        )
    }

    final override fun pause() {
        appLifecycle.pause()
    }

    final override fun resume() {
        appLifecycle.resume()
    }

    final override fun resize(x: Int, y: Int, width: Int, height: Int) {
        if (isReady && !surfaceReleased) resizeBackend()
        appLifecycle.resize(width.toFloat(), height.toFloat())
    }

    /** Keeps the renderer and the app alive while the window has no surface: the app is paused
     * and the backend drops only what was built on the surface. */
    final override fun releaseSurface() {
        if (!isReady || surfaceReleased) return
        renderer.waitIdle()
        appLifecycle.pause()
        releaseBackendSurface()
        surfaceReleased = true
    }

    final override fun restoreSurface(surface: Any) {
        if (!surfaceReleased) return
        restoreBackendSurface(surface)
        surfaceReleased = false
        appLifecycle.resume()
    }

    final override fun setDensity(density: Float) {
        hostDensity = density.takeIf { it > 0f }
    }

    final override fun setSafeArea(insets: DisplayInsets) {
        hostSafeArea = insets.clamped()
    }

    final override fun setSystemGestureInsets(insets: DisplayInsets) {
        hostSystemGestureInsets = insets.clamped()
    }

    final override fun setDpi(dpi: Float) {
        hostDpi = if (dpi > 0f && dpi.isFinite()) dpi else 0f
    }

    final override fun dispose() {
        // Before game.dispose(), not just before destroyBackend(): a game frees its own
        // meshes/materials in dispose(), and the last frame's command buffers can still be
        // pending on them at that point.
        if (isReady) renderer.waitIdle()
        engineScope?.cancel()
        engineScope = null
        appLifecycle.dispose()
        if (isReady) destroyBackend()
    }

    /**
     * Reads the frame this engine last drew back to the CPU, as tightly packed RGBA8 pixels. A
     * backend can read it when it was created on a [HeadlessSurface], with no window to present
     * to; see [Renderer.readPresentedPixels].
     */
    suspend fun readPresentedPixels(): TextureAsset {
        check(isReady) { "The engine hasn't started, so it has drawn no frame." }
        return renderer.readPresentedPixels()
    }

    /** Draws world-space debug lines (e.g. a frustum wireframe) this frame -- see
     * [Renderer.drawDebugLines]'s doc comment
     * for the staging/depth-testing details. */
    protected fun drawDebugLines(lines: List<LineSegment>) {
        renderer.drawDebugLines(lines)
    }

    private suspend fun setupCommon(window: Any) {
        val backend = createBackendResources(window)
        renderer = backend.renderer
        viewportSize = backend.viewportSize
        density = backend.density

        appLifecycle.ready(renderer)

        isReady = true
    }

    /** Backend-specific: construct `GraphicsDevice`/`SwapchainManager`/`RenderPipeline`/
     * etc., and return the handful of interface-typed objects the shared bootstrap above
     * needs. Called once, off [create]'s calling thread only in the "hasn't suspended yet"
     * sense -- see [create]'s own doc comment. */
    protected abstract suspend fun createBackendResources(window: Any): BackendResources

    /** Destroys what the backend built on the window surface (swapchain, surface) and nothing
     * else. Only backends whose host can lose its surface (Android) override this pair. */
    protected open fun releaseBackendSurface(): Unit = throw UnsupportedOperationException("${this::class.simpleName} can't release its surface")

    /** Rebuilds what [releaseBackendSurface] destroyed, against the new [window]. */
    protected open fun restoreBackendSurface(window: Any): Unit = throw UnsupportedOperationException("${this::class.simpleName} can't restore its surface")

    /** The window surface changed size or orientation. Called on the host's UI thread, which can
     * run beside a frame, so a backend should only note the change for its next frame. */
    protected open fun resizeBackend() = Unit

    /** Backend-specific GPU teardown -- reads [renderer] (this class's own protected field)
     * plus whatever backend-local pipeline objects the override's own
     * [createBackendResources] stashed in its own fields. */
    protected abstract fun destroyBackend()

    /** The interface-typed objects [createBackendResources] must produce -- everything the
     * shared bootstrap touches generically, and nothing more (backend-concrete types like
     * `GraphicsDevice`/`SwapchainManager`/`RenderPipeline` stay entirely inside each
     * backend's own subclass). [viewportSize] is queried live each frame (not cached),
     * since the swapchain/canvas can resize. */
    protected data class BackendResources(
        val renderer: Renderer,
        val viewportSize: () -> Pair<Float, Float>,
        /** Physical pixels per device-independent unit -- see [AppFrame.density]. Defaults to
         * unscaled (1f); only a backend that actually knows the display's real scale (desktop
         * Vulkan today) overrides it. */
        val density: () -> Float = { 1f },
    )
}
