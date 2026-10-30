/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.render.effects.FxaaEffect
import com.awakekt.awake.render.effects.PostProcessingEffect
import com.awakekt.awake.render.effects.PostProcessingStack
import com.awakekt.awake.render.effects.ToneMappingGradeEffect
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * FXAA on a scene whose lit shaders already wrote display-ready colour, on both backends.
 *
 * Every lit shader applies exposure, the tone curve and the gamma 2.2 encode itself, so the colour
 * a post-processing chain snapshots is already final. FXAA on that colour may only touch edges.
 * It used to bring a tone-mapping step with it: a chain with FXAA and nothing else re-applied the
 * curve and the encode to the whole frame (#1007).
 *
 * Measured on the 128-square shadow scene, over the 14 333 pixels away from edges, before the
 * fix: Vulkan max shift 61/255, mean 21.1, 9 461 pixels off by more than 1/255; WebGPU max 50/255,
 * mean 11.3, 9 461 pixels. After: 0/255 on both, while FXAA still changes 135 edge pixels on
 * Vulkan and 129 on WebGPU.
 *
 * A tone-mapping step matches the CPU reference twin within 1/255 on both backends (#1011).
 * WebGPU's used to differ from it by 16/255 with FXAA after it and 17/255 without (17/255 on
 * macOS too): its headless target took the surface's first format, `BGRA8UnormSrgb`, so the GPU
 * encoded every already display-encoded output a second time and decoded it again on sampling.
 * The bytes read back were the sRGB encode of the twin's result for the sRGB decode of the plain
 * frame, which matched within 1/255. With the UNORM counterpart, as on Vulkan: 0/255 on both, and
 * the tone-mapping control above shifts WebGPU's frame exactly as Vulkan's, max 61/255, mean 21.6.
 */
class PostProcessingFxaaParityTest {

    @Test
    fun fxaaAloneOnDisplayReferredColourOnlyTouchesEdgesOnBothBackends() {
        val failures = mutableListOf<String>()
        BACKEND_ORDER.forEach { backend ->
            openHeadlessScene(backend).use { session ->
                val renderer = session.renderer
                renderer.effectQuality = EffectQuality.High
                val plain = renderer.renderLdrScene(effects = null).also { write(backend, it, "post-fxaa-none") }
                val fxaa = renderer.renderLdrScene(listOf(FxaaEffect())).also { write(backend, it, "post-fxaa-only") }

                val edges = edgeMask(plain)
                val shift = shiftAwayFromEdges(plain, fxaa, edges)
                val edgeChanged = changedPixels(plain, fxaa, edges, inside = true)
                println("FXAA only, $backend: $shift; edge pixels changed: $edgeChanged")

                assertTrue(shift.awayPixels > MIN_AWAY_PIXELS, "$backend: only ${shift.awayPixels} pixels lie away from edges")
                assertTrue(edgeChanged > 0, "$backend: FXAA changed no edge pixel, so it did not run")
                if (shift.maxShift > AWAY_TOLERANCE) {
                    failures += "$backend: FXAA alone shifted pixels away from edges: $shift"
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /**
     * The explicit combination renders as before: a listed [ToneMappingGradeEffect] still applies
     * the curve and the encode to whatever the target holds. This is also the negative control for
     * the test above: the same measurement on the same scene reports the shift (max 61/255 on
     * both backends; 50/255 on WebGPU while its target was sRGB) when a tone-mapping step is
     * present.
     *
     * Both backends match the CPU reference twin within 1/255 (#1011); the class comment has the
     * measurements.
     */
    @Test
    fun anExplicitToneMappingStepStillAppliesTheCurveOnBothBackends() {
        val failures = mutableListOf<String>()
        BACKEND_ORDER.forEach { backend ->
            openHeadlessScene(backend).use { session ->
                val renderer = session.renderer
                renderer.effectQuality = EffectQuality.High
                val plain = renderer.renderLdrScene(effects = null)
                val graded = renderer.renderLdrScene(listOf(ToneMappingGradeEffect(), FxaaEffect()))
                    .also { write(backend, it, "post-tonemap-fxaa") }

                val edges = edgeMask(plain)
                val shift = shiftAwayFromEdges(plain, graded, edges)
                println("Tone mapping + FXAA, $backend: $shift")
                assertTrue(shift.maxShift > MIN_CONTROL_SHIFT, "$backend: the curve did not run: $shift")

                val worst = (0 until SCENE_SIZE * SCENE_SIZE).filterNot { edges[it] }.maxOf { pixel ->
                    referenceDifference(plain, graded, pixel)
                }
                println("Tone mapping + FXAA, $backend: worst difference from the reference twin $worst/255")
                if (worst > REFERENCE_TOLERANCE) failures += "$backend: tone mapping drifted from its reference by $worst/255"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /**
     * A tone-mapping step with nothing after it draws into the target, so it reads a copy of the
     * target held in a transient that takes the target's own format (#1010): one more place an
     * sRGB format would decode and encode again. It must match the CPU twin within 1/255 on both
     * backends too (#1011); the class comment has the measurements.
     */
    @Test
    fun aToneMappingStepAloneMatchesTheCpuTwinOnBothBackends() {
        val failures = mutableListOf<String>()
        BACKEND_ORDER.forEach { backend ->
            openHeadlessScene(backend).use { session ->
                val renderer = session.renderer
                renderer.effectQuality = EffectQuality.High
                val plain = renderer.renderLdrScene(effects = null)
                val graded = renderer.renderLdrScene(listOf(ToneMappingGradeEffect()))
                    .also { write(backend, it, "post-tonemap-only") }

                val everywhere = BooleanArray(SCENE_SIZE * SCENE_SIZE)
                val shift = shiftAwayFromEdges(plain, graded, everywhere)
                assertTrue(shift.maxShift > MIN_CONTROL_SHIFT, "$backend: the curve did not run: $shift")

                val worst = (0 until SCENE_SIZE * SCENE_SIZE).maxOf { pixel -> referenceDifference(plain, graded, pixel) }
                println("Tone mapping alone, $backend: worst difference from the reference twin $worst/255")
                if (worst > REFERENCE_TOLERANCE) failures += "$backend: tone mapping drifted from its reference by $worst/255"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** How far [graded]'s [pixel] lies from the CPU twin's tone mapping of [plain]'s, in 8-bit steps. */
    private fun referenceDifference(plain: ByteArray, graded: ByteArray, pixel: Int): Int {
        val offset = pixel * 4
        val input = FloatArray(3) { (plain[offset + it].toInt() and 0xFF) / 255f }
        val expected = PostProcessingStack.evaluateToneMappingGradeReference(input)
        return (0 until 3).maxOf { channel ->
            abs((graded[offset + channel].toInt() and 0xFF) - (expected[channel] * 255f).roundToInt())
        }
    }

    private fun Renderer.renderLdrScene(effects: List<PostProcessingEffect>?): ByteArray {
        val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        val ground = createMesh(plane(GROUND_HALF, y = 0f))
        val caster = createMesh(plane(CASTER_HALF, y = CASTER_Y, r = 1f, g = 0f, b = 0f))
        val material = createMaterial(LitShadowUniformLayout)
        return try {
            val chain = effects?.let { PostProcessingStack.buildChain(it, imagePassTargetContext(target)) } ?: ImagePassChain.Empty
            val input = compileShadowScene(
                listOf(RenderDrawCommand(ground, material), RenderDrawCommand(caster, material)),
            ).copy(postPassChain = chain)
            renderToTexture(target, input)
            runBlocking { readPixels(target) }.data
        } finally {
            ground.destroy()
            caster.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private data class Shift(val awayPixels: Int, val maxShift: Int, val meanShift: Double, val pixelsOverOne: Int) {
        override fun toString(): String =
            "max shift $maxShift/255, mean ${"%.1f".format(meanShift)}, $pixelsOverOne of $awayPixels pixels off by more than 1/255"
    }

    /** Largest and mean per-channel difference over the pixels [edges] leaves out. */
    private fun shiftAwayFromEdges(a: ByteArray, b: ByteArray, edges: BooleanArray): Shift {
        var away = 0
        var worst = 0
        var total = 0L
        var overOne = 0
        for (pixel in 0 until SCENE_SIZE * SCENE_SIZE) {
            if (edges[pixel]) continue
            away += 1
            var pixelWorst = 0
            for (channel in 0 until 3) {
                val delta = abs((a[pixel * 4 + channel].toInt() and 0xFF) - (b[pixel * 4 + channel].toInt() and 0xFF))
                pixelWorst = max(pixelWorst, delta)
            }
            worst = max(worst, pixelWorst)
            total += pixelWorst
            if (pixelWorst > AWAY_TOLERANCE) overOne += 1
        }
        return Shift(away, worst, if (away == 0) 0.0 else total.toDouble() / away, overOne)
    }

    private fun changedPixels(a: ByteArray, b: ByteArray, edges: BooleanArray, inside: Boolean): Int =
        (0 until SCENE_SIZE * SCENE_SIZE).count { pixel ->
            edges[pixel] == inside && (0 until 3).any { channel -> a[pixel * 4 + channel] != b[pixel * 4 + channel] }
        }

    /**
     * Pixels FXAA may touch: any whose 3 x 3 luma range in [pixels] reaches [EDGE_RANGE], grown by
     * [EDGE_GROWTH] pixels, since FXAA samples half a pixel along the edge and its neighbours.
     */
    private fun edgeMask(pixels: ByteArray): BooleanArray {
        fun luma(x: Int, y: Int): Float {
            val offset = (y.coerceIn(0, SCENE_SIZE - 1) * SCENE_SIZE + x.coerceIn(0, SCENE_SIZE - 1)) * 4
            return 0.2126f * (pixels[offset].toInt() and 0xFF) +
                0.7152f * (pixels[offset + 1].toInt() and 0xFF) +
                0.0722f * (pixels[offset + 2].toInt() and 0xFF)
        }
        val seeds = BooleanArray(SCENE_SIZE * SCENE_SIZE) { pixel ->
            val values = neighbourhood(pixel, 1).map { (x, y) -> luma(x, y) }
            values.max() - values.min() >= EDGE_RANGE
        }
        val mask = BooleanArray(SCENE_SIZE * SCENE_SIZE)
        seeds.indices.filter { seeds[it] }.forEach { pixel ->
            neighbourhood(pixel, EDGE_GROWTH)
                .filter { (x, y) -> x in 0 until SCENE_SIZE && y in 0 until SCENE_SIZE }
                .forEach { (x, y) -> mask[y * SCENE_SIZE + x] = true }
        }
        return mask
    }

    /** The (x, y) positions within [radius] of [pixel], including ones off the frame. */
    private fun neighbourhood(pixel: Int, radius: Int): List<Pair<Int, Int>> {
        val x = pixel % SCENE_SIZE
        val y = pixel / SCENE_SIZE
        return (-radius..radius).flatMap { dy -> (-radius..radius).map { dx -> x + dx to y + dy } }
    }

    private fun write(backend: HeadlessUiBackend, pixels: ByteArray, name: String) {
        val image = BufferedImage(SCENE_SIZE, SCENE_SIZE, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until SCENE_SIZE) {
            for (x in 0 until SCENE_SIZE) {
                val offset = (y * SCENE_SIZE + x) * 4
                fun channel(index: Int) = pixels[offset + index].toInt() and 0xFF
                image.setRGB(x, y, (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
            }
        }
        val out = File(REPORT_DIR).apply { mkdirs() }
        ImageIO.write(image, "png", File(out, "$name-${backend.name.lowercase()}.png"))
    }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        const val REPORT_DIR = "build/reports/render-parity"

        /** The issue's bar: away from edges, FXAA alone leaves colour within 1/255. */
        const val AWAY_TOLERANCE = 1

        /**
         * A 3 x 3 luma range, in 8-bit steps, that counts as an edge. FXAA's own threshold is
         * 0.0833 (21 steps); 8 also covers the steepest smooth shading in this scene.
         */
        const val EDGE_RANGE = 8f
        const val EDGE_GROWTH = 2

        /** Most of the 128-square frame must stay comparable, or the check proves nothing. */
        const val MIN_AWAY_PIXELS = SCENE_SIZE * SCENE_SIZE / 2

        /** The curve and encode move the lit ground by tens of steps; less means they did not run. */
        const val MIN_CONTROL_SHIFT = 10

        /** GPU and CPU evaluate pow and the curve in different float orders: one 8-bit step. */
        const val REFERENCE_TOLERANCE = 1
    }
}
