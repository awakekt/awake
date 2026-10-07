/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.webgpu.application.WebGpuEngine
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking

/**
 * Boots a production [RenderPlan] on a windowless WebGPU surface over wgpu-native.
 *
 * Counterpart to Vulkan's `HeadlessPlanEngine` in `samples/engine-showcase`. Where
 * [webGpuHeadlessScene] and [webGpuHeadlessUi] handcraft fixed pipeline subsets, this class boots
 * the complete requested [RenderPlan] dynamically through [WebGpuEngine], compiling all declared
 * primary/format/instanced/depth/content pipelines.
 */
class HeadlessWebGpuEngine(
    lifecycle: AwakeAppLifecycle = defaultHeadlessLifecycle(),
    plan: RenderPlan,
) : WebGpuEngine(lifecycle, plan) {

    /**
     * Creates native WebGPU backend resources over a hidden GLFW context with dimensions
     * from [surface], returning the initialized [Renderer].
     */
    suspend fun boot(surface: HeadlessSurface = HeadlessSurface(1, 1)): Renderer {
        val context = glfwContextRenderer(
            width = surface.width,
            height = surface.height,
            title = "awake-headless-webgpu",
            onUncapturedError = { error -> println("WGPU UNCAPTURED: $error") },
        )
        create(context.wgpuContext)
        return renderer
    }

    /**
     * Convenience entry point to expose teardown for test teardown.
     */
    fun destroy() {
        dispose()
    }
}

/**
 * Convenience function to boot a production [RenderPlan] on a windowless WebGPU surface,
 * returning an [AutoCloseable] [HeadlessRenderSession].
 */
fun webGpuHeadlessPlan(
    plan: RenderPlan,
    lifecycle: AwakeAppLifecycle = defaultHeadlessLifecycle(),
    surface: HeadlessSurface = HeadlessSurface(1, 1),
): HeadlessRenderSession = runBlocking {
    val engine = HeadlessWebGpuEngine(lifecycle, plan)
    val renderer = engine.boot(surface)
    object : HeadlessRenderSession {
        override val renderer: Renderer = renderer
        override fun close() {
            engine.destroy()
        }
    }
}

private fun defaultHeadlessLifecycle(width: Int = 1, height: Int = 1): AwakeAppLifecycle =
    AppSpecBuilder().apply {
        window {
            title = "headless-webgpu"
            size(width, height)
        }
    }.build().createLifecycle()
