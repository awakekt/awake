/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.lifecycle

import com.awakekt.awake.render.renderer.Renderer

/**
 * A game's own behavior, injected into [com.awakekt.awake.engine.platform.GraphicsEngine] rather than provided by
 * inheriting from it.
 */
interface AppLifecycle {
    /**
     * Invoked when the rendering backend is initialized and ready for resource allocation.
     *
     * @param renderer Hardware renderer instance for the application window.
     */
    suspend fun ready(renderer: Renderer)

    /**
     * Invoked on every frame tick to update game simulation and render state.
     *
     * @param frame Per-frame timing, input, and viewport context.
     */
    fun update(frame: AppFrame)

    /**
     * Invoked when the viewport dimensions change.
     *
     * @param width New viewport width in physical pixels.
     * @param height New viewport height in physical pixels.
     */
    fun resize(width: Float, height: Float) {}

    /** Invoked when the application loses focus or is paused by the platform. */
    fun pause() {}

    /** Invoked when the application regains focus or is resumed by the platform. */
    fun resume() {}

    /** Invoked during application shutdown to release retained resources. */
    fun dispose() {}
}
