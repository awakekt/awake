/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackdropDirtyRectsTest {
    @Test
    fun twoFarApartRectanglesDoNotCoverTheSpaceBetween() {
        val dirty = BackdropDirtyRects()
        dirty.add(0f, 0f, 10f, 10f)
        dirty.add(100f, 100f, 110f, 110f)

        assertTrue(dirty.touches(5f, 5f, 6f, 6f))
        assertTrue(dirty.touches(105f, 105f, 106f, 106f))
        assertFalse(dirty.touches(50f, 50f, 60f, 60f), "a bounding box would have covered this")
    }

    @Test
    fun overlappingRectanglesMergeAndABridgeJoinsTwoGroups() {
        val dirty = BackdropDirtyRects()
        dirty.add(0f, 0f, 10f, 10f)
        dirty.add(20f, 0f, 30f, 10f)
        dirty.add(8f, 0f, 22f, 10f)

        assertTrue(dirty.touches(14f, 4f, 15f, 5f), "the bridge and both ends form one rectangle")
    }

    @Test
    fun pastItsCapacityItCollapsesToOneBoxAndNeverForgetsPaint() {
        val dirty = BackdropDirtyRects()
        for (i in 0 until 20) dirty.add(i * 100f, 0f, i * 100f + 10f, 10f)

        for (i in 0 until 20) assertTrue(dirty.touches(i * 100f + 1f, 1f, i * 100f + 2f, 2f))
    }

    @Test
    fun clearForgetsEverything() {
        val dirty = BackdropDirtyRects()
        dirty.add(0f, 0f, 10f, 10f)
        dirty.clear()

        assertFalse(dirty.touches(0f, 0f, 10f, 10f))
    }
}
