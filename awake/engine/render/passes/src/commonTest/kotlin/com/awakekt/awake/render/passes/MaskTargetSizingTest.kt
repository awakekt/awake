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

    @Test
    fun rectangularExtentSizesEachAxisIndependentlyForWidescreenScenes() {
        val sizing = MaskTargetSizing()
        // 1093x480 widescreen phone: 1152x512 (2.25x fewer texels than 1152x1152).
        val first = sizing.presentedExtent(MASK_TARGET_MAX_SIZE, MASK_TARGET_MAX_SIZE, 1093, 480)
        assertEquals(1152, maskExtentWidth(first))
        assertEquals(512, maskExtentHeight(first))

        // Half render scale (547x240 -> needed 576x256): width shrinks (1152 > 576+256), height stays 512 (within 256 slack).
        val half = sizing.presentedExtent(1152, 512, 547, 240)
        assertEquals(576, maskExtentWidth(half))
        assertEquals(512, maskExtentHeight(half))

        // From 1088 height (1080p) to half scale 240p: past 256 slack, so height shrinks all the way to 256.
        val shrunkHeight = sizing.presentedExtent(1920, 1088, 547, 240)
        assertEquals(576, maskExtentWidth(shrunkHeight))
        assertEquals(256, maskExtentHeight(shrunkHeight))

        // Small height jitter stays within slack, never reallocating.
        val jitter = sizing.presentedExtent(1152, 512, 1100, 460)
        assertEquals(1152, maskExtentWidth(jitter))
        assertEquals(512, maskExtentHeight(jitter))

        // Offscreen view grows only the axes that need it and settles across frames.
        var curW = 576
        var curH = 256
        repeat(3) {
            val off = sizing.offscreenExtent(curW, curH, 256, 512)
            curW = maskExtentWidth(off)
            curH = maskExtentHeight(off)
            assertEquals(576, curW)
            assertEquals(512, curH)
            val pres = sizing.presentedExtent(curW, curH, 547, 240)
            curW = maskExtentWidth(pres)
            curH = maskExtentHeight(pres)
            assertEquals(576, curW)
            assertEquals(512, curH)
        }
        // Once the offscreen view stops, height shrinks back to 256 (diff 256 = slack, test 768 -> 256 past slack).
        val shrunk = sizing.presentedExtent(576, 832, 547, 240)
        assertEquals(576, maskExtentWidth(shrunk))
        assertEquals(256, maskExtentHeight(shrunk))
    }
}
