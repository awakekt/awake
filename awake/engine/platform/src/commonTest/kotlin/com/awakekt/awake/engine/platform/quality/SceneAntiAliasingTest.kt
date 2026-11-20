/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.quality

import com.awakekt.awake.render.pipeline.MultisampleState
import com.awakekt.awake.render.renderer.AntiAliasing
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Both engines turn the requested mode into their scene pass through [SceneAntiAliasing.of] (#1791),
 * so a mode, its fallback and its raster state are the same decision on every backend.
 */
class SceneAntiAliasingTest {
    private val coveragePlan = MultisampleState(count = 4, alphaToCoverageEnabled = true)

    @Test
    fun anOfferedModeKeepsItsCountAndThePlansCoverage() {
        val reports = mutableListOf<String>()
        val scene = SceneAntiAliasing.of(AntiAliasing.Msaa2x, coveragePlan, setOf(1, 2, 4, 8), reports::add)

        assertEquals(AntiAliasing.Msaa2x, scene.mode)
        assertEquals(MultisampleState(2, alphaToCoverageEnabled = true), scene.rasterState)
        assertEquals(AntiAliasing.entries.toSet(), scene.supported)
        assertEquals(emptyList(), reports)
    }

    @Test
    fun aMissingModeFallsBackOnceAndDrawsTheFallbacksSamples() {
        val reports = mutableListOf<String>()
        val webGpu = SceneAntiAliasing.of(AntiAliasing.Msaa8x, coveragePlan, setOf(1, 4), reports::add)
        val noMultisampling = SceneAntiAliasing.of(AntiAliasing.Msaa4x, coveragePlan, setOf(1), reports::add)

        assertEquals(AntiAliasing.Msaa4x, webGpu.mode)
        assertEquals(coveragePlan, webGpu.rasterState)
        assertEquals(AntiAliasing.Fxaa, noMultisampling.mode)
        assertEquals(MultisampleState.Single, noMultisampling.rasterState)
        assertEquals(setOf(AntiAliasing.Off, AntiAliasing.Fxaa), noMultisampling.supported)
        assertEquals(2, reports.size)
    }
}
