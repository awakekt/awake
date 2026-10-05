/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.core

import com.awakekt.awake.engine.platform.config.WindowConfig
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.AppLifecycle
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.renderer.Renderer
import kotlin.reflect.KClass

/**
 * Immutable specification for an [AwakeAppLifecycle].
 *
 * @property windowConfig Window and presentation configuration settings.
 * @param onReady Initializer callbacks invoked when the renderer becomes ready.
 * @param onTick Per-frame update callbacks invoked during application tick.
 * @param onResize Viewport resize callbacks invoked when display bounds change.
 * @param onPause Callbacks invoked when the application pauses or loses focus.
 * @param onResume Callbacks invoked when the application resumes from a paused state.
 * @param onDispose Teardown callbacks invoked in reverse order during disposal.
 * @param services Registered dependency injection services keyed by class.
 */
class AppSpec internal constructor(
    val windowConfig: WindowConfig,
    private val onReady: List<suspend (Renderer) -> Unit>,
    private val onTick: List<(frame: AppFrame) -> Unit>,
    private val onResize: List<(width: Float, height: Float) -> Unit>,
    private val onPause: List<() -> Unit>,
    private val onResume: List<() -> Unit>,
    private val onDispose: List<() -> Unit>,
    private val services: Map<KClass<*>, Any>,
) {
    /**
     * Creates a new [AwakeAppLifecycle] instance wired with the configured callbacks and services.
     *
     * @return Fully configured [AwakeAppLifecycle] instance ready for platform initialization.
     */
    fun createLifecycle(): AwakeAppLifecycle = AwakeAppLifecycle(
        delegate = object : AppLifecycle {
            override suspend fun ready(renderer: Renderer) {
                onReady.forEach { callback -> callback(renderer) }
            }

            override fun update(frame: AppFrame) {
                onTick.forEach { callback -> callback(frame) }
            }

            override fun resize(width: Float, height: Float) {
                onResize.forEach { callback -> callback(width, height) }
            }

            override fun pause() {
                onPause.forEach { callback -> callback() }
            }

            override fun resume() {
                onResume.forEach { callback -> callback() }
            }

            override fun dispose() {
                onDispose.asReversed().forEach { callback -> callback() }
            }
        },
        windowConfig = windowConfig,
        services = services,
    )
}
