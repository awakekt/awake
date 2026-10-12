/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.runBlocking

/**
 * [backend] headless at [width] by [height] with the showcase's plan, for [app] to be readied and run
 * on. Vulkan runs its real frame loop into presentable stand-in images; WebGPU's renderer is an
 * [OffscreenFrames].
 */
internal fun openRuntimeSession(backend: HeadlessUiBackend, app: AwakeAppLifecycle, width: Int, height: Int): HeadlessRenderSession =
    when (backend) {
        HeadlessUiBackend.WebGpu -> {
            val session = webGpuHeadlessPlan(EngineShowcaseRenderPlan, surface = HeadlessSurface(width, height))
            val renderer = OffscreenFrames(session.renderer, width, height)
            object : HeadlessRenderSession {
                override val renderer: Renderer = renderer
                override fun close() {
                    renderer.destroyFrameTarget()
                    session.close()
                }
            }
        }
        HeadlessUiBackend.Vulkan -> {
            val renderer = runBlocking { PlanEngine(app).boot(HeadlessSurface(width, height)) }
            object : HeadlessRenderSession {
                override val renderer: Renderer = renderer
                override fun close() = renderer.destroy()
            }
        }
    }

private class PlanEngine(app: AwakeAppLifecycle) : VulkanEngine(app, EngineShowcaseRenderPlan) {
    suspend fun boot(surface: HeadlessSurface): Renderer = createBackendResources(surface).renderer
}

/**
 * wgpu-native's fixture has no presentation loop; scheduled frames render into [frame] instead, with
 * no UI over them.
 */
internal class OffscreenFrames(private val backend: Renderer, width: Int, height: Int) :
    Renderer by backend,
    GpuDrawPreparationSource {
    override val gpuDrawPreparer = (backend as GpuDrawPreparationSource).gpuDrawPreparer
    val frame = backend.createRenderTarget(width, height)
    override fun draw(input: GpuPassInput) = backend.renderToTexture(frame, input)
    fun destroyFrameTarget() {
        backend.waitIdle()
        frame.destroy()
    }
}
