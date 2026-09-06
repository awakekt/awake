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
    suspend fun ready(renderer: Renderer)
    fun update(frame: AppFrame)
    fun resize(width: Float, height: Float) {}
    fun pause() {}
    fun resume() {}
    fun dispose() {}
}
