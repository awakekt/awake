/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.pipeline.GpuDeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RenderQualityPolicyTest {

    private fun sceneShortEdge(quality: RenderQuality, width: Int, height: Int, device: GpuDeviceClass): Int {
        val scale = RenderQualityPolicy.renderScale(quality, width, height, device)
        return RenderScale.sceneExtent(minOf(width, height), scale)
    }

    @Test
    fun a1080pDesktopAtDpr1StaysAtFullScale() {
        for (device in GpuDeviceClass.entries.filter { it != GpuDeviceClass.Software }) {
            assertEquals(1f, RenderQualityPolicy.renderScale(RenderQuality.Auto, 1920, 1080, device), "$device")
        }
        assertEquals(1f, RenderQualityPolicy.renderScale(RenderQuality.High, 1920, 1080, GpuDeviceClass.Discrete))
    }

    @Test
    fun a4kRetinaDesktopAtDpr2IsCappedToItsDeviceBudget() {
        // A 1920 by 1080 CSS-pixel window at dpr 2 presents 3840 by 2160 physical pixels.
        assertEquals(1440, sceneShortEdge(RenderQuality.Auto, 3840, 2160, GpuDeviceClass.Discrete))
        assertEquals(1080, sceneShortEdge(RenderQuality.Auto, 3840, 2160, GpuDeviceClass.Integrated))
        assertEquals(1080, sceneShortEdge(RenderQuality.Auto, 3840, 2160, GpuDeviceClass.Unknown))
        assertEquals(2160, sceneShortEdge(RenderQuality.High, 3840, 2160, GpuDeviceClass.Discrete))
    }

    @Test
    fun aHighDpiPhoneRendersFromItsPhysicalPixelsNotItsCssPixels() {
        // 844 by 390 CSS pixels at dpr 3: a 2532 by 1170 physical buffer.
        val scale = RenderQualityPolicy.renderScale(RenderQuality.Auto, 2532, 1170, GpuDeviceClass.Mobile)
        assertEquals(900, RenderScale.sceneExtent(1170, scale))
        assertEquals(1948, RenderScale.sceneExtent(2532, scale))
        // Naive 1 / dpr would have rendered the scene 390 pixels tall.
        assertEquals(true, RenderScale.sceneExtent(1170, scale) > 390)
    }

    @Test
    fun eachExplicitPresetCapsTheShortEdge() {
        assertEquals(720, sceneShortEdge(RenderQuality.Balanced, 3840, 2160, GpuDeviceClass.Discrete))
        assertEquals(540, sceneShortEdge(RenderQuality.Performance, 3840, 2160, GpuDeviceClass.Discrete))
        assertEquals(720, sceneShortEdge(RenderQuality.Balanced, 1170, 2532, GpuDeviceClass.Mobile), "portrait")
        assertEquals(540, sceneShortEdge(RenderQuality.Auto, 1920, 1080, GpuDeviceClass.Software))
    }

    @Test
    fun aPresetNeverUpsamples() {
        assertEquals(1f, RenderQualityPolicy.renderScale(RenderQuality.Performance, 800, 480, GpuDeviceClass.Mobile))
        assertEquals(1f, RenderQualityPolicy.renderScale(RenderQuality.Balanced, 1280, 720, GpuDeviceClass.Discrete))
    }

    @Test
    fun aSurfaceWithNoSizeYetRendersAtFullScale() {
        assertEquals(1f, RenderQualityPolicy.renderScale(RenderQuality.Performance, 0, 0, GpuDeviceClass.Mobile))
    }

    @Test
    fun budgetsAreOverridableData() {
        val budgets = RenderQualityBudgets(balancedShortEdge = 1080)
        val scale = RenderQualityPolicy.renderScale(RenderQuality.Balanced, 3840, 2160, GpuDeviceClass.Discrete, budgets)
        assertEquals(0.5f, scale)
        val noAuto = RenderQualityBudgets(autoShortEdge = emptyMap())
        assertEquals(1f, RenderQualityPolicy.renderScale(RenderQuality.Auto, 3840, 2160, GpuDeviceClass.Mobile, noAuto))
        assertFailsWith<IllegalArgumentException> { RenderQualityBudgets(performanceShortEdge = 0) }
    }

    @Test
    fun effectQualityFollowsThePreset() {
        assertEquals(EffectQuality.High, RenderQualityPolicy.effectQuality(RenderQuality.Auto, GpuDeviceClass.Discrete))
        assertEquals(EffectQuality.Low, RenderQualityPolicy.effectQuality(RenderQuality.Auto, GpuDeviceClass.Mobile))
        assertEquals(EffectQuality.High, RenderQualityPolicy.effectQuality(RenderQuality.High, GpuDeviceClass.Mobile))
        assertEquals(EffectQuality.Medium, RenderQualityPolicy.effectQuality(RenderQuality.Balanced, GpuDeviceClass.Discrete))
        assertEquals(EffectQuality.Low, RenderQualityPolicy.effectQuality(RenderQuality.Performance, GpuDeviceClass.Discrete))
    }
}
