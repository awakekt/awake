/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.engine.platform

import io.github.awakelab.awake.core.input.Input

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
    val input: Input
    fun create(surface: Any? = null)
    fun update(delta: Float)
    fun pause()
    fun resume()
    fun resize(x: Int, y: Int, width: Int, height: Int)
    fun dispose()
}
