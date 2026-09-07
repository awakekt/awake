/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Backend-neutral render bootstrap shared by `VulkanEngine` (`awake-backend-vulkan`)
 * and `WebGpuEngine` (`awake-backend-webgpu`).
 */
abstract class GraphicsEngine(
    protected val appLifecycle: AwakeAppLifecycle,
) : WindowLifecycle {

    private val logger = Logger("GraphicsEngine")

    /** The session's input accumulator. */
    override val input: Input get() = appLifecycle.input

    /** Same "create() stays synchronous, launch internally" reasoning the original
     * `VulkanApplication`/`WebGpuApplication` used -- [update] is a no-op until
     * [createBackendResources] (and [AppLifecycle.ready]) finish. */
    private var isReady = false

    /** Populated by [createBackendResources] -- `protected` (not `private`) so each
     * backend's [destroyBackend] override can still reach it for teardown. */
    protected lateinit var renderer: Renderer
        private set
    private lateinit var viewportSize: () -> Pair<Float, Float>
    private var density: () -> Float = { 1f }

    /** The current swapchain's width/height aspect ratio -- for a subclass computing a
     * [com.awakekt.awake.core.math.Frustum]'s corners to visualize via
     * [drawDebugLines]. */
    protected val aspectRatio: Float
        get() = viewportSize().let { (width, height) -> width / height }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.error(throwable) { "Uncaught exception in graphics engine coroutine" }
    }

    @Suppress("TooGenericExceptionCaught")
    final override fun create(surface: Any?) {
        // NOT MainScope(): its Dispatchers.Main can resolve to a Swing/AWT dispatcher on
        // desktop, deadlocking against -XstartOnFirstThread (already claimed by GLFW).
        // Dispatchers.Unconfined keeps every call on this calling thread instead.
        surface?.let { window ->
            CoroutineScope(Dispatchers.Unconfined + exceptionHandler).launch {
                try {
                    logger.debug { "GraphicsEngine create() starting, initializing backend resources" }
                    setupCommon(window)
                    logger.info { "GraphicsEngine created and ready" }
                } catch (t: Throwable) {
                    logger.error(t) { "Failed to initialize graphics engine" }
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
                density = density(),
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
        appLifecycle.resize(width.toFloat(), height.toFloat())
    }

    final override fun dispose() {
        // Before game.dispose(), not just before destroyBackend(): a game frees its own
        // meshes/materials in dispose(), and the last frame's command buffers can still be
        // pending on them at that point.
        if (isReady) renderer.waitIdle()
        appLifecycle.dispose()
        if (isReady) destroyBackend()
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
