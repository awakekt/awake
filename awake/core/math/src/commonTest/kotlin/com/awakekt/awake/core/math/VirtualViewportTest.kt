/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import com.awakekt.awake.core.math2d.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VirtualViewportTest {
    @Test
    fun wideTargetHasTheDocumentedExtentsForEveryStrategy() {
        val expected = mapOf(
            ViewportScaling.Fit to listOf(300f, 0f, 1000f, 1000f, 10f, 10f),
            ViewportScaling.Extend to listOf(0f, 0f, 1600f, 1000f, 16f, 10f),
            ViewportScaling.Fill to listOf(0f, 0f, 1600f, 1000f, 10f, 6.25f),
            ViewportScaling.Stretch to listOf(0f, 0f, 1600f, 1000f, 10f, 10f),
            ViewportScaling.Screen to listOf(0f, 0f, 1600f, 1000f, 1600f, 1000f),
        )
        expected.forEach { (strategy, values) ->
            val view = ViewportLayout()
            VirtualViewport(10f, 10f, strategy).resolve(1600f, 1000f, view)
            assertEquals(values, listOf(view.x, view.y, view.width, view.height, view.worldWidth, view.worldHeight), strategy.name)
        }
    }

    @Test
    fun portraitFitCentersBarsAndExtendPreservesMinimumWorldSize() {
        val layout = ViewportLayout()
        VirtualViewport(16f, 9f).resolve(900f, 1600f, layout, 30f, 50f)
        assertEquals(30f, layout.x)
        assertEquals(596.875f, layout.y)
        assertEquals(900f, layout.width)
        assertEquals(506.25f, layout.height)
        VirtualViewport(16f, 9f, ViewportScaling.Extend).resolve(900f, 1600f, layout)
        assertEquals(16f, layout.worldWidth)
        assertEquals(28.444445f, layout.worldHeight, 1e-5f)
    }

    @Test
    fun mappingsRoundTripInEveryStrategyWithOffsetAndRejectBars() {
        val view = ViewportLayout()
        val point = Vec2(0f, 0f)
        ViewportScaling.entries.forEach { strategy ->
            VirtualViewport(16f, 9f, strategy).resolve(900f, 1600f, view, 40f, 20f)
            assertTrue(view.worldToScreen(2f, -1f, point))
            val x = point.x
            val y = point.y
            assertTrue(view.screenToWorld(x, y, point))
            assertEquals(2f, point.x, 1e-4f)
            assertEquals(-1f, point.y, 1e-4f)
            assertFalse(view.contains(view.x - 1f, view.y))
            assertFalse(view.contains(view.x + view.width, view.y))
            assertFalse(view.contains(view.x, view.y + view.height))
            assertFalse(view.contains(Float.NaN, view.y))
        }
        VirtualViewport(16f, 9f).resolve(900f, 1600f, view)
        assertFalse(view.screenToWorld(450f, 0f, point))
    }

    @Test
    fun resizingReusesTheResultAndInvalidTargetsCannotLeaveAStaleMapping() {
        val viewport = VirtualViewport(16f, 9f)
        val view = ViewportLayout()
        val out = Vec2(99f, 99f)
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { bad ->
            viewport.resolve(1600f, 900f, view)
            assertTrue(view.isValid)
            viewport.resolve(bad, 900f, view)
            assertFalse(view.isValid)
            assertFalse(view.screenToWorld(0f, 0f, out))
            assertFalse(view.worldToScreen(0f, 0f, out))
            assertEquals(Vec2(99f, 99f), out)
        }
        assertFailsWith<IllegalArgumentException> { VirtualViewport(Float.NaN, 1f) }
        assertFailsWith<IllegalArgumentException> { VirtualViewport(1f, 0f) }
    }
}
