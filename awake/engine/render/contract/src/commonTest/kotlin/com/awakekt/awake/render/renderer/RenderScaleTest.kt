/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import kotlin.test.Test
import kotlin.test.assertEquals

class RenderScaleTest {
    @Test
    fun fullScaleKeepsTheOutputSizeExactly() {
        assertEquals(1921, RenderScale.sceneExtent(1921, 1f))
        assertEquals(0, RenderScale.sceneExtent(0, 0.5f))
    }

    @Test
    fun aScaleRoundsToTheNearestPixelAndNeverBelowOne() {
        assertEquals(960, RenderScale.sceneExtent(1920, 0.5f))
        assertEquals(1013, RenderScale.sceneExtent(1350, 0.75f))
        assertEquals(1, RenderScale.sceneExtent(3, RenderScale.MIN))
    }

    @Test
    fun outOfRangeScalesAreClamped() {
        assertEquals(RenderScale.MIN, RenderScale.coerce(0f))
        assertEquals(RenderScale.MIN, RenderScale.coerce(-2f))
        assertEquals(RenderScale.FULL, RenderScale.coerce(3f))
        assertEquals(RenderScale.FULL, RenderScale.coerce(Float.NaN))
        assertEquals(RenderScale.FULL, RenderScale.coerce(Float.POSITIVE_INFINITY))
    }

    @Test
    fun aViewportMovesOntoTheSceneTargetByTheSameFractionOfEachAxis() {
        val viewport = RenderViewport(x = 100f, y = 50f, width = 400f, height = 300f)
        assertEquals(
            RenderViewport(x = 50f, y = 25f, width = 200f, height = 150f),
            RenderScale.sceneViewport(viewport, outputWidth = 800, outputHeight = 600, sceneWidth = 400, sceneHeight = 300),
        )
    }
}
