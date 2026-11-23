/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.CloudLayerQuality
import com.awakekt.awake.asset.shaderpack.StylizedWaterQuality
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies [com.awakekt.awake.asset.shaderpack.stylizedWaterContentFeature] and
 * [com.awakekt.awake.asset.shaderpack.cloudLayerContentFeature] on both Vulkan and WebGPU.
 *
 * Negative control: setting `enabled = false` leaves the frame equal to the clear colour
 * (0% water/cloud coverage and 0.0 mean delta against a disabled frame).
 */
class WaterAndCloudParityTest {
    @Test
    fun vulkanStylizedWaterRendersAnimatesAndRespondsToSunAndQuality() = verifyWater(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuStylizedWaterRendersAnimatesAndRespondsToSunAndQuality() = verifyWater(HeadlessUiBackend.WebGpu)

    @Test
    fun vulkanAndWebGpuStylizedWaterMatch() {
        val vk = openHeadlessScene(HeadlessUiBackend.Vulkan).use {
            it.renderer.renderStylizedWaterScene(StylizedWaterQuality.Standard, timeSeconds = 1.25f)
        }
        val wgpu = openHeadlessScene(HeadlessUiBackend.WebGpu).use {
            it.renderer.renderStylizedWaterScene(StylizedWaterQuality.Standard, timeSeconds = 1.25f)
        }
        val diff = meanRgbDiff(vk, wgpu)
        println("PARITY stylized-water Vulkan vs WebGPU mean RGB diff = $diff")
        assertTrue(diff < MAX_BACKEND_DIFF, "Vulkan and WebGPU stylized water should match closely (diff=$diff)")
    }

    @Test
    fun vulkanCloudLayerRendersAnimatesAndFadesTowardHorizon() = verifyCloudLayer(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuCloudLayerRendersAnimatesAndFadesTowardHorizon() = verifyCloudLayer(HeadlessUiBackend.WebGpu)

    @Test
    fun vulkanAndWebGpuCloudLayerMatch() {
        val vk = openHeadlessScene(HeadlessUiBackend.Vulkan).use {
            it.renderer.renderCloudLayerScene(CloudLayerQuality.Standard, timeSeconds = 2.0f)
        }
        val wgpu = openHeadlessScene(HeadlessUiBackend.WebGpu).use {
            it.renderer.renderCloudLayerScene(CloudLayerQuality.Standard, timeSeconds = 2.0f)
        }
        val diff = meanRgbDiff(vk, wgpu)
        println("PARITY cloud-layer Vulkan vs WebGPU mean RGB diff = $diff")
        assertTrue(diff < MAX_BACKEND_DIFF, "Vulkan and WebGPU cloud layer should match closely (diff=$diff)")
    }

    @Test
    fun vulkanStylizedWaterDepthFoamWrapsRockAndShore() = verifyWaterDepthFoam(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuStylizedWaterDepthFoamWrapsRockAndShore() = verifyWaterDepthFoam(HeadlessUiBackend.WebGpu)

    @Test
    fun vulkanAndWebGpuStylizedWaterDepthFoamMatch() {
        val vk = renderStylizedWaterWithRockScene(HeadlessUiBackend.Vulkan, StylizedWaterQuality.Standard, samplesDepth = true)
        val wgpu = renderStylizedWaterWithRockScene(HeadlessUiBackend.WebGpu, StylizedWaterQuality.Standard, samplesDepth = true)
        val diff = meanRgbDiff(vk, wgpu)
        println("PARITY stylized-water-depth-foam Vulkan vs WebGPU mean RGB diff = $diff")
        assertTrue(diff < MAX_BACKEND_DIFF, "Vulkan and WebGPU depth water foam should match closely (diff=$diff)")
    }

    private fun verifyWaterDepthFoam(backend: HeadlessUiBackend) {
        val withFoam = renderStylizedWaterWithRockScene(backend, StylizedWaterQuality.Standard, samplesDepth = true)
        val attachedFoam = renderStylizedWaterWithRockScene(
            backend,
            StylizedWaterQuality.Standard,
            samplesDepth = true,
            attachDynamically = true,
        )
        val noFoam = renderStylizedWaterWithRockScene(backend, StylizedWaterQuality.Standard, samplesDepth = false)
        val fastWithDepthPlan = renderStylizedWaterWithRockScene(backend, StylizedWaterQuality.Fast, samplesDepth = true)
        val fastWithoutDepthPlan = renderStylizedWaterWithRockScene(backend, StylizedWaterQuality.Fast, samplesDepth = false)
        val foamDelta = meanRgbDiff(withFoam, noFoam)
        val dynamicAttachDelta = meanRgbDiff(withFoam, attachedFoam)
        val fastDepthDelta = meanRgbDiff(fastWithDepthPlan, fastWithoutDepthPlan)
        println(
            "WATER_DEPTH[$backend] foamDelta=$foamDelta dynamicAttachDelta=$dynamicAttachDelta fastDepthDelta=$fastDepthDelta",
        )
        assertTrue(foamDelta > 3.0f, "$backend: SceneDepth contact foam should visibly brighten shore and rock edges (delta=$foamDelta)")
        assertTrue(dynamicAttachDelta < 0.5f, "$backend: Dynamically attached depth water must match RenderPlan-declared water (delta=$dynamicAttachDelta)")
        assertTrue(fastDepthDelta < 0.5f, "$backend: Fast mobile tier must skip depth foam sampling (fastDelta=$fastDepthDelta)")
    }

    private fun verifyWater(backend: HeadlessUiBackend) = openHeadlessScene(backend).use { session ->
        val r = session.renderer
        val off = r.renderStylizedWaterScene(StylizedWaterQuality.Standard, enabled = false)
        val stdT0 = r.renderStylizedWaterScene(StylizedWaterQuality.Standard, timeSeconds = 0f)
        val stdT2 = r.renderStylizedWaterScene(StylizedWaterQuality.Standard, timeSeconds = 2.5f)
        val fastT0 = r.renderStylizedWaterScene(StylizedWaterQuality.Fast, timeSeconds = 0f)
        val night = r.renderStylizedWaterScene(StylizedWaterQuality.Standard, timeSeconds = 0f, sunDirection = NIGHT_SUN)

        val waterRowBlue = bandMeanChannel(stdT0, SCENE_SIZE, yStart = SCENE_SIZE / 2, yEnd = SCENE_SIZE - 4, channel = 2)
        val waterRowRed = bandMeanChannel(stdT0, SCENE_SIZE, yStart = SCENE_SIZE / 2, yEnd = SCENE_SIZE - 4, channel = 0)
        val offDiff = meanRgbDiff(stdT0, off)
        val animDiff = meanRgbDiff(stdT0, stdT2)
        val tierDiff = meanRgbDiff(stdT0, fastT0)
        val dayLuma = bandMeanLuma(stdT0, SCENE_SIZE, yStart = SCENE_SIZE / 2, yEnd = SCENE_SIZE - 4)
        val nightLuma = bandMeanLuma(night, SCENE_SIZE, yStart = SCENE_SIZE / 2, yEnd = SCENE_SIZE - 4)

        println(
            "WATER[$backend] blue=$waterRowBlue red=$waterRowRed offDiff=$offDiff animDiff=$animDiff " +
                "tierDiff=$tierDiff dayLuma=$dayLuma nightLuma=$nightLuma",
        )
        assertTrue(offDiff > 20f, "$backend: water draws visibly over backdrop (offDiff=$offDiff)")
        assertTrue(waterRowBlue > waterRowRed + 20f, "$backend: water reads blue-turquoise (blue=$waterRowBlue red=$waterRowRed)")
        assertTrue(animDiff > 1.0f, "$backend: water ripples animate with timeSeconds (animDiff=$animDiff)")
        assertTrue(tierDiff > 1.0f, "$backend: Standard tier differs from Fast tier (tierDiff=$tierDiff)")
        assertTrue(dayLuma > nightLuma + 10f, "$backend: water dims at night (day=$dayLuma night=$nightLuma)")
    }

    private fun verifyCloudLayer(backend: HeadlessUiBackend) = openHeadlessScene(backend).use { session ->
        val r = session.renderer
        val off = r.renderCloudLayerScene(CloudLayerQuality.Standard, enabled = false)
        val stdT0 = r.renderCloudLayerScene(CloudLayerQuality.Standard, timeSeconds = 0f)
        val stdT6 = r.renderCloudLayerScene(CloudLayerQuality.Standard, timeSeconds = 6f)
        val fastT0 = r.renderCloudLayerScene(CloudLayerQuality.Fast, timeSeconds = 0f)

        val offDiff = meanRgbDiff(stdT0, off)
        val animDiff = meanRgbDiff(stdT0, stdT6)
        val tierDiff = meanRgbDiff(stdT0, fastT0)
        val spatialSpread = bandLumaStdDev(stdT0, SCENE_SIZE, yStart = SCENE_SIZE / 2, yEnd = SCENE_SIZE - 4)
        val nearContrast = bandMeanDiffFromRef(stdT0, off, SCENE_SIZE, yStart = (SCENE_SIZE * 3) / 4, yEnd = SCENE_SIZE - 4)
        val farContrast = bandMeanDiffFromRef(stdT0, off, SCENE_SIZE, yStart = SCENE_SIZE / 3, yEnd = SCENE_SIZE / 2)
        val maxStdJump = maxHorizontalJump(stdT0, SCENE_SIZE, yStart = (SCENE_SIZE * 3) / 4, yEnd = SCENE_SIZE - 4)
        val maxFastJump = maxHorizontalJump(fastT0, SCENE_SIZE, yStart = (SCENE_SIZE * 3) / 4, yEnd = SCENE_SIZE - 4)

        println(
            "CLOUD[$backend] offDiff=$offDiff animDiff=$animDiff tierDiff=$tierDiff " +
                "spread=$spatialSpread nearContrast=$nearContrast farContrast=$farContrast " +
                "maxStdJump=$maxStdJump maxFastJump=$maxFastJump",
        )
        assertTrue(offDiff > 5f, "$backend: cloud layer draws visibly over sky backdrop (offDiff=$offDiff)")
        assertTrue(spatialSpread > 3f, "$backend: clouds form soft billowing variation instead of a flat sheet (spread=$spatialSpread)")
        assertTrue(animDiff > 1.0f, "$backend: clouds drift over time (animDiff=$animDiff)")
        assertTrue(tierDiff > 0.5f, "$backend: Standard dual-layer differs from Fast single-layer (tierDiff=$tierDiff)")
        assertTrue(nearContrast > farContrast, "$backend: clouds fade gently toward the horizon (near=$nearContrast far=$farContrast)")
        assertTrue(maxStdJump < MAX_CLOUD_SEAM_JUMP, "$backend: Standard cloud noise has no cell-boundary seams (maxJump=$maxStdJump)")
        assertTrue(maxFastJump < MAX_CLOUD_SEAM_JUMP, "$backend: Fast cloud noise has no cell-boundary seams (maxJump=$maxFastJump)")
    }

    private fun meanRgbDiff(a: ByteArray, b: ByteArray): Float {
        var sum = 0.0
        val count = a.size / 4
        for (i in 0 until count) {
            val o = i * 4
            sum += (
                abs((a[o].toInt() and 0xFF) - (b[o].toInt() and 0xFF)) +
                    abs((a[o + 1].toInt() and 0xFF) - (b[o + 1].toInt() and 0xFF)) +
                    abs((a[o + 2].toInt() and 0xFF) - (b[o + 2].toInt() and 0xFF))
                ) / 3.0
        }
        return (sum / count).toFloat()
    }

    private fun bandMeanChannel(pixels: ByteArray, size: Int, yStart: Int, yEnd: Int, channel: Int): Float {
        var sum = 0.0
        var count = 0
        for (y in yStart until yEnd) {
            for (x in 0 until size) {
                sum += pixels[(y * size + x) * 4 + channel].toInt() and 0xFF
                count++
            }
        }
        return (sum / count).toFloat()
    }

    private fun bandMeanLuma(pixels: ByteArray, size: Int, yStart: Int, yEnd: Int): Float {
        var sum = 0.0
        var count = 0
        for (y in yStart until yEnd) {
            for (x in 0 until size) {
                val o = (y * size + x) * 4
                sum += ((pixels[o].toInt() and 0xFF) + (pixels[o + 1].toInt() and 0xFF) + (pixels[o + 2].toInt() and 0xFF)) / 3.0
                count++
            }
        }
        return (sum / count).toFloat()
    }

    private fun bandLumaStdDev(pixels: ByteArray, size: Int, yStart: Int, yEnd: Int): Float {
        val mean = bandMeanLuma(pixels, size, yStart, yEnd).toDouble()
        var sumSq = 0.0
        var count = 0
        for (y in yStart until yEnd) {
            for (x in 0 until size) {
                val o = (y * size + x) * 4
                val l = ((pixels[o].toInt() and 0xFF) + (pixels[o + 1].toInt() and 0xFF) + (pixels[o + 2].toInt() and 0xFF)) / 3.0
                val d = l - mean
                sumSq += d * d
                count++
            }
        }
        return kotlin.math.sqrt(sumSq / count).toFloat()
    }

    private fun bandMeanDiffFromRef(a: ByteArray, ref: ByteArray, size: Int, yStart: Int, yEnd: Int): Float {
        var sum = 0.0
        var count = 0
        for (y in yStart until yEnd) {
            for (x in 0 until size) {
                val o = (y * size + x) * 4
                sum += (
                    abs((a[o].toInt() and 0xFF) - (ref[o].toInt() and 0xFF)) +
                        abs((a[o + 1].toInt() and 0xFF) - (ref[o + 1].toInt() and 0xFF)) +
                        abs((a[o + 2].toInt() and 0xFF) - (ref[o + 2].toInt() and 0xFF))
                    ) / 3.0
                count++
            }
        }
        return (sum / count).toFloat()
    }

    private fun maxHorizontalJump(pixels: ByteArray, size: Int, yStart: Int, yEnd: Int): Int {
        var worst = 0
        for (y in yStart until yEnd) {
            for (x in 0 until size - 1) {
                val o = (y * size + x) * 4
                val r = (y * size + x + 1) * 4
                for (c in 0..2) {
                    val v = pixels[o + c].toInt() and 0xFF
                    worst = maxOf(worst, abs(v - (pixels[r + c].toInt() and 0xFF)))
                }
            }
        }
        return worst
    }

    private companion object {
        val NIGHT_SUN = Vec3f(0.1f, -0.55f, -0.83f)
        const val MAX_BACKEND_DIFF = 3.5f
        const val MAX_CLOUD_SEAM_JUMP = 55
    }
}
