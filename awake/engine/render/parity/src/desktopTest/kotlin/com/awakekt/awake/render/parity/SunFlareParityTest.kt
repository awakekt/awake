/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.SunFlareElement
import com.awakekt.awake.asset.shaderpack.SunFlareLook
import com.awakekt.awake.asset.shaderpack.sunFlareContentFeature
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies depth-occluded sun flare on both Vulkan and WebGPU (#1537 Stage C):
 * - An unoccluded sun above the horizon adds bright flare elements at the sun and along the screen-centre axis.
 * - Geometry in front of the sun occludes all 5 depth taps and hides the flare.
 * - A sun below the horizon draws nothing.
 */
class SunFlareParityTest {

    @Test
    fun vulkanSunFlareDrawsOccludesAndHidesBelowHorizon() = verify(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuSunFlareDrawsOccludesAndHidesBelowHorizon() = verify(HeadlessUiBackend.WebGpu)

    private fun verify(backend: HeadlessUiBackend) {
        val look = SunFlareLook(
            enabled = true,
            intensity = 1f,
            sunVisibility = 1f,
            followsDirection = true,
            elements = listOf(
                SunFlareElement(position = 0f, size = 0.25f, color = Color(1f, 0.9f, 0.5f), intensity = 1f),
                SunFlareElement(position = 1f, size = 0.25f, color = Color(0.6f, 0.8f, 1f), intensity = 1f),
            ),
        )
        runBlocking { open(backend, look) }.use { session ->
            val renderer = session.renderer
            val target = renderer.createRenderTarget(SIZE, SIZE)
            val blocker = renderer.createMesh(verticalQuad(half = 3f))
            val blockerMaterial = renderer.createMaterial(LitShadowUniformLayout)
            try {
                // Camera at origin looking toward +Y/+Z; sun slightly upper-right of centre so t=0 != t=1.
                val lens = Lens(eye = Vec3f(0f, 0f, 0f), center = Vec3f(0f, 0.4f, -1f), fovYRadians = 1f, near = 0.1f, far = 100f)

                // 1. Unoccluded sun above horizon: centre ghost (t=1) must be brightly lit.
                look.sunDirection.set(0.25f, 0.5f, -1f)
                val clearPixels = render(renderer, target, lens, drawCalls = emptyList())
                val clearCenter = rgbAt(clearPixels, SIZE / 2, SIZE / 2)
                println("$backend sun flare clear centre: $clearCenter")
                assertTrue(clearCenter[2] > MIN_FLARE, "$backend: unoccluded sun must draw flare ghost at centre: $clearCenter")

                // 2. Occluded sun: quad directly in front of the sun blocks all 5 depth taps.
                val blockerDraw = RenderDrawCommand(
                    mesh = blocker,
                    material = blockerMaterial,
                    model = Mat4().apply { setTranslationScale(1.25f, 2.5f, -5f, 1f) },
                )
                val occludedPixels = render(renderer, target, lens, drawCalls = listOf(blockerDraw))
                val occludedCenter = rgbAt(occludedPixels, SIZE / 2, SIZE / 2)
                println("$backend sun flare occluded centre: $occludedCenter")
                assertTrue(occludedCenter.max() < MAX_OCCLUDED, "$backend: geometry covering the sun must hide the flare: $occludedCenter")

                // 3. Sun below horizon: flare must not draw.
                look.sunDirection.set(0f, -0.3f, -1f)
                val nightPixels = render(renderer, target, lens, drawCalls = emptyList())
                val nightCenter = rgbAt(nightPixels, SIZE / 2, SIZE / 2)
                println("$backend sun flare below horizon centre: $nightCenter")
                assertTrue(nightCenter.max() < MAX_OCCLUDED, "$backend: sun below horizon must draw no flare: $nightCenter")
            } finally {
                renderer.waitIdle()
                blocker.destroy()
                blockerMaterial.destroy()
                target.destroy()
            }
        }
    }

    private fun render(
        renderer: Renderer,
        target: com.awakekt.awake.render.texture.RenderTarget,
        lens: Lens,
        drawCalls: List<RenderDrawCommand>,
    ): ByteArray {
        val pass = ScenePassCompiler.compile(
            lens = lens,
            drawCalls = drawCalls,
            light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(0.1f, 0.1f, 0.1f), ambient = 0.05f),
            environment = EnvironmentUniforms.Default.copy(showSky = true, shadowsEnabled = false, fogDensity = 0f),
            clipSpace = renderer.clipSpace,
            aspect = 1f,
            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
        )
        renderer.renderToTexture(target, pass)
        return runBlocking { renderer.readPixels(target) }.data
    }

    private companion object {
        const val SIZE = 64
        const val MIN_FLARE = 80
        const val MAX_OCCLUDED = 20
        const val BOOT_TIMEOUT_MS = 30_000L

        fun rgbAt(pixels: ByteArray, x: Int, y: Int): List<Int> {
            val base = (y * SIZE + x) * 4
            return (0..2).map { pixels[base + it].toInt() and 0xFF }
        }

        fun verticalQuad(half: Float): MeshGeometry {
            val vertices = floatArrayOf(
                -half, -half, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
                half, -half, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
                half, half, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
                -half, half, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
            )
            return MeshGeometry(vertices, intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColor)
        }

        suspend fun open(backend: HeadlessUiBackend, look: SunFlareLook): HeadlessRenderSession {
            val plan = RenderPlan(
                primary = ScenePipeline(
                    PipelineKey.Primary,
                    PackShaderSets.LitShadow,
                    VertexFormat.PositionNormalColor,
                    materialBindings = GroupBindings.UniformOnlyMaterial,
                ),
                contentFeatures = listOf(sunFlareContentFeature(look = look, occludeByDepth = true)),
                depthPrePassShaderSet = PackShaderSets.ShadowDepth,
                sceneDepthShaderSet = PackShaderSets.SceneDepth,
            )
            if (backend == HeadlessUiBackend.WebGpu) return webGpuHeadlessPlan(plan, surface = HeadlessSurface(SIZE, SIZE))
            val ready = CompletableDeferred<Renderer>()
            val app = AppSpecBuilder().apply { ready { ready.complete(it) } }.build().createLifecycle()
            val engine = VulkanEngine(app, plan)
            engine.create(HeadlessSurface(SIZE, SIZE))
            val renderer = withTimeout(BOOT_TIMEOUT_MS) { ready.await() }
            return object : HeadlessRenderSession {
                override val renderer = renderer
                override fun close() = engine.dispose()
            }
        }
    }
}
