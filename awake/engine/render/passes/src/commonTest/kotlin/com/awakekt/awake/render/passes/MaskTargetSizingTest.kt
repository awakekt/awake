/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import kotlin.test.Test
import kotlin.test.assertEquals

/** The mask target follows the scene's size, not a fixed 2048 square (#1709). */
class MaskTargetSizingTest {
    @Test
    fun aPresentedSceneSizesTheMaskToItsLongerSide() {
        val sizing = MaskTargetSizing()
        assertEquals(1152, sizing.presented(MASK_TARGET_MAX_SIZE, 1093, 480))
        // Half render scale: the scene is drawn at 547x240.
        assertEquals(576, sizing.presented(1152, 547, 240))
        assertEquals(MASK_TARGET_MAX_SIZE, sizing.presented(576, 3840, 2160), "never past the old fixed size")
    }

    @Test
    fun aSmallResizeKeepsTheTarget() {
        val sizing = MaskTargetSizing()
        assertEquals(1152, sizing.presented(1152, 1100, 480))
        assertEquals(1152, sizing.presented(1152, 900, 480), "within the slack")
        assertEquals(704, sizing.presented(1152, 700, 480), "past it")
    }

    @Test
    fun anOffscreenRenderEveryFrameSettlesOnOneSize() {
        val sizing = MaskTargetSizing()
        var edge = sizing.presented(MASK_TARGET_MAX_SIZE, 640, 360)
        assertEquals(640, edge)
        repeat(3) {
            edge = sizing.offscreen(edge, 1920, 1080)
            assertEquals(1920, edge)
            edge = sizing.presented(edge, 640, 360)
            assertEquals(1920, edge, "the presented frame keeps what the offscreen view needs")
        }
        // The offscreen view stops: the next presented frame shrinks.
        edge = sizing.presented(edge, 640, 360)
        assertEquals(640, sizing.presented(edge, 640, 360))
    }
}
