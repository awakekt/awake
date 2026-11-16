/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The shadow array's size, frame by frame (#1698). */
class ShadowArraySizingTest {
    @Test
    fun nothingIsResidentUntilALightCasts() {
        val sizing = ShadowArraySizing()
        repeat(1_000) { assertFalse(sizing.update(neededLayers = 0, mapSize = 2048)) }
        assertTrue(sizing.isPlaceholder)
    }

    @Test
    fun theFirstCastingFrameAllocatesWhatItDrawsAtTheBudgetsSize() {
        val sizing = ShadowArraySizing()
        assertTrue(sizing.update(neededLayers = 3, mapSize = 2048))
        assertEquals(3 to 2048, sizing.layers to sizing.size)
        assertFalse(sizing.update(neededLayers = 3, mapSize = 2048), "the same frame again changes nothing")
    }

    @Test
    fun whileShadowsAreDrawnItOnlyGrows() {
        val sizing = ShadowArraySizing()
        sizing.update(3, 2048)
        assertTrue(sizing.update(10, 2048), "a point light's faces")
        assertFalse(sizing.update(3, 2048), "the point light gone: kept")
        assertEquals(10, sizing.layers)
    }

    @Test
    fun aMapSizeChangeReallocatesAtOnce() {
        val sizing = ShadowArraySizing()
        sizing.update(3, 2048)
        assertTrue(sizing.update(3, 1024))
        assertEquals(3 to 1024, sizing.layers to sizing.size)
    }

    @Test
    fun shadowsSwitchedOffReleaseTheArrayOnlyAfterTheIdleFrames() {
        val sizing = ShadowArraySizing(releaseAfterFrames = 4)
        sizing.update(3, 2048)
        repeat(3) { assertFalse(sizing.update(0, 2048)) }
        assertTrue(sizing.update(0, 2048), "the fourth frame without shadows frees it")
        assertTrue(sizing.isPlaceholder)
        assertFalse(sizing.update(0, 2048))
    }

    @Test
    fun shadowsTogglingFasterThanTheIdleFramesReallocateOnce() {
        val sizing = ShadowArraySizing(releaseAfterFrames = 4)
        var changes = 0
        repeat(100) { frame -> if (sizing.update(if (frame % 6 < 3) 3 else 0, 2048)) changes++ }
        assertEquals(1, changes, "allocated once, never released, never reallocated")
    }
}
