/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.trail.TrailBlend
import com.awakekt.awake.trail.TrailRenderer
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Trails on Vulkan (lavapipe) and WebGPU (wgpu-native), drawn as [TrailView] draws them: one buffer a
 * trail, rewritten in place from frame to frame over a black background.
 *
 * - **Additive:** a red ribbon at half intensity reads half red, and two over each other add to full
 *   red; two alpha-blended ones at full alpha cover each other and stay half red.
 * - **Reveal:** raising [com.awakekt.awake.trail.TrailRenderer.visible] lights more of the ribbon,
 *   left to right, and nothing past the last visible pair.
 * - **Fade:** colours rewritten from the newest pair back towards black read brightest at the head and
 *   darkest at the tail, after only the changed range was uploaded.
 * - **Parity:** the two backends' frames differ by at most [MAX_PARITY_ERROR] on any channel.
 */
class TrailParityTest {
    @Test
    fun trailsAddRevealAndFadeAlikeOnBothBackends() {
        val frames = HashMap<HeadlessUiBackend, List<ByteArray>>()
        val failures = ArrayList<String>()
        HeadlessUiBackend.entries.forEach { backend ->
            openHeadlessScene(backend, SIZE).use { session ->
                TrailView(session.renderer, SIZE).use { view -> frames[backend] = view.check(backend, failures) }
            }
        }
        val vulkan = frames.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = frames.getValue(HeadlessUiBackend.WebGpu)
        vulkan.indices.forEach { index ->
            val worst = vulkan[index].indices.maxOf { abs(channel(vulkan[index], it) - channel(webGpu[index], it)) }
            println("Trail parity frame $index: largest channel difference $worst")
            if (worst > MAX_PARITY_ERROR) failures += "frame $index: Vulkan and WebGPU differ by $worst on a channel"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /**
     * With depth writes off, the far ribbon's green channel reads 255. With writes on, it reads 0.
     * The back face reads red 255 with culling off and 0 with culling on. The front face reads 255.
     * Both controls produced these values on Vulkan and WebGPU for both blend modes.
     */
    @Test
    fun blendedTrailsWriteDepthAndCullBackFacesOnBothBackends() {
        val failures = ArrayList<String>()
        HeadlessUiBackend.entries.forEach { backend ->
            openHeadlessScene(backend, SIZE).use { session ->
                TrailView(session.renderer, SIZE).use { view ->
                    for (blend in TrailBlend.entries) {
                        view.checkDepthWrite(backend, blend, failures)
                        view.checkBackFaceCulling(backend, blend, failures)
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** One draw submits the near red ribbon first, then the far green ribbon at the same pixels. */
    private fun TrailView.checkDepthWrite(backend: HeadlessUiBackend, blend: TrailBlend, failures: MutableList<String>) {
        val trail = TrailRenderer(4, blend).apply {
            setPair(0, -1f, 0.3f, RED)
            setPair(1, 1f, 0.3f, RED)
            setPair(2, 1f, -0.3f, GREEN)
            setPair(3, -1f, -0.3f, GREEN)
            visible = 4
        }
        val control = frame(listOf(trail))
        trail.depthWrite = true
        val writesDepth = frame(listOf(trail))
        val centerGreen = (CENTRE * SIZE + CENTRE) * RGBA + 1
        println("Trail $backend $blend depth write: green ${channel(control, centerGreen)} -> ${channel(writesDepth, centerGreen)}")
        expect(failures, backend, "$blend depth control green", channel(control, centerGreen), 255, ADD_TOLERANCE)
        expect(failures, backend, "$blend depth write green", channel(writesDepth, centerGreen), 0, ADD_TOLERANCE)
        expect(failures, backend, "$blend depth write red", red(writesDepth, CENTRE, CENTRE), 255, ADD_TOLERANCE)
    }

    private fun TrailView.checkBackFaceCulling(backend: HeadlessUiBackend, blend: TrailBlend, failures: MutableList<String>) {
        val back = straightTrail(2, RED, blend).apply { visible = 2 }
        val front = TrailRenderer(2, blend, doubleSided = false).apply {
            setPair(0, -1f, 0f, RED, halfHeight = -0.2f)
            setPair(1, 1f, 0f, RED, halfHeight = -0.2f)
            visible = 2
        }
        val control = red(frame(listOf(back)), CENTRE, CENTRE)
        back.doubleSided = false
        val culled = red(frame(listOf(back)), CENTRE, CENTRE)
        val facing = red(frame(listOf(front)), CENTRE, CENTRE)
        println("Trail $backend $blend culling: back $control -> $culled, front $facing")
        expect(failures, backend, "$blend double-sided back", control, 255, ADD_TOLERANCE)
        expect(failures, backend, "$blend single-sided back", culled, 0, ADD_TOLERANCE)
        expect(failures, backend, "$blend single-sided front", facing, 255, ADD_TOLERANCE)
    }

    private fun TrailRenderer.setPair(index: Int, x: Float, z: Float, color: Color, halfHeight: Float = 0.2f) {
        set(index, Vec3f(x, -halfHeight, z), Vec3f(x, halfHeight, z), color, color)
    }

    /** Draws the frames in order on one view, checks each, and returns them for the parity check. */
    private fun TrailView.check(backend: HeadlessUiBackend, failures: MutableList<String>): List<ByteArray> {
        val half = Color(0.5f, 0f, 0f, 1f)
        val first = straightTrail(PAIRS, half).apply { visible = PAIRS }
        val second = straightTrail(PAIRS, half).apply { visible = PAIRS }
        val alpha = straightTrail(PAIRS, half, TrailBlend.Alpha).apply { visible = PAIRS }
        val alphaAgain = straightTrail(PAIRS, half, TrailBlend.Alpha).apply { visible = PAIRS }

        val single = frame(listOf(first)).also { save(backend, "single", it) }
        expect(failures, backend, "a half-red additive ribbon over black", red(single, CENTRE, CENTRE), 128, ADD_TOLERANCE)
        expect(failures, backend, "the background", red(single, CENTRE, BACKGROUND_ROW), 0, ADD_TOLERANCE)

        val added = frame(listOf(first, second)).also { save(backend, "added", it) }
        expect(failures, backend, "two additive ribbons over each other", red(added, CENTRE, CENTRE), 255, ADD_TOLERANCE)
        val blended = frame(listOf(alpha, alphaAgain)).also { save(backend, "blended", it) }
        expect(failures, backend, "two opaque alpha-blended ribbons over each other", red(blended, CENTRE, CENTRE), 128, ADD_TOLERANCE)

        val (quarter, threeQuarters) = reveal(first, backend, failures)
        val faded = fade(first, backend, failures)
        return listOf(single, added, blended, quarter, threeQuarters, faded)
    }

    /** Reveals a quarter of [trail], then three quarters, from the left, and checks more is lit. */
    private fun TrailView.reveal(trail: TrailRenderer, backend: HeadlessUiBackend, failures: MutableList<String>): Pair<ByteArray, ByteArray> {
        trail.visible = PAIRS / 4
        val quarter = frame(listOf(trail)).also { save(backend, "quarter", it) }
        trail.visible = PAIRS * 3 / 4
        val threeQuarters = frame(listOf(trail)).also { save(backend, "three-quarters", it) }
        val litQuarter = litColumns(quarter)
        val litThreeQuarters = litColumns(threeQuarters)
        if (litThreeQuarters < litQuarter * 2) failures += "$backend: revealing 3/4 lit $litThreeQuarters columns against $litQuarter at 1/4"
        expect(failures, backend, "past the last visible pair", red(quarter, RIGHT, CENTRE), 0, ADD_TOLERANCE)
        return quarter to threeQuarters
    }

    /** Fades [trail] from its newest pair back to black and checks it reads darkest at the tail. */
    private fun TrailView.fade(trail: TrailRenderer, backend: HeadlessUiBackend, failures: MutableList<String>): ByteArray {
        trail.visible = PAIRS
        val newest = PAIRS - 1
        for (pair in 0..newest) trail.strip.setColor(pair, pair.toFloat() / newest, 0f, 0f, 1f)
        val faded = frame(listOf(trail)).also { save(backend, "faded", it) }
        val tail = red(faded, LEFT, CENTRE)
        val middle = red(faded, CENTRE, CENTRE)
        val head = red(faded, RIGHT, CENTRE)
        println("Trail $backend fade: tail $tail, middle $middle, head $head")
        if (!(tail < middle && middle < head)) failures += "$backend: the fade is not darkest at the tail and brightest at the head: $tail, $middle, $head"
        expect(failures, backend, "the fade's middle", middle, 128, FADE_TOLERANCE)
        return faded
    }

    private fun expect(failures: MutableList<String>, backend: HeadlessUiBackend, what: String, actual: Int, expected: Int, tolerance: Int) {
        if (abs(actual - expected) > tolerance) failures += "$backend: $what reads red $actual, expected $expected"
    }

    /** How many columns along the middle row are lit. */
    private fun litColumns(frame: ByteArray): Int = (0 until SIZE).count { red(frame, it, CENTRE) > LIT }

    private fun red(frame: ByteArray, x: Int, y: Int): Int = channel(frame, (y * SIZE + x) * RGBA)

    private fun channel(frame: ByteArray, index: Int): Int = frame[index].toInt() and 0xFF

    private fun save(backend: HeadlessUiBackend, name: String, frame: ByteArray) =
        PixelMap(SIZE, SIZE, frame).writePng(File("build/reports/render-captures/trail-$backend-$name.png"))

    private companion object {
        val RED = Color(1f, 0f, 0f)
        val GREEN = Color(0f, 1f, 0f)
        const val SIZE = 128
        const val PAIRS = 64
        const val RGBA = 4
        const val CENTRE = SIZE / 2

        // x = -0.8 and 0.8 at 3 m through a 60 degree lens, and a row well above the ribbon.
        const val LEFT = 37
        const val RIGHT = 91
        const val BACKGROUND_ROW = 16
        const val LIT = 16
        const val ADD_TOLERANCE = 3
        const val FADE_TOLERANCE = 12
        const val MAX_PARITY_ERROR = 4
    }
}
