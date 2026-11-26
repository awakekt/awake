/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

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
import com.awakekt.awake.render.passes.directionalShadowBox
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.skinnedMaterialFloats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end headless parity proof for automatic skinned draw batching on Vulkan and WebGPU.
 *
 * Submits two separate single `PositionNormalColorSkin` draws (no authored `instanceModels` or
 * `instanceJointPalettes`) carrying distinct transforms, joint palettes, and `SkinnedMaterialLayout`
 * tints through [ScenePassCompiler.compile]. Verifies both backends automatically fold the two
 * draws into 1 skinned-instanced draw (and 1 shadow caster draw when shadows are enabled) and render
 * matching tinted posed quads at the exact expected screen centroids and alpha.
 */
class AutoSkinnedBatchingParityTest {
    @Test
    fun vulkanAutoFoldsSingleSkinnedDrawsWithPosesAndTints() = verify(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuAutoFoldsSingleSkinnedDrawsWithPosesAndTints() = verify(HeadlessUiBackend.WebGpu)

    private fun verify(backend: HeadlessUiBackend) = runBlocking {
        open(backend).use { session ->
            val renderer = session.renderer
            val target = renderer.createRenderTarget(SIZE, SIZE)
            val mesh = renderer.createMesh(quad())
            val material = renderer.createMaterial(SkinnedUniformLayout)
            try {
                val lens = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 20f).apply {
                    projection = Lens.Projection.Orthographic
                    orthoHalfHeight = 2f
                }
                val leftRed = RenderDrawCommand(
                    mesh = mesh,
                    material = material,
                    model = Mat4().translate(-1f, 0f, 0f),
                    extraUniformFloats = skinnedMaterialFloats(
                        palette = palette(-0.6f),
                        baseColorFactor = RED,
                        emissiveFactor = NO_EMISSIVE,
                    ),
                )
                val rightBlue = RenderDrawCommand(
                    mesh = mesh,
                    material = material,
                    model = Mat4().translate(1f, 0f, 0f),
                    extraUniformFloats = skinnedMaterialFloats(
                        palette = palette(0.6f),
                        baseColorFactor = BLUE,
                        emissiveFactor = NO_EMISSIVE,
                    ),
                )
                val preparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer

                fun compile(draws: List<RenderDrawCommand>, shadowsEnabled: Boolean) = ScenePassCompiler.compile(
                    lens = lens,
                    drawCalls = draws,
                    light = SceneLight(
                        direction = Vec3f(0f, 0f, 1f),
                        color = Vec3f(1f, 1f, 1f),
                        viewProjection = if (shadowsEnabled) directionalShadowBox(Vec3f(0f, 0f, 1f), renderer.clipSpace).viewProjection else null,
                    ),
                    environment = EnvironmentUniforms.Default.copy(shadowsEnabled = shadowsEnabled),
                    clipSpace = renderer.clipSpace,
                    aspect = 1f,
                    drawPreparer = preparer,
                )

                val compiled = compile(listOf(leftRed, rightBlue), shadowsEnabled = false)
                assertEquals(1, compiled.resolvedOpaqueDraws.size, "$backend: two matching single skinned draws must auto-fold into 1 draw")
                assertEquals(2, compiled.resolvedOpaqueDraws[0].instances, "$backend: folded draw must carry 2 instances")
                renderer.renderToTexture(target, compiled)
                val tinted = counts(renderer.readPixels(target).data)
                println("$backend auto-batched skinned: $tinted")
                assertTrue(tinted.red > 400 && tinted.blue > 400, "$backend: both auto-batched tinted instances must render: $tinted")
                assertTrue(tinted.redX < 50 && tinted.blueX > 78, "$backend: transforms must stay aligned per instance: $tinted")
                assertTrue(tinted.redY > 70 && tinted.blueY < 58, "$backend: joint palettes must stay aligned per instance: $tinted")
                assertTrue(tinted.redAlpha in 125..130, "$backend: baseColorFactor alpha must survive auto-batching: $tinted")

                val shadowed = compile(listOf(leftRed, rightBlue), shadowsEnabled = true)
                assertEquals(1, shadowed.resolvedOpaqueDraws.size, "$backend: shadowed auto-batched draws must still fold to 1")
                assertTrue(
                    shadowed.prePasses.any { pass -> pass.resolvedDraws.size == 1 && pass.resolvedDraws[0].instances == 2 },
                    "$backend: shadow cascade must cast both auto-batched instances in 1 draw",
                )
                renderer.renderToTexture(target, shadowed)
                val shadowedCounts = counts(renderer.readPixels(target).data)
                assertTrue(shadowedCounts.red > 400 && shadowedCounts.blue > 400, "$backend: shadowed auto-batched draws must render: $shadowedCounts")
            } finally {
                renderer.waitIdle()
                mesh.destroy()
                material.destroy()
                target.destroy()
            }
        }
    }

    private suspend fun open(backend: HeadlessUiBackend): HeadlessRenderSession {
        val plan = EngineShowcaseRenderPlan.copy(contentFeatures = emptyList())
        if (backend == HeadlessUiBackend.WebGpu) return webGpuHeadlessPlan(plan, surface = HeadlessSurface(SIZE, SIZE))
        val ready = CompletableDeferred<Renderer>()
        val app = AppSpecBuilder().apply { ready { ready.complete(it) } }.build().createLifecycle()
        val engine = VulkanEngine(app, plan)
        engine.create(HeadlessSurface(SIZE, SIZE))
        val renderer = withTimeout(30_000) { ready.await() }
        return object : HeadlessRenderSession {
            override val renderer = renderer
            override fun close() = engine.dispose()
        }
    }

    private fun palette(y: Float): FloatArray = Mat4().data + Mat4().translate(0f, y, 0f).data

    private fun quad() = MeshGeometry(
        floatArrayOf(
            -0.4f, -0.4f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, Float.fromBits(1), 0f, 0f, 0f, 1f, 0f, 0f,
            0.4f, -0.4f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, Float.fromBits(1), 0f, 0f, 0f, 1f, 0f, 0f,
            0.4f, 0.4f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, Float.fromBits(1), 0f, 0f, 0f, 1f, 0f, 0f,
            -0.4f, 0.4f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, Float.fromBits(1), 0f, 0f, 0f, 1f, 0f, 0f,
        ),
        intArrayOf(0, 1, 2, 2, 3, 0),
        VertexFormat.PositionNormalColorSkin,
    )

    private fun counts(pixels: ByteArray): Counts {
        var red = 0
        var blue = 0
        var redX = 0
        var redY = 0
        var blueX = 0
        var blueY = 0
        var redAlpha = 0
        for (pixel in 0 until SIZE * SIZE) {
            val r = pixels[pixel * 4].toInt() and 255
            val g = pixels[pixel * 4 + 1].toInt() and 255
            val b = pixels[pixel * 4 + 2].toInt() and 255
            if (r > 100 && r > g + 50 && r > b + 50) {
                red++
                redX += pixel % SIZE
                redY += pixel / SIZE
                redAlpha += pixels[pixel * 4 + 3].toInt() and 255
            } else if (b > 100 && b > r + 50 && b > g + 50) {
                blue++
                blueX += pixel % SIZE
                blueY += pixel / SIZE
            }
        }
        return Counts(red, blue, redX / maxOf(1, red), redY / maxOf(1, red), blueX / maxOf(1, blue), blueY / maxOf(1, blue), redAlpha / maxOf(1, red))
    }

    private data class Counts(val red: Int, val blue: Int, val redX: Int, val redY: Int, val blueX: Int, val blueY: Int, val redAlpha: Int)

    private companion object {
        const val SIZE = 128
        val RED = Color(1f, 0f, 0f, 0.5f)
        val BLUE = Color(0f, 0f, 1f, 1f)
        val NO_EMISSIVE = Color(0f, 0f, 0f, 0f)
    }
}
