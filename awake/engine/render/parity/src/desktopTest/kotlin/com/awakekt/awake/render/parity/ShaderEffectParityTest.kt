/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderdocument.ShaderDocumentException
import com.awakekt.awake.asset.shaderdocument.ShaderDocuments
import com.awakekt.awake.render.renderer.Renderer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A project's shader documents draw the same on Vulkan and WebGPU.
 *
 * The checks are structural, not pixel-exact, because WebGPU's target is sRGB: the colours are pure
 * channels, which both targets store alike, and each check compares where a colour is, not its value.
 */
class ShaderEffectParityTest {
    private val sky = shaderEffectDocument("sky")

    /** Blue above the horizon and red below: the frame is the right way up, and the same way on both. */
    @Test
    fun theSkyIsTheRightWayUpOnBothBackends() = eachBackend { backend, renderer ->
        val frame = renderer.renderShaderEffectScene(listOf(sky))

        val topBlue = frame.share(rows = 0 until SCENE_SIZE / 4) { blue > red }
        val bottomRed = frame.share(rows = SCENE_SIZE * 3 / 4 until SCENE_SIZE) { red > blue }
        assertTrue(topBlue > MOSTLY, "$backend: only $topBlue of the top rows are the sky's top colour")
        assertTrue(bottomRed > MOSTLY, "$backend: only $bottomRed of the bottom rows are the sky's bottom colour")
    }

    @Test
    fun aPlaneCoversTheSameAreaOnBothBackends() {
        val covered = BACKEND_ORDER.associateWith { backend ->
            openHeadlessScene(backend).use { session ->
                session.renderer.renderShaderEffectScene(listOf(shaderEffectDocument("plane")), view = ShaderEffectView.Above)
                    .count { green > red + DOMINANT && green > blue + DOMINANT }
            }
        }
        println("SHADER PLANE COVERAGE $covered")

        val vulkan = covered.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = covered.getValue(HeadlessUiBackend.WebGpu)
        assertTrue(vulkan > MIN_PLANE_PIXELS, "Vulkan drew $vulkan pixels of the plane; it should fill much of the view.")
        assertTrue(abs(webGpu - vulkan) <= vulkan * COVERAGE_TOLERANCE, "The plane covers $webGpu pixels on WebGPU against $vulkan on Vulkan.")
    }

    /** `fract(time) > 0.5` picks white: the effect reads the clock it is given, on both backends. */
    @Test
    fun twoClockValuesFlipTheColour() = eachBackend { backend, renderer ->
        val clock = shaderEffectDocument("clock")

        val early = renderer.renderShaderEffectScene(listOf(clock), timeSeconds = 0.25f)
        val late = renderer.renderShaderEffectScene(listOf(clock), timeSeconds = 0.75f)

        assertTrue(early.share(rows = 0 until SCENE_SIZE) { red < DARK } > MOSTLY, "$backend: at 0.25 s the clock document should be black")
        assertTrue(late.share(rows = 0 until SCENE_SIZE) { red > BRIGHT } > MOSTLY, "$backend: at 0.75 s the clock document should be white")
    }

    /** A rejected document attaches nothing: the frame is the one the scene draws without it. */
    @Test
    fun aMalformedDocumentLeavesTheFrameAsItWas() {
        val malformed = shaderEffectDocument("malformed")
        assertFailsWith<ShaderDocumentException> { ShaderDocuments.compile(malformed) }

        eachBackend { backend, renderer ->
            val without = renderer.renderShaderEffectScene(listOf(sky))
            val with = renderer.renderShaderEffectScene(listOf(sky, malformed))
            assertContentEquals(without, with, "$backend: the malformed document changed the frame")
        }
    }

    private fun eachBackend(check: (HeadlessUiBackend, Renderer) -> Unit) {
        BACKEND_ORDER.forEach { backend -> openHeadlessScene(backend).use { session -> check(backend, session.renderer) } }
    }

    /** One RGBA pixel's channels, 0 to 255. */
    private class Pixel(val red: Int, val green: Int, val blue: Int)

    private fun ByteArray.pixel(index: Int) = Pixel(
        this[index * 4].toInt() and 0xFF,
        this[index * 4 + 1].toInt() and 0xFF,
        this[index * 4 + 2].toInt() and 0xFF,
    )

    private fun ByteArray.count(test: Pixel.() -> Boolean): Int = (0 until SCENE_SIZE * SCENE_SIZE).count { pixel(it).test() }

    /** The share of the pixels in [rows], 0 to 1, that pass [test]. Row 0 is the top of the frame. */
    private fun ByteArray.share(rows: IntRange, test: Pixel.() -> Boolean): Float {
        val indices = rows.flatMap { row -> (0 until SCENE_SIZE).map { row * SCENE_SIZE + it } }
        return indices.count { pixel(it).test() }.toFloat() / indices.size
    }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        const val MOSTLY = 0.95f
        const val DOMINANT = 64
        const val DARK = 32
        const val BRIGHT = 224
        const val MIN_PLANE_PIXELS = 1_000
        const val COVERAGE_TOLERANCE = 0.05
    }
}
