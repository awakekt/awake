/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.parity

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The same drawings through Vulkan and WebGPU, in one run, compared to each other.
 *
 * Both backends implement the same `Renderer` and both had headless coverage, but each test lived
 * inside its own backend module -- so nothing ever put the two results side by side, and a WebGPU
 * regression could only be caught by someone who happened to also read the Vulkan picture. Every
 * divergence this repo has found so far (WebGPU shipping without an alpha-blended pipeline, without
 * mesh clustering, with a differently-populated pipeline table) is the kind this notices.
 *
 * Writes every capture to `build/reports/render-parity/`, so a failure is something to look at
 * rather than a number to interpret.
 */
class UiBackendParityTest {

    /**
     * One pass, both backends, every scenario -- captured, checked for ink, then compared.
     *
     * Deliberately one test rather than three. Each backend is opened exactly once and Vulkan goes
     * first: `libwgpu_native` brings its own Vulkan loader into the process, and a Vulkan instance
     * created after that one is loaded fails at `vkCreateInstance` with a missing extension. Split
     * across tests, the second one to run met exactly that.
     */
    @Test
    fun bothBackendsDrawEveryScenarioTheSame() {
        val captures = BACKEND_ORDER.associateWith { backend ->
            openHeadlessUi(backend).use { session ->
                UI_PARITY_SCENARIOS.associate { scenario ->
                    val pixels = session.renderer.render(scenario)
                    write(scenario.name, backend, pixels)
                    // A comparison passes vacuously when both sides drew nothing, which is what a
                    // pipeline that failed to build produces.
                    assertTrue(pixels.anyInk(), "$backend drew nothing for ${scenario.name}")
                    scenario.name to pixels
                }
            }
        }

        val vulkan = captures.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = captures.getValue(HeadlessUiBackend.WebGpu)
        val divergent = UI_PARITY_SCENARIOS.mapNotNull { scenario ->
            disagreement(vulkan.getValue(scenario.name), webGpu.getValue(scenario.name))
                ?.let { "${scenario.name}: $it" }
        }

        assertTrue(
            divergent.isEmpty(),
            "the backends disagree -- see $REPORT_DIR:\n" + divergent.joinToString("\n"),
        )
    }

    /**
     * How two captures of one scenario differ, or null when they agree.
     *
     * **Alpha everywhere, colour only where alpha is full.** Coverage is the claim both backends
     * must meet: it is what the shape *is*, and it is untouched by colour space. Colour is not
     * comparable at partial coverage, because the two targets store it differently -- Vulkan's
     * offscreen target is UNORM and holds premultiplied linear values, while WebGPU's takes its
     * format from the surface, which is sRGB on this host, so the same coverage comes back
     * gamma-encoded. At full alpha the encodings agree and a real colour bug still shows.
     */
    private fun disagreement(vulkan: ByteArray, webGpu: ByteArray): String? {
        var coverageDiffs = 0
        var maxCoverageDiff = 0
        var opaqueColourDiffs = 0
        for (offset in vulkan.indices step 4) {
            fun channel(pixels: ByteArray, index: Int) = pixels[offset + index].toInt() and 0xFF
            val alphaDiff = kotlin.math.abs(channel(vulkan, 3) - channel(webGpu, 3))
            if (alphaDiff > CHANNEL_TOLERANCE) {
                coverageDiffs++
                maxCoverageDiff = maxOf(maxCoverageDiff, alphaDiff)
            }
            val bothOpaque = channel(vulkan, 3) == OPAQUE && channel(webGpu, 3) == OPAQUE
            if (bothOpaque && (0..2).any { kotlin.math.abs(channel(vulkan, it) - channel(webGpu, it)) > CHANNEL_TOLERANCE }) {
                opaqueColourDiffs++
            }
        }
        val pixels = SCENARIO_SIZE * SCENARIO_SIZE
        // Not zero: a driver update may move an edge pixel or two without that being a regression.
        val coverageDiverged = coverageDiffs.toFloat() / pixels > MAX_DIFFERING_FRACTION
        val colourDiverged = opaqueColourDiffs.toFloat() / pixels > MAX_DIFFERING_FRACTION
        return when {
            coverageDiverged -> "$coverageDiffs px differ in coverage (max $maxCoverageDiff)"
            colourDiverged -> "$opaqueColourDiffs opaque px differ in colour"
            else -> null
        }
    }

    private fun ByteArray.anyInk(): Boolean = indices.step(4).any { this[it + 3].toInt() != 0 }

    private fun write(scenario: String, backend: HeadlessUiBackend, pixels: ByteArray) {
        val image = BufferedImage(SCENARIO_SIZE, SCENARIO_SIZE, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until SCENARIO_SIZE) {
            for (x in 0 until SCENARIO_SIZE) {
                val offset = (y * SCENARIO_SIZE + x) * 4
                fun channel(index: Int) = pixels[offset + index].toInt() and 0xFF
                image.setRGB(
                    x,
                    y,
                    (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2),
                )
            }
        }
        val out = File(REPORT_DIR).apply { mkdirs() }
        ImageIO.write(image, "png", File(out, "$scenario-${backend.name.lowercase()}.png"))
    }

    private companion object {
        const val OPAQUE = 255

        /** Vulkan first -- see [bothBackendsDrawEveryScenarioTheSame]. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)

        const val REPORT_DIR = "build/reports/render-parity"

        /** Blending and rasterization legitimately differ by a channel or two between backends. */
        const val CHANNEL_TOLERANCE = 8

        /**
         * Near-zero: the backends currently agree pixel for pixel on these scenarios. Not exactly
         * zero, because a driver update is allowed to move a couple of edge pixels without that
         * being a regression worth failing a build over.
         */
        const val MAX_DIFFERING_FRACTION = 0.01f
    }
}
