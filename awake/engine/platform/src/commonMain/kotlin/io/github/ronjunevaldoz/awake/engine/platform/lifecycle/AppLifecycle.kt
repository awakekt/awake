// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.engine.platform.lifecycle

import io.github.ronjunevaldoz.awake.render.renderer.Renderer

/**
 * A game's own behavior, injected into [io.github.ronjunevaldoz.awake.engine.platform.GraphicsEngine] rather than provided by
 * inheriting from it.
 */
interface AppLifecycle {
    suspend fun ready(renderer: Renderer)
    fun update(frame: AppFrame)
    fun resize(width: Float, height: Float) {}
    fun pause() {}
    fun resume() {}
    fun dispose() {}
}
