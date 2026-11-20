/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.render.pipeline.MultisampleState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AntiAliasingTest {
    private val webGpuCounts = setOf(1, 4)
    private val vulkanDesktopCounts = setOf(1, 2, 4, 8)

    @Test
    fun aDeviceOffersTheSingleSampleModesAndTheCountsItRasterizes() {
        assertEquals(
            setOf(AntiAliasing.Off, AntiAliasing.Fxaa, AntiAliasing.Msaa4x),
            AntiAliasing.supported(webGpuCounts),
        )
        assertEquals(AntiAliasing.entries.toSet(), AntiAliasing.supported(vulkanDesktopCounts))
        assertEquals(setOf(AntiAliasing.Off, AntiAliasing.Fxaa), AntiAliasing.supported(emptySet()))
    }

    @Test
    fun anOfferedModeResolvesToItselfWithoutAReport() {
        val reports = mutableListOf<String>()
        AntiAliasing.entries.forEach { mode ->
            assertEquals(mode, AntiAliasing.resolve(mode, vulkanDesktopCounts, reports::add))
        }
        assertTrue(reports.isEmpty(), "An offered mode reported a change: $reports")
    }

    @Test
    fun aMissingModeResolvesToTheNearestCheaperOneTheDeviceOffers() {
        val reports = mutableListOf<String>()

        assertEquals(AntiAliasing.Fxaa, AntiAliasing.resolve(AntiAliasing.Msaa2x, webGpuCounts, reports::add))
        assertEquals(AntiAliasing.Msaa4x, AntiAliasing.resolve(AntiAliasing.Msaa8x, webGpuCounts, reports::add))
        assertEquals(AntiAliasing.Msaa2x, AntiAliasing.resolve(AntiAliasing.Msaa8x, setOf(1, 2), reports::add))
        assertEquals(AntiAliasing.Fxaa, AntiAliasing.resolve(AntiAliasing.Msaa4x, setOf(1), reports::add))

        assertEquals(4, reports.size)
        assertEquals("Anti-aliasing requested=Msaa2x selected=Fxaa (sample counts=[1, 4]).", reports.first())
    }

    @Test
    fun theRasterStateTakesTheModesCountAndKeepsThePlansCoverageWhileMultisampled() {
        val plan = MultisampleState(count = 4, alphaToCoverageEnabled = true)

        assertEquals(MultisampleState(2, alphaToCoverageEnabled = true), AntiAliasing.Msaa2x.rasterState(plan))
        assertEquals(MultisampleState(8, alphaToCoverageEnabled = true), AntiAliasing.Msaa8x.rasterState(plan))
        assertEquals(MultisampleState.Single, AntiAliasing.Off.rasterState(plan))
        assertEquals(MultisampleState.Single, AntiAliasing.Fxaa.rasterState(plan))
        assertEquals(MultisampleState(4), AntiAliasing.Msaa4x.rasterState(MultisampleState.Single))
    }

    @Test
    fun aPlansSampleCountNamesItsMode() {
        assertEquals(AntiAliasing.Off, AntiAliasing.forSampleCount(1))
        assertEquals(AntiAliasing.Msaa2x, AntiAliasing.forSampleCount(2))
        assertEquals(AntiAliasing.Msaa4x, AntiAliasing.forSampleCount(4))
        assertEquals(AntiAliasing.Msaa8x, AntiAliasing.forSampleCount(8))
        assertFailsWith<IllegalArgumentException> { AntiAliasing.forSampleCount(3) }
    }
}
