/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.worldtext.WorldText
import com.awakekt.awake.worldtext.WorldTextBatch
import com.awakekt.awake.worldtext.WorldTextStyle
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sign placed in the scene, half a metre an em, at 1 m, 10 m and 50 m and turned 70 degrees away at 10 m,
 * on Vulkan (lavapipe) and WebGPU (wgpu-native) (#1062). Through a 60 degree lens on a 256 pixel frame
 * its em is about 110 pixels at 1 m, 11 at 10 m and 2 at 50 m.
 *
 * Each view is held to the same view drawn at four times the resolution and box-filtered down, the
 * picture the pixel grid would show with no aliasing. Coverage runs from 0 to 1:
 * - **Aliasing and smearing:** the mean difference from that picture over the pixels either inks is
 *   within the view's [View.maxError]: a stair-stepped edge differs from it, and so does a smeared one.
 * - **Weight:** the total ink is within [INK_RATIO] of the picture's, so text neither thins away
 *   nor bleeds out at a distance or at the grazing angle.
 * - **Parity:** the two backends differ by at most [MAX_PARITY_ERROR] on the mean.
 *
 * The tolerances were set against two broken shaders, and each fails at least one view: a hard edge
 * (aliasing: mean error 0.038 at 1 m, 0.22 at 10 m and 0.40 at 70 degrees, no ink at 50 m) and an edge
 * three times too wide (smearing: 0.079 at 1 m, 0.22 and 1.39 times the ink at 10 m, 1.20 times at 70
 * degrees). The shader measured 0.004, 0.133, 0.119 and 0.157, with ink within 4 %.
 */
class WorldTextParityTest {
    /** A view of the sign from [distance] metres, turned [turnDegrees] about its vertical axis, allowed [maxError]. */
    private data class View(val name: String, val distance: Float, val turnDegrees: Float, val maxError: Float)

    private val views = listOf(
        View("1m", 1f, 0f, maxError = 0.02f),
        View("10m", 10f, 0f, maxError = 0.17f),
        View("50m", 50f, 0f, maxError = 0.15f),
        View("10m-70deg", 10f, 70f, maxError = 0.18f),
    )

    @Test
    fun aSignIsSharpAtEveryDistanceAndAngleOnBothBackends() {
        val coverage = HashMap<String, Map<String, FloatArray>>()
        val failures = ArrayList<String>()
        HeadlessUiBackend.entries.forEach { backend ->
            openHeadlessScene(backend, SIZE).use { session ->
                val drawn = HashMap<String, FloatArray>()
                for (view in views) {
                    val picture = session.renderer.sign(view, SIZE)
                    val reference = downsample(session.renderer.sign(view, SIZE * SUPERSAMPLE), SIZE * SUPERSAMPLE, SUPERSAMPLE)
                    val error = meanError(picture, reference)
                    val ratio = picture.sum() / reference.sum()
                    println("WorldText $backend ${view.name}: mean error ${"%.4f".format(error)}, ink ${"%.3f".format(ratio)} of the supersampled")
                    if (reference.sum() <= MIN_INK) failures += "$backend ${view.name}: the sign is not on screen"
                    if (error > view.maxError) failures += "$backend ${view.name}: mean error $error against the supersampled sign"
                    if (abs(ratio - 1f) > INK_RATIO) failures += "$backend ${view.name}: ink $ratio of the supersampled sign"
                    drawn[view.name] = picture
                }
                coverage[backend.name] = drawn
            }
        }
        val (vulkan, webGpu) = coverage.values.toList()
        for (view in views) {
            val error = meanError(vulkan.getValue(view.name), webGpu.getValue(view.name))
            println("WorldText parity ${view.name}: mean difference ${"%.4f".format(error)}")
            if (error > MAX_PARITY_ERROR) failures += "${view.name}: Vulkan and WebGPU differ by $error on the mean"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** The sign seen from [View.distance] metres, as each pixel's ink coverage, 0 to 1, in a [size]-square frame. */
    private fun Renderer.sign(view: View, size: Int): FloatArray {
        val batch = WorldTextBatch(this)
        val target = createRenderTarget(size, size)
        try {
            val text = WorldText("Hg", WorldTextStyle(size = SIGN_EM))
            val radians = view.turnDegrees * PI.toFloat() / 180f
            val placement = Mat4().setTrs(Vec3f(0f, 0f, 0f), Quat(0f, sin(radians / 2f), 0f, cos(radians / 2f)), Vec3f(1f, 1f, 1f))
            val lens = Lens(eye = Vec3f(0f, 0f, view.distance), center = Vec3f.ZERO, fovYRadians = (PI / 3).toFloat(), near = 0.05f, far = 200f)
            val draws = ArrayList<RenderDrawCommand>()
            batch.collect(text, placement, lens, draws)
            val background = render(target, lens, emptyList(), size)
            val image = render(target, lens, draws, size)
            PixelMap(size, size, image).writePng(File("build/reports/render-captures/world-text-${view.name}-$size.png"))
            return FloatArray(size * size) { pixel ->
                val drawnRed = image[pixel * 4].toInt() and 0xFF
                val groundRed = background[pixel * 4].toInt() and 0xFF
                ((drawnRed - groundRed) / (255f - groundRed).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
        } finally {
            target.destroy()
            batch.close()
        }
    }

    private fun Renderer.render(target: RenderTarget, lens: Lens, draws: List<RenderDrawCommand>, size: Int): ByteArray {
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = draws,
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as GpuDrawPreparationSource).gpuDrawPreparer,
            ),
        )
        return runBlocking { readPixels(target) }.data.copyOf(size * size * 4)
    }

    /** [values], [size] square, box-filtered down by [factor] on each axis. */
    private fun downsample(values: FloatArray, size: Int, factor: Int): FloatArray {
        val out = size / factor
        return FloatArray(out * out) { pixel ->
            val x0 = (pixel % out) * factor
            val y0 = (pixel / out) * factor
            var sum = 0f
            for (y in y0 until y0 + factor) for (x in x0 until x0 + factor) sum += values[y * size + x]
            sum / (factor * factor)
        }
    }

    /** The mean difference of [a] and [b] over the pixels either inks. */
    private fun meanError(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        var count = 0
        for (i in a.indices) {
            if (a[i] <= INKED && b[i] <= INKED) continue
            sum += abs(a[i] - b[i])
            count++
        }
        return if (count == 0) 0f else sum / count
    }

    private companion object {
        const val SIZE = 256
        const val SIGN_EM = 0.5f
        const val SUPERSAMPLE = 4
        const val INKED = 0.02f
        const val MIN_INK = 1f
        const val INK_RATIO = 0.10f
        const val MAX_PARITY_ERROR = 0.02f
    }
}
