/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.render.pipeline.MultisampleState
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Actual runtime camera targets, not a texture primitive supplied by a fake resolver. Both native
 * backends measure the same premultiplied RGBA values: half-red over the panel is [127,25,34,204],
 * and the empty camera leaves [0,64,51,153] untouched.
 *
 * Negative controls, run separately on both backends and restored:
 * - Restoring SRC_ALPHA for source alpha produces [127,37,50,178], leaking the panel through.
 * - Keeping the opaque camera clear/sky produces [25,51,204,255] in the empty half.
 * - Restoring the black backing alone produces [0,0,0,255] there.
 * - Bypassing textured path clipping produces [127,21,37,204] at the rounded/circle corner,
 *   instead of panel [0,42,73,153], and at the ancestor-clipped corner instead of [0,41,74,153].
 * Each control fails both tests with fresh XML. Opaque defaults, interior pixels and fog over
 * geometry are positive controls; fog must also leave the empty camera background uncovered.
 */
class CanvasViewCoverageParityTest {
    @Test
    fun vulkanCompositesCoverageAndClipsTheCameraPicture() = verify(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuCompositesCoverageAndClipsTheCameraPicture() = verify(HeadlessUiBackend.WebGpu)

    @Test
    fun vulkanKeepsCoverageThroughTheMultisampleResolve() = verifyMultisampled(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuKeepsCoverageThroughTheMultisampleResolve() = verifyMultisampled(HeadlessUiBackend.WebGpu)

    /**
     * A 4x plan rasterizes the camera's target multisampled and resolves it before the canvas
     * samples it. The probes sit away from the plane's edges, so a resolve that keeps coverage
     * reads the same premultiplied values one sample does; one that wrote alpha 1 would turn the
     * empty half opaque.
     */
    private fun verifyMultisampled(backend: HeadlessUiBackend) = runBlocking {
        val captures = captureCanvasViewCoverage(backend, File("build/reports/render-parity"), MultisampleState(count = 4))
        fun pixel(name: String, x: Int, y: Int) = captures.getValue(name).let { data ->
            List(4) { data[(y * VIEW_COVERAGE_SIZE + x) * 4 + it].toInt() and 0xFF }
        }
        val panel = pixel("panel", 128, 80)
        val solid = pixel("solid", 128, 80)
        val half = pixel("transparent", 128, 80)
        val expected = List(4) { channel -> ((solid[channel] + panel[channel]) / 2f).toInt() }
        println("$backend 4x View coverage: panel=$panel solid=$solid half=$half expected=$expected empty=${pixel("transparent", 128, 176)}")
        assertTrue(solid[0] > 240 && solid[3] == 255, "$backend 4x: control, the camera sees the red plane: $solid")
        assertTrue(half.zip(expected).all { (actual, wanted) -> abs(actual - wanted) <= 2 }, "$backend 4x: premultiplied coverage after resolve: $half vs $expected")
        assertEquals(pixel("panel", 128, 176), pixel("transparent", 128, 176), "$backend 4x: the resolve leaves empty camera pixels transparent")
        val opaque = pixel("opaque", 128, 176)
        assertEquals(255, opaque[3], "$backend 4x: default View stays opaque")
        assertTrue(opaque[2] > opaque[0] + 50, "$backend 4x: default View keeps its blue sky: $opaque")
    }

    private fun verify(backend: HeadlessUiBackend) = runBlocking {
        val captures = captureCanvasViewCoverage(backend, File("build/reports/render-parity"))
        fun pixel(name: String, x: Int, y: Int) = captures.getValue(name).let { data ->
            List(4) { data[(y * VIEW_COVERAGE_SIZE + x) * 4 + it].toInt() and 0xFF }
        }
        val panel = pixel("panel", 128, 80)
        val solid = pixel("solid", 128, 80)
        val half = pixel("transparent", 128, 80)
        val expected = List(4) { channel -> ((solid[channel] + panel[channel]) / 2f).toInt() }
        println("$backend View coverage: panel=$panel solid=$solid half=$half expected=$expected")
        println("$backend View empty=${pixel("transparent", 128, 176)} panelEmpty=${pixel("panel", 128, 176)} opaque=${pixel("opaque", 128, 176)}")
        println("$backend View corners: rounded=${pixel("rounded", 36, 36)} circle=${pixel("circle", 36, 36)} panel=${pixel("panel", 36, 36)}")
        println("$backend View ancestor: clipped=${pixel("ancestor", 34, 34)} panel=${pixel("ancestor-panel", 34, 34)} unclipped=${pixel("transparent", 34, 34)}")
        assertTrue(solid[0] > 240 && solid[3] == 255, "$backend: control, the camera sees the red plane: $solid")
        assertTrue(panel[3] in 152..154, "$backend: control, the panel is translucent: $panel")
        assertTrue(half.zip(expected).all { (actual, wanted) -> abs(actual - wanted) <= 2 }, "$backend: premultiplied source-over coverage: $half vs $expected")
        assertEquals(pixel("panel", 128, 176), pixel("transparent", 128, 176), "$backend: empty camera pixels preserve the panel including alpha")
        assertEquals(pixel("panel", 128, 176), pixel("fog", 128, 176), "$backend: depth fog does not fill the empty camera background")
        assertTrue(pixel("fog", 128, 80) != half, "$backend: the registered depth fog still renders over geometry")
        for (mask in listOf("rounded", "circle")) {
            assertEquals(pixel("panel", 36, 36), pixel(mask, 36, 36), "$backend: $mask removes the picture at its corner")
            assertEquals(half, pixel(mask, 128, 80), "$backend: $mask preserves its interior")
        }
        assertTrue(pixel("transparent", 34, 34)[0] > pixel("ancestor-panel", 34, 34)[0] + 100, "$backend: control, the camera fills this corner without the ancestor clip")
        assertEquals(pixel("ancestor-panel", 34, 34), pixel("ancestor", 34, 34), "$backend: the camera respects the ancestor circle")
        assertEquals(half, pixel("ancestor", 128, 80), "$backend: ancestor clip preserves the camera interior")
        val opaque = pixel("opaque", 128, 176)
        assertEquals(255, opaque[3], "$backend: default View stays opaque")
        assertTrue(opaque[2] > opaque[0] + 50, "$backend: default View keeps its blue sky: $opaque")
        assertEquals(pixel("panel", 16, 128), pixel("transparent", 16, 128), "$backend: the camera stays inside its layout box")
    }
}
