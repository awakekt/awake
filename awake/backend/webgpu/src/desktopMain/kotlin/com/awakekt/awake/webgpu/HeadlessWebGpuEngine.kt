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
import com.awakekt.awake.webgpu.device.withDisplayEncodedColorFormat
import com.awakekt.awake.webgpu.device.withGpuTiming
import io.ygdrasil.webgpu.CompositeAlphaMode
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.SurfaceConfiguration
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
    private var windowHandle = 0L
    private var presentSurface: (() -> Unit)? = null

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
        val gpu = context.wgpuContext.withGpuTiming { error -> println("WGPU UNCAPTURED: $error") }
            .withDisplayEncodedColorFormat()
        // Offscreen probes did not acquire the surface. Full-frame probes do, so configure the
        // same display-encoded format the engine adopts before its first acquire.
        gpu.surface.configure(
            SurfaceConfiguration(
                device = gpu.device,
                format = gpu.renderingContext.textureFormat,
                usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
                alphaMode = CompositeAlphaMode.Opaque,
            ),
        )
        windowHandle = context.windowHandler
        presentSurface = { gpu.surface.present() }
        create(gpu)
        return renderer
    }

    /**
     * Convenience entry point to expose teardown for test teardown.
     */
    fun destroy() {
        // A full-frame probe must hand its acquired texture back before closing the surface.
        if ((renderer as? com.awakekt.awake.webgpu.renderer.Renderer)?.presentedColor != null) presentSurface?.invoke()
        try {
            dispose()
        } finally {
            if (windowHandle != 0L) org.lwjgl.glfw.GLFW.glfwDestroyWindow(windowHandle)
            windowHandle = 0L
            presentSurface = null
        }
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
