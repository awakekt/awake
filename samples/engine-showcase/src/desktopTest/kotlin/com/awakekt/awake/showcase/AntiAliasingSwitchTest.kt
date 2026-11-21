/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.engine.bootstrap.dsl.appSpec
import com.awakekt.awake.engine.bootstrap.dsl.renderQuality
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.engine.platform.quality.RenderQualitySettings
import com.awakekt.awake.render.pipeline.MultisampleState
import com.awakekt.awake.render.renderer.AntiAliasing
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A settings screen switches between [AntiAliasing.Off] and [AntiAliasing.Fxaa] from the next frame
 * (#1791): the engine applies [RenderQualitySettings] before every frame, and a renderer whose scene
 * pass is single-sampled takes either mode there. A multisampled mode changes the scene pass's
 * attachments and pipelines, which the engine builds once, so it still waits for the next start.
 *
 * Booted through each engine, so the renderer offers what the device does. Which effects a camera
 * runs in each mode is `FxaaAntiAliasingModeTest`'s.
 */
class AntiAliasingSwitchTest {
    @Test
    fun vulkanSwitchesBetweenOffAndFxaaFromTheNextFrame() = verifySwitching(RenderBackend.Vulkan)

    @Test
    fun webGpuSwitchesBetweenOffAndFxaaFromTheNextFrame() = verifySwitching(RenderBackend.WebGpu)

    @Test
    fun vulkanBuiltMultisampledKeepsItsModeUntilRestart() = verifyMultisampledStays(RenderBackend.Vulkan)

    @Test
    fun webGpuBuiltMultisampledKeepsItsModeUntilRestart() = verifyMultisampledStays(RenderBackend.WebGpu)

    private fun verifySwitching(backend: RenderBackend) {
        withRenderer(backend, AntiAliasing.Off) { renderer, settings ->
            assertEquals(AntiAliasing.Off, renderer.antiAliasing, "$backend starts as built")

            settings.antiAliasing = AntiAliasing.Fxaa
            settings.applyTo(renderer)
            assertEquals(AntiAliasing.Fxaa, renderer.antiAliasing, "$backend takes FXAA without a restart")

            settings.antiAliasing = AntiAliasing.Off
            settings.applyTo(renderer)
            assertEquals(AntiAliasing.Off, renderer.antiAliasing, "$backend turns FXAA off without a restart")

            settings.antiAliasing = AntiAliasing.Msaa4x
            settings.applyTo(renderer)
            assertEquals(AntiAliasing.Off, renderer.antiAliasing, "$backend builds multisampling only at start")
        }
    }

    private fun verifyMultisampledStays(backend: RenderBackend) {
        withRenderer(backend, AntiAliasing.Msaa4x) { renderer, settings ->
            assertEquals(AntiAliasing.Msaa4x, renderer.antiAliasing, "$backend builds 4x")
            settings.antiAliasing = AntiAliasing.Fxaa
            settings.applyTo(renderer)
            assertEquals(AntiAliasing.Msaa4x, renderer.antiAliasing, "$backend keeps its multisampled pass until restart")
            assertEquals(false, renderer.switchAntiAliasing(AntiAliasing.Off))
        }
    }

    /** Boots [backend]'s engine with [start] as the app's anti-aliasing, then runs [check] on its renderer and settings. */
    private fun withRenderer(backend: RenderBackend, start: AntiAliasing, check: (Renderer, RenderQualitySettings) -> Unit) = runBlocking {
        val lifecycle: AwakeAppLifecycle = appSpec {
            window { size(SIZE, SIZE) }
            renderQuality { antiAliasing = start }
        }.createLifecycle()
        val settings = requireNotNull(lifecycle.service(RenderQualitySettings::class))
        if (backend == RenderBackend.Vulkan) {
            val engine = SwitchEngine(lifecycle)
            engine.create(HeadlessSurface(SIZE, SIZE))
            try {
                withTimeout(60_000) {
                    while (!engine.isReady) {
                        engine.startupError?.let { throw it }
                        delay(1)
                    }
                }
                check(engine.backend(), settings)
            } finally {
                engine.dispose()
            }
        } else {
            val session = webGpuHeadlessPlan(EngineShowcaseRenderPlan, lifecycle = lifecycle, surface = HeadlessSurface(SIZE, SIZE))
            try {
                check(session.renderer, settings)
            } finally {
                session.close()
            }
        }
    }

    private class SwitchEngine(lifecycle: AwakeAppLifecycle) : VulkanEngine(lifecycle, EngineShowcaseRenderPlan.copy(multisample = MultisampleState.Single)) {
        fun backend(): Renderer = renderer
    }

    private companion object {
        const val SIZE = 64
    }
}
