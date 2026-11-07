/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.trail

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrailStripTest {
    private val format = TrailStrip.FORMAT
    private val stride = format.strideFloats
    private val position = format.floatOffsetOf(VertexSemantic.Position)
    private val color = format.floatOffsetOf(VertexSemantic.Color)
    private val uv = format.floatOffsetOf(VertexSemantic.Uv)

    @Test
    fun aPairIsTwoVerticesWithPointColourAndUvAcross() {
        val strip = TrailStrip(4)
        strip.set(2, Vec3f(1f, 2f, 3f), Vec3f(4f, 5f, 6f), Color(1f, 0f, 0f, 1f), Color(0f, 0f, 1f, 0.5f))

        val a = 2 * TrailStrip.FLOATS_PER_PAIR
        val b = a + stride
        assertEquals(listOf(1f, 2f, 3f), strip.vertices.slice(a + position..a + position + 2))
        assertEquals(listOf(4f, 5f, 6f), strip.vertices.slice(b + position..b + position + 2))
        assertEquals(listOf(1f, 0f, 0f, 1f), strip.vertices.slice(a + color..a + color + 3))
        assertEquals(listOf(0f, 0f, 1f, 0.5f), strip.vertices.slice(b + color..b + color + 3))
        // u spreads once over the capacity, v is 0 at a and 1 at b.
        assertEquals(listOf(2f / 3f, 0f), strip.vertices.slice(a + uv..a + uv + 1))
        assertEquals(listOf(2f / 3f, 1f), strip.vertices.slice(b + uv..b + uv + 1))
    }

    @Test
    fun changesAreTrackedAsOneRangeUntilUploaded() {
        val strip = TrailStrip(10)
        assertFalse(strip.hasChanges)

        strip.setColor(6, 1f, 1f, 1f, 1f)
        strip.setPoints(3, Vec3f(0f, 0f, 0f), Vec3f(0f, 1f, 0f))
        assertTrue(strip.hasChanges)
        assertEquals(3, strip.firstChanged)
        assertEquals(7, strip.changedEnd)

        strip.markUploaded()
        assertFalse(strip.hasChanges)

        strip.markAllChanged()
        assertEquals(0, strip.firstChanged)
        assertEquals(10, strip.changedEnd)
    }

    @Test
    fun changingVisibleChangesNoPair() {
        val strip = TrailStrip(8)
        strip.visible = 5
        strip.visible = 2
        assertFalse(strip.hasChanges)
        assertFailsWith<IllegalArgumentException> { strip.visible = 9 }
        assertFailsWith<IllegalArgumentException> { strip.visible = -1 }
    }

    @Test
    fun boundsCoverOnlyTheVisiblePairs() {
        val strip = TrailStrip(4)
        strip.set(0, Vec3f(0f, 0f, 0f), Vec3f(0f, 1f, 0f), Color.White, Color.White)
        strip.set(1, Vec3f(1f, 0f, -1f), Vec3f(1f, 1f, -1f), Color.White, Color.White)
        strip.set(2, Vec3f(9f, 9f, 9f), Vec3f(9f, 9f, 9f), Color.White, Color.White)
        val min = Vec3f(0f, 0f, 0f)
        val max = Vec3f(0f, 0f, 0f)

        strip.visible = 1
        assertFalse(strip.visibleBounds(min, max), "one pair draws nothing")

        strip.visible = 2
        assertTrue(strip.visibleBounds(min, max))
        assertEquals(Vec3f(0f, 0f, -1f), min)
        assertEquals(Vec3f(1f, 1f, 0f), max)

        strip.visible = 3
        strip.visibleBounds(min, max)
        assertEquals(Vec3f(9f, 9f, 9f), max)

        // A moved point stales the cached box.
        strip.setPoints(2, Vec3f(-5f, 0f, 0f), Vec3f(-5f, 0f, 0f))
        strip.visibleBounds(min, max)
        assertEquals(-5f, min.x)
    }

    @Test
    fun aPairOutsideTheCapacityIsRefused() {
        val strip = TrailStrip(2)
        assertFailsWith<IndexOutOfBoundsException> { strip.setColor(2, 0f, 0f, 0f, 0f) }
        assertFailsWith<IllegalArgumentException> { TrailStrip(1) }
    }

    @Test
    fun theRendererForwardsToItsStrip() {
        val trail = TrailRenderer(capacity = 3, blend = TrailBlend.Alpha)
        trail.set(1, Vec3f(0f, 0f, 0f), Vec3f(0f, 1f, 0f), Color.White, Color.White)
        trail.visible = 2
        assertEquals(3, trail.capacity)
        assertEquals(2, trail.strip.visible)
        assertEquals(1, trail.strip.firstChanged)
    }
}
