/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.engine.platform.config.WindowConfig

/**
 * The window/surface shell a backend drives, plus the subsystems a game reaches through it.
 *
 * Subsystem properties get added here as they land -- the same role libGDX's `Application`
 * accessors play, minus its `Gdx.*` statics: each one is injected, never global. Planned shape:
 * - `val audio: Audio` once the audio milestone starts
 * - `val files: Files` if `core.utils.Resource` outgrows being a plain object
 *
 * No `graphics` accessor is planned. Awake pushes what libGDX's `Graphics` lets you pull:
 * delta and viewport size are parameters of `AppLifecycle.update`, the GPU context is the
 * `Renderer` handed to `AppLifecycle.ready`, and fps/frame-time live on `FrameStats`. Nothing
 * is left for such a property to hold.
 */
interface WindowLifecycle {
    /** Input event accumulator driving this window session. */
    val input: Input

    /** Configuration describing window dimensions and presentation options, or `null`. */
    val windowConfig: WindowConfig? get() = null

    /**
     * Initializes the platform window and rendering surface.
     *
     * @param surface Platform-specific window or surface handle, or `null` for offscreen or default creation.
     */
    fun create(surface: Any? = null)

    /**
     * Advances the window simulation and renders one frame.
     *
     * @param delta Elapsed time in seconds since the previous frame.
     */
    fun update(delta: Float)

    /** Pauses window rendering and notifies underlying subsystems. */
    fun pause()

    /** Resumes window rendering and notifies underlying subsystems. */
    fun resume()

    /**
     * Resizes the window viewport and adjusts swapchain bounds.
     *
     * @param x Window horizontal position in pixels.
     * @param y Window vertical position in pixels.
     * @param width New window width in pixels.
     * @param height New window height in pixels.
     */
    fun resize(x: Int, y: Int, width: Int, height: Int)

    /** Destroys the window and releases platform handles. */
    fun dispose()

    /** The window lost its surface (Android: the app went to the background). The renderer and
     * the app stay alive; nothing is drawn until [restoreSurface]. */
    fun releaseSurface() = Unit

    /** A new [surface] replaces the one given up in [releaseSurface]. */
    fun restoreSurface(surface: Any) = Unit

    /**
     * Sets the display's physical pixels per dp, from a platform whose surface can't report it (Android).
     *
     * @param density Physical pixels per dp display density.
     */
    fun setDensity(density: Float) = Unit
}
