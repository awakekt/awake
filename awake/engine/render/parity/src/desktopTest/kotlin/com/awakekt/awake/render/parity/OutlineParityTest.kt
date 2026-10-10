/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.outlineContentFeature
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.NO_MASK_LAYER
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.OutlineStyle
import com.awakekt.awake.render.renderer.Renderer
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
 * The silhouette outline on both backends, through the real plan's mask pass and outline overlay:
 * a white cube seen face-on under an orthographic camera, so it is a square of known pixels.
 *
 * Masked in set 0 the square gets a red ring, [WIDTH] pixels wide on each side of the centre row,
 * with no red inside it; in set 1 the ring is blue; unmasked there is no ring at all, which is the
 * control. Measured on WebGPU (Windows, a discrete GPU): 4 red pixels left and 4 right in set 0,
 * none inside, and none of either colour unmasked. CI measures Vulkan on lavapipe; each run prints
 * its counts.
 */
class OutlineParityTest {
    @Test
    fun vulkanOutlinesTheMaskedCube() = verify(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuOutlinesTheMaskedCube() = verify(HeadlessUiBackend.WebGpu)

    private fun verify(backend: HeadlessUiBackend) = runBlocking {
        open(backend).use { session ->
            val renderer = session.renderer
            val target = renderer.createRenderTarget(SIZE, SIZE)
            val mesh = renderer.createMesh(generate { cube(size = CUBE, colored = false) })
            val material = renderer.createMaterial(LitShadowUniformLayout)
            try {
                val lens = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 20f).apply {
                    projection = Lens.Projection.Orthographic
                    orthoHalfHeight = HALF_HEIGHT
                }
                suspend fun render(maskLayer: Int): Row {
                    renderer.renderToTexture(
                        target,
                        ScenePassCompiler.compile(
                            lens = lens,
                            drawCalls = listOf(RenderDrawCommand(mesh, material, maskLayer = maskLayer)),
                            light = SceneLight(direction = Vec3f(0f, 0f, 1f), color = Vec3f(1f, 1f, 1f)),
                            clipSpace = renderer.clipSpace,
                            aspect = 1f,
                            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
                            maskLayers = listOf(SELECTED.packed(), HOVERED.packed()),
                        ),
                    )
                    return measure(renderer.readPixels(target).data)
                }
                val selected = render(0)
                val hovered = render(1)
                val unmasked = render(NO_MASK_LAYER)
                println("$backend outline: selected=$selected hovered=$hovered unmasked=$unmasked")

                assertEquals(0, unmasked.red + unmasked.blue, "$backend: nothing masked draws no outline: $unmasked")
                assertTrue(selected.left in WIDTH - 1..WIDTH + 1 && selected.right in WIDTH - 1..WIDTH + 1, "$backend: a ring $WIDTH px wide: $selected")
                assertEquals(0, selected.inside, "$backend: nothing inside the silhouette: $selected")
                assertTrue(selected.red > RING_PIXELS && selected.blue == 0, "$backend: set 0 in its own colour: $selected")
                assertTrue(hovered.blue > RING_PIXELS && hovered.red == 0, "$backend: set 1 in its own colour: $hovered")
            } finally {
                renderer.waitIdle()
                mesh.destroy()
                material.destroy()
                target.destroy()
            }
        }
    }

    private suspend fun open(backend: HeadlessUiBackend): HeadlessRenderSession {
        // The showcase's plan without its fog and sky, plus the outline: the mask pass and its overlay.
        val plan = EngineShowcaseRenderPlan.copy(contentFeatures = listOf(outlineContentFeature()), maskShaderSet = PackShaderSets.SceneDepth)
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

    /** Red and blue pixels in the frame, and red ones in the centre row: left of the square, right of it, and inside it. */
    private fun measure(pixels: ByteArray): Row {
        fun red(pixel: Int) = pixels.channel(pixel, 0) > STRONG && pixels.channel(pixel, 1) < WEAK && pixels.channel(pixel, 2) < WEAK
        fun blue(pixel: Int) = pixels.channel(pixel, 2) > STRONG && pixels.channel(pixel, 0) < WEAK && pixels.channel(pixel, 1) < WEAK
        val row = SIZE / 2
        val squareLeft = SIZE / 2 - SQUARE / 2
        val squareRight = SIZE / 2 + SQUARE / 2
        val rowPixels = (0 until SIZE).map { row * SIZE + it }
        return Row(
            red = (0 until SIZE * SIZE).count(::red),
            blue = (0 until SIZE * SIZE).count(::blue),
            left = rowPixels.count { it % SIZE < squareLeft && red(it) },
            right = rowPixels.count { it % SIZE >= squareRight && red(it) },
            inside = rowPixels.count { it % SIZE in squareLeft + 2 until squareRight - 2 && red(it) },
        )
    }

    private fun ByteArray.channel(pixel: Int, channel: Int): Int = this[pixel * 4 + channel].toInt() and 0xFF

    private data class Row(val red: Int, val blue: Int, val left: Int, val right: Int, val inside: Int)

    private companion object {
        const val SIZE = 128
        const val CUBE = 2f
        const val HALF_HEIGHT = 2f

        /** The cube's face on screen: [CUBE] of the frame's 2 x [HALF_HEIGHT] units. */
        const val SQUARE = (SIZE * CUBE / (2 * HALF_HEIGHT)).toInt()
        const val WIDTH = 4
        const val RING_PIXELS = 100
        const val STRONG = 180
        const val WEAK = 90
        val SELECTED = OutlineStyle(Color(1f, 0f, 0f, 1f), WIDTH.toFloat())
        val HOVERED = OutlineStyle(Color(0f, 0f, 1f, 1f), WIDTH.toFloat())
    }
}
