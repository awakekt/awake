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
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.NO_MASK_LAYER
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.pipeline.EffectQuality
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
                suspend fun render(maskLayer: Int, exempt: Boolean = false, styled: Boolean = true, bounds: Aabb? = null): Row {
                    renderer.renderToTexture(
                        target,
                        ScenePassCompiler.compile(
                            lens = lens,
                            drawCalls = listOf(RenderDrawCommand(mesh, material, maskLayer = maskLayer, exemptFromCoverings = exempt, worldBounds = bounds)),
                            light = SceneLight(direction = Vec3f(0f, 0f, 1f), color = Vec3f(1f, 1f, 1f)),
                            clipSpace = renderer.clipSpace,
                            aspect = 1f,
                            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
                            maskLayers = if (styled) listOf(SELECTED.packed(), HOVERED.packed()) else emptyList(),
                        ),
                    )
                    return measure(renderer.readPixels(target).data)
                }
                val selected = render(0)
                val hovered = render(1)
                // With the cube's bounds, the overlay draws only around them (#1709): the same ring.
                val half = CUBE / 2
                val scissored = render(0, bounds = Aabb(Vec3f(-half, -half, -half), Vec3f(half, half, half)))
                // Bounds a quarter of the cube's clip its ring: the backend honours the overlay's scissor.
                val clipped = render(0, bounds = Aabb(Vec3f(-half / 4, -half / 4, -half), Vec3f(half / 4, half / 4, half)))
                val unmasked = render(NO_MASK_LAYER)
                val unmaskedDraws = renderer.frameStats?.drawCalls
                // An unlit mesh joins the covering-exempt mask layer whether anything is outlined or not,
                // as a scene's draws do while nothing is outlined: no styles. The overlay must not draw then (#1709).
                val exemptOnly = render(NO_MASK_LAYER, exempt = true, styled = false)
                val exemptDraws = renderer.frameStats?.drawCalls
                println("$backend outline: exempt only=$exemptOnly draws=$exemptDraws against unmasked draws=$unmaskedDraws")
                // The mask follows the scene's size, not a fixed 2048 square of three D32 layers (#1709).
                val maskBytes = renderer.maskTargetBytes
                println("$backend outline: mask target $maskBytes bytes, against $FIXED_MASK_BYTES at the fixed 2048 square")
                assertTrue(maskBytes in 1 until FIXED_MASK_BYTES / 64, "$backend: a $SIZE px scene's mask: $maskBytes bytes")
                println("$backend outline: selected=$selected hovered=$hovered unmasked=$unmasked scissored=$scissored clipped=$clipped")

                assertEquals(0, unmasked.red + unmasked.blue, "$backend: nothing masked draws no outline: $unmasked")
                assertEquals(0, exemptOnly.red + exemptOnly.blue, "$backend: a covering-exempt draw alone draws no outline: $exemptOnly")
                assertTrue(selected.left in WIDTH - 1..WIDTH + 1 && selected.right in WIDTH - 1..WIDTH + 1, "$backend: a ring $WIDTH px wide: $selected")
                assertEquals(selected, scissored, "$backend: scissored to the cube's bounds, the same outline")
                assertTrue(clipped.red < selected.red / 2, "$backend: bounds inside the silhouette leave no ring to draw: $clipped")
                assertEquals(0, selected.inside, "$backend: nothing inside the silhouette: $selected")
                assertTrue(selected.red > RING_PIXELS && selected.blue == 0, "$backend: set 0 in its own colour: $selected")
                assertTrue(hovered.blue > RING_PIXELS && hovered.red == 0, "$backend: set 1 in its own colour: $hovered")

                verifyWideTarget(backend, renderer, mesh, material, lens)
                verifyMultiClusterDisjointScissor(backend, renderer, material, lens)
                verifySoftGlow(backend, renderer, target, mesh, material, lens)
                verifyEffectQualityScaling(backend, renderer, target, mesh, material, lens, unmaskedDraws)
            } finally {
                renderer.waitIdle()
                mesh.destroy()
                material.destroy()
                target.destroy()
            }
        }
    }

    /** Rectangular (256x128) widescreen scene: mask allocates 256x128 rather than 256x256 while preserving the ring (#1709). */
    private suspend fun verifyWideTarget(
        backend: HeadlessUiBackend,
        renderer: Renderer,
        mesh: com.awakekt.awake.render.mesh.Mesh,
        material: com.awakekt.awake.render.material.Material,
        lens: Lens,
    ) {
        val wideTarget = renderer.createRenderTarget(WIDE_WIDTH, SIZE)
        try {
            renderer.renderToTexture(
                wideTarget,
                ScenePassCompiler.compile(
                    lens = lens,
                    drawCalls = listOf(RenderDrawCommand(mesh, material, maskLayer = 0)),
                    light = SceneLight(direction = Vec3f(0f, 0f, 1f), color = Vec3f(1f, 1f, 1f)),
                    clipSpace = renderer.clipSpace,
                    aspect = WIDE_WIDTH.toFloat() / SIZE.toFloat(),
                    drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
                    maskLayers = listOf(SELECTED.packed(), HOVERED.packed()),
                ),
            )
            val wideRow = measure(renderer.readPixels(wideTarget).data, WIDE_WIDTH, SIZE)
            val wideBytes = renderer.maskTargetBytes
            println("$backend outline (256x128): mask target $wideBytes bytes (square 256x256 would be $SQUARE_WIDE_BYTES), row=$wideRow")
            assertTrue(
                wideBytes in RECT_WIDE_BYTES until SQUARE_WIDE_BYTES,
                "$backend: 256x128 scene must allocate a rectangular mask target ($RECT_WIDE_BYTES..<$SQUARE_WIDE_BYTES), got $wideBytes",
            )
            assertTrue(
                wideRow.left in WIDTH - 1..WIDTH + 1 && wideRow.right in WIDTH - 1..WIDTH + 1,
                "$backend: 256x128 ring must stay $WIDTH px wide: $wideRow",
            )
            assertEquals(0, wideRow.inside, "$backend: 256x128 nothing inside silhouette: $wideRow")
            assertTrue(wideRow.red > RING_PIXELS && wideRow.blue == 0, "$backend: 256x128 ring in red: $wideRow")
        } finally {
            renderer.waitIdle()
            wideTarget.destroy()
        }
    }

    /** Two distant bounded meshes render pixel-identically under disjoint per-cluster scissors vs full-screen. */
    private suspend fun verifyMultiClusterDisjointScissor(
        backend: HeadlessUiBackend,
        renderer: Renderer,
        material: com.awakekt.awake.render.material.Material,
        lens: Lens,
    ) {
        val wideTarget = renderer.createRenderTarget(WIDE_WIDTH, SIZE)
        // Distinct meshes so auto-instancing does not fold them into one combined draw before mask culling.
        val leftCube = renderer.createMesh(generate { cube(size = CLUSTER_CUBE, colored = false) })
        val rightCube = renderer.createMesh(generate { cube(size = CLUSTER_CUBE, colored = false) })
        try {
            val leftModel = Mat4().translate(-CLUSTER_OFFSET_X, 0f, 0f)
            val rightModel = Mat4().translate(CLUSTER_OFFSET_X, 0f, 0f)
            val half = CLUSTER_CUBE / 2f
            val leftBounds = Aabb(Vec3f(-CLUSTER_OFFSET_X - half, -half, -half), Vec3f(-CLUSTER_OFFSET_X + half, half, half))
            val rightBounds = Aabb(Vec3f(CLUSTER_OFFSET_X - half, -half, -half), Vec3f(CLUSTER_OFFSET_X + half, half, half))

            suspend fun renderPair(withBounds: Boolean): Pair<ByteArray, Int> {
                val passInput = ScenePassCompiler.compile(
                    lens = lens,
                    drawCalls = listOf(
                        RenderDrawCommand(leftCube, material, model = leftModel, maskLayer = 0, worldBounds = if (withBounds) leftBounds else null),
                        RenderDrawCommand(rightCube, material, model = rightModel, maskLayer = 0, worldBounds = if (withBounds) rightBounds else null),
                    ),
                    light = SceneLight(direction = Vec3f(0f, 0f, 1f), color = Vec3f(1f, 1f, 1f)),
                    clipSpace = renderer.clipSpace,
                    aspect = WIDE_WIDTH.toFloat() / SIZE.toFloat(),
                    drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
                    maskLayers = listOf(SELECTED.packed(), HOVERED.packed()),
                )
                renderer.renderToTexture(wideTarget, passInput)
                return renderer.readPixels(wideTarget).data.copyOf() to (passInput.maskPasses.firstOrNull { it.targetLayer == 0 }?.screenBoundRectCount ?: 0)
            }

            val (unboundedPixels, unboundedCount) = renderPair(withBounds = false)
            val (clusteredPixels, clusterCount) = renderPair(withBounds = true)
            assertEquals(0, unboundedCount, "$backend: unbounded pair should not populate cluster rects")
            assertEquals(2, clusterCount, "$backend: bounded pair must populate 2 per-draw cluster rects")
            assertTrue(
                unboundedPixels.contentEquals(clusteredPixels),
                "$backend: disjoint cluster scissors must match unbounded outline pixel-for-pixel",
            )
            val redCount = (0 until WIDE_WIDTH * SIZE).count { p ->
                clusteredPixels.channel(p, 0) > STRONG && clusteredPixels.channel(p, 1) < WEAK && clusteredPixels.channel(p, 2) < WEAK
            }
            println("$backend multi-cluster outline (256x128): clusters=$clusterCount redPixels=$redCount (exact pixel parity)")
            assertTrue(redCount > RING_PIXELS, "$backend: expected both distant cubes outlined, got $redCount red pixels")
        } finally {
            renderer.waitIdle()
            leftCube.destroy()
            rightCube.destroy()
            wideTarget.destroy()
        }
    }

    /** Softness > 0 renders a smooth, monotonically fading screen-space Gaussian glow at [EffectQuality.High] and [EffectQuality.Medium]. */
    private suspend fun verifySoftGlow(
        backend: HeadlessUiBackend,
        renderer: Renderer,
        target: com.awakekt.awake.render.texture.RenderTarget,
        mesh: com.awakekt.awake.render.mesh.Mesh,
        material: com.awakekt.awake.render.material.Material,
        lens: Lens,
    ) {
        val previousQuality = renderer.effectQuality
        try {
            val half = CUBE / 2
            val bounds = Aabb(Vec3f(-half, -half, -half), Vec3f(half, half, half))
            for (quality in listOf(EffectQuality.High, EffectQuality.Medium)) {
                renderer.effectQuality = quality
                renderer.renderToTexture(
                    target,
                    ScenePassCompiler.compile(
                        lens = lens,
                        drawCalls = listOf(RenderDrawCommand(mesh, material, maskLayer = 0, worldBounds = bounds)),
                        light = SceneLight(direction = Vec3f(0f, 0f, 1f), color = Vec3f(1f, 1f, 1f)),
                        clipSpace = renderer.clipSpace,
                        aspect = 1f,
                        drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
                        maskLayers = listOf(SOFT_SELECTED.packed(), HOVERED.packed()),
                    ),
                )
                val pixels = renderer.readPixels(target).data
                val row = SIZE / 2
                val squareLeft = SIZE / 2 - SQUARE / 2
                val nearRed = pixels.channel(row * SIZE + (squareLeft - 1), 0)
                val midRed = pixels.channel(row * SIZE + (squareLeft - 5), 0)
                val farRed = pixels.channel(row * SIZE + (squareLeft - 12), 0)
                val insideRed = pixels.channel(row * SIZE + (squareLeft + 4), 0)
                val insideBlue = pixels.channel(row * SIZE + (squareLeft + 4), 2)
                println("$backend soft outline ($quality): nearRed=$nearRed midRed=$midRed farRed=$farRed inside=($insideRed,$insideBlue)")
                assertTrue(nearRed > STRONG, "$backend ($quality): soft glow near edge must be bright, got $nearRed")
                assertTrue(
                    midRed in (farRed + 20) until (nearRed - 20),
                    "$backend ($quality): soft glow must smoothly fade (far=$farRed < mid=$midRed < near=$nearRed)",
                )
                assertTrue(
                    kotlin.math.abs(insideRed - insideBlue) < 10,
                    "$backend ($quality): soft glow must not bleed into the white silhouette interior: R=$insideRed B=$insideBlue",
                )
            }
        } finally {
            renderer.effectQuality = previousQuality
        }
    }

    /**
     * [EffectQuality.Low] downgrades soft 64-tap glows to crisp 2 px silhouettes and [EffectQuality.Off]
     * skips both outline mask layers and the overlay draw on both Vulkan and WebGPU.
     */
    @Suppress("LongParameterList")
    private suspend fun verifyEffectQualityScaling(
        backend: HeadlessUiBackend,
        renderer: Renderer,
        target: com.awakekt.awake.render.texture.RenderTarget,
        mesh: com.awakekt.awake.render.mesh.Mesh,
        material: com.awakekt.awake.render.material.Material,
        lens: Lens,
        unmaskedDraws: Int?,
    ) {
        val previousQuality = renderer.effectQuality
        val half = CUBE / 2
        val bounds = Aabb(Vec3f(-half, -half, -half), Vec3f(half, half, half))
        try {
            renderer.effectQuality = EffectQuality.Low
            renderer.renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = lens,
                    drawCalls = listOf(RenderDrawCommand(mesh, material, maskLayer = 0, worldBounds = bounds)),
                    light = SceneLight(direction = Vec3f(0f, 0f, 1f), color = Vec3f(1f, 1f, 1f)),
                    clipSpace = renderer.clipSpace,
                    aspect = 1f,
                    drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
                    maskLayers = listOf(SOFT_SELECTED.packed(), HOVERED.packed()),
                ),
            )
            val lowPixels = renderer.readPixels(target).data
            val row = SIZE / 2
            val squareLeft = SIZE / 2 - SQUARE / 2
            val lowNearRed = lowPixels.channel(row * SIZE + (squareLeft - 1), 0)
            val lowMidRed = lowPixels.channel(row * SIZE + (squareLeft - 5), 0)
            val lowInsideRed = lowPixels.channel(row * SIZE + (squareLeft + 4), 0)
            val lowInsideBlue = lowPixels.channel(row * SIZE + (squareLeft + 4), 2)
            println("$backend soft outline (Low): nearRed=$lowNearRed midRed=$lowMidRed inside=($lowInsideRed,$lowInsideBlue)")
            assertTrue(lowNearRed > STRONG, "$backend: Low crisp downgrade must still draw near edge, got $lowNearRed")
            assertTrue(lowMidRed < WEAK, "$backend: Low crisp downgrade must cap soft width to 2 px (5 px out=$lowMidRed)")
            assertTrue(
                kotlin.math.abs(lowInsideRed - lowInsideBlue) < 10,
                "$backend: Low outline must not bleed inside silhouette: R=$lowInsideRed B=$lowInsideBlue",
            )

            renderer.effectQuality = EffectQuality.Off
            renderer.renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = lens,
                    drawCalls = listOf(RenderDrawCommand(mesh, material, maskLayer = 0, worldBounds = bounds)),
                    light = SceneLight(direction = Vec3f(0f, 0f, 1f), color = Vec3f(1f, 1f, 1f)),
                    clipSpace = renderer.clipSpace,
                    aspect = 1f,
                    drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
                    maskLayers = listOf(SOFT_SELECTED.packed(), HOVERED.packed()),
                ),
            )
            val offRow = measure(renderer.readPixels(target).data)
            val offDraws = renderer.frameStats?.drawCalls
            println("$backend outline (Off): row=$offRow draws=$offDraws against unmasked draws=$unmaskedDraws")
            assertEquals(0, offRow.red + offRow.blue, "$backend: EffectQuality.Off must draw no outline: $offRow")
            if (unmaskedDraws != null && offDraws != null) {
                assertEquals(
                    unmaskedDraws,
                    offDraws,
                    "$backend: EffectQuality.Off must skip outline mask and overlay draws ($offDraws vs unmasked $unmaskedDraws)",
                )
            }
        } finally {
            renderer.effectQuality = previousQuality
        }
    }

    private suspend fun open(backend: HeadlessUiBackend): HeadlessRenderSession {
        // The showcase's plan without its fog and sky, plus the outline: the mask pass and its overlay.
        // No scene-depth pass: the outline reads only the mask, which then follows the scene's size. With
        // one, the mask keeps scene depth's size, which fog compares it with (UnlitOverFogParityTest).
        val plan = EngineShowcaseRenderPlan.copy(
            contentFeatures = listOf(outlineContentFeature()),
            maskShaderSet = PackShaderSets.SceneDepth,
            sceneDepthShaderSet = null,
        )
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
    private fun measure(pixels: ByteArray, width: Int = SIZE, height: Int = SIZE): Row {
        fun red(pixel: Int) = pixels.channel(pixel, 0) > STRONG && pixels.channel(pixel, 1) < WEAK && pixels.channel(pixel, 2) < WEAK
        fun blue(pixel: Int) = pixels.channel(pixel, 2) > STRONG && pixels.channel(pixel, 0) < WEAK && pixels.channel(pixel, 1) < WEAK
        val row = height / 2
        val squareLeft = width / 2 - SQUARE / 2
        val squareRight = width / 2 + SQUARE / 2
        val rowPixels = (0 until width).map { row * width + it }
        return Row(
            red = (0 until width * height).count(::red),
            blue = (0 until width * height).count(::blue),
            left = rowPixels.count { it % width < squareLeft && red(it) },
            right = rowPixels.count { it % width >= squareRight && red(it) },
            inside = rowPixels.count { it % width in squareLeft + 2 until squareRight - 2 && red(it) },
        )
    }

    private fun ByteArray.channel(pixel: Int, channel: Int): Int = this[pixel * 4 + channel].toInt() and 0xFF

    private data class Row(val red: Int, val blue: Int, val left: Int, val right: Int, val inside: Int)

    private companion object {
        const val SIZE = 128
        const val WIDE_WIDTH = 256
        const val CUBE = 2f
        const val CLUSTER_CUBE = 1f
        const val CLUSTER_OFFSET_X = 2.5f
        const val HALF_HEIGHT = 2f

        /** The cube's face on screen: [CUBE] of the frame's 2 x [HALF_HEIGHT] units. */
        const val SQUARE = (SIZE * CUBE / (2 * HALF_HEIGHT)).toInt()
        const val WIDTH = 4

        /** Three D32 layers at the 2048 edge every mask target had. */
        const val FIXED_MASK_BYTES = 2048L * 2048L * 3L * 4L

        /** Three D32 layers at 256x128 rectangular vs 256x256 square. */
        const val RECT_WIDE_BYTES = WIDE_WIDTH.toLong() * SIZE.toLong() * 3L * 4L
        const val SQUARE_WIDE_BYTES = WIDE_WIDTH.toLong() * WIDE_WIDTH.toLong() * 3L * 4L
        const val RING_PIXELS = 100
        const val STRONG = 180
        const val WEAK = 90
        val SELECTED = OutlineStyle(Color(1f, 0f, 0f, 1f), WIDTH.toFloat())
        val SOFT_SELECTED = OutlineStyle(Color(1f, 0f, 0f, 1f), widthPixels = 10f, softness = 1f)
        val HOVERED = OutlineStyle(Color(0f, 0f, 1f, 1f), WIDTH.toFloat())
    }
}
