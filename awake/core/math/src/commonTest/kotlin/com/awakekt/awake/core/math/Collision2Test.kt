/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 2D box and circle overlap, and the smallest move that separates them. Touching counts as overlapping.
 * Beyond hand-worked cases these check the two properties that make a penetration usable: moving by it
 * really separates the shapes, and asking from the other shape's side gives the opposite answer.
 */
class Collision2Test {
    private val into = Overlap2()

    // --- box and box

    @Test
    fun twoBoxesThatShareAreaOverlap() {
        assertTrue(Box2(0f, 0f, 1f, 1f).overlaps(Box2(1.5f, 0.5f, 1f, 1f)))
    }

    @Test
    fun twoBoxesApartDoNotOverlapOnEitherAxis() {
        assertFalse(Box2(0f, 0f, 1f, 1f).overlaps(Box2(3f, 0f, 1f, 1f)), "apart in x")
        assertFalse(Box2(0f, 0f, 1f, 1f).overlaps(Box2(0f, 3f, 1f, 1f)), "apart in y")
    }

    @Test
    fun twoBoxesSharingAnEdgeOverlap() {
        assertTrue(Box2(0f, 0f, 1f, 1f).overlaps(Box2(2f, 0f, 1f, 1f)))
        assertTrue(Box2(0f, 0f, 1f, 1f).overlaps(Box2(2f, 2f, 1f, 1f)), "touching at a corner")
    }

    @Test
    fun aBoxInsideAnotherOverlaps() {
        assertTrue(Box2(0f, 0f, 5f, 5f).overlaps(Box2(1f, 1f, 0.5f, 0.5f)))
    }

    @Test
    fun boxesSeparateAlongTheAxisThatNeedsTheLeastMove() {
        // Overlapping 0.5 in x and 1.5 in y: move along x, away from the other box.
        assertTrue(Box2(1.5f, 0f, 1f, 1f).penetration(Box2(0f, 0f, 1f, 1f), into))
        assertEquals(1f, into.normalX)
        assertEquals(0f, into.normalY)
        assertEquals(0.5f, into.depth, EPS)

        // The same pair from the left box's side: the opposite way, the same distance.
        assertTrue(Box2(0f, 0f, 1f, 1f).penetration(Box2(1.5f, 0f, 1f, 1f), into))
        assertEquals(-1f, into.normalX)
        assertEquals(0.5f, into.depth, EPS)
    }

    @Test
    fun aBoxStandingOnAnotherIsPushedUp() {
        // The ground's top is at y = 0. A character box 2 tall centred at 0.9 reaches down to -0.1: 0.1 in.
        val character = Box2(0f, 0.9f, 1f, 1f)
        val ground = Box2(0f, -1f, 10f, 1f)

        assertTrue(character.penetration(ground, into))
        assertEquals(0f, into.normalX)
        assertEquals(1f, into.normalY)
        assertEquals(0.1f, into.depth, EPS)
    }

    @Test
    fun boxesAtTheSameCentreSeparateAlongPlusX() {
        assertTrue(Box2(0f, 0f, 1f, 1f).penetration(Box2(0f, 0f, 1f, 1f), into))

        assertEquals(1f, into.normalX)
        assertEquals(0f, into.normalY)
        assertEquals(2f, into.depth, EPS)
    }

    @Test
    fun touchingBoxesPenetrateByZero() {
        assertTrue(Box2(2f, 0f, 1f, 1f).penetration(Box2(0f, 0f, 1f, 1f), into))

        assertEquals(0f, into.depth, EPS)
    }

    @Test
    fun boxesApartLeaveTheResultAlone() {
        into.set(0.25f, 0.5f, 0.75f)

        assertFalse(Box2(0f, 0f, 1f, 1f).penetration(Box2(5f, 0f, 1f, 1f), into))

        assertEquals(0.25f, into.normalX)
        assertEquals(0.5f, into.normalY)
        assertEquals(0.75f, into.depth)
    }

    // --- circle and circle

    @Test
    fun twoCirclesThatReachEachOtherOverlap() {
        assertTrue(Circle2(0f, 0f, 1f).overlaps(Circle2(1.5f, 0f, 1f)))
        assertFalse(Circle2(0f, 0f, 1f).overlaps(Circle2(2.5f, 0f, 1f)))
    }

    @Test
    fun twoCirclesTouchingAtOnePointOverlap() {
        assertTrue(Circle2(0f, 0f, 1f).overlaps(Circle2(2f, 0f, 1f)))
    }

    @Test
    fun circlesSeparateStraightAwayFromEachOthersCentre() {
        // Centres 2 apart along a 3-4-5 diagonal, radii summing to 2.5: depth 0.5, along (0.6, 0.8).
        assertTrue(Circle2(1.2f, 1.6f, 1f).penetration(Circle2(0f, 0f, 1.5f), into))

        assertEquals(0.6f, into.normalX, EPS)
        assertEquals(0.8f, into.normalY, EPS)
        assertEquals(0.5f, into.depth, EPS)
    }

    @Test
    fun circlesAtTheSameCentreSeparateAlongPlusX() {
        assertTrue(Circle2(1f, 1f, 1f).penetration(Circle2(1f, 1f, 2f), into))

        assertEquals(1f, into.normalX)
        assertEquals(0f, into.normalY)
        assertEquals(3f, into.depth, EPS)
    }

    // --- box and circle

    @Test
    fun aCircleOverlapsABoxThatItReachesFromOutside() {
        val box = Box2(0f, 0f, 1f, 1f)

        assertTrue(box.overlaps(Circle2(1.5f, 0f, 0.6f)), "reaches the right face")
        assertFalse(box.overlaps(Circle2(1.5f, 0f, 0.4f)), "stops short of it")
        assertTrue(Circle2(1.5f, 0f, 0.5f).overlaps(box), "touching the face")
    }

    @Test
    fun aCircleBesideACornerIsMeasuredToTheCornerNotTheEdges() {
        val box = Box2(0f, 0f, 1f, 1f)

        // The corner is at (1, 1). A circle at (1.6, 1.6) is sqrt(0.72) = 0.85 from it.
        assertFalse(box.overlaps(Circle2(1.6f, 1.6f, 0.8f)), "within each axis's reach, but not of the corner")
        assertTrue(box.overlaps(Circle2(1.6f, 1.6f, 0.9f)))
    }

    @Test
    fun aCircleWhoseCentreIsInsideTheBoxOverlaps() {
        assertTrue(Box2(0f, 0f, 2f, 2f).overlaps(Circle2(0.5f, -0.5f, 0.1f)))
    }

    @Test
    fun aBoxIsPushedAwayFromACircleAgainstItsFace() {
        // Circle centred 1.5 to the right of the box's centre, radius 0.6: overlaps the face by 0.1.
        val box = Box2(0f, 0f, 1f, 1f)

        assertTrue(box.penetration(Circle2(1.5f, 0f, 0.6f), into))

        assertEquals(-1f, into.normalX, EPS, "the circle is on the box's right, so the box moves left, away from it")
        assertEquals(0f, into.normalY, EPS)
        assertEquals(0.1f, into.depth, EPS)
    }

    @Test
    fun aCircleIsPushedAwayFromABoxAgainstItsFace() {
        val circle = Circle2(1.5f, 0f, 0.6f)

        assertTrue(circle.penetration(Box2(0f, 0f, 1f, 1f), into))

        assertEquals(1f, into.normalX, EPS, "the circle moves right, away from the box")
        assertEquals(0f, into.normalY, EPS)
        assertEquals(0.1f, into.depth, EPS)
    }

    @Test
    fun aCircleAtACornerIsPushedAlongTheCornersDiagonal() {
        // Corner at (1, 1); circle centre at (1.3, 1.4): 0.5 away along (0.6, 0.8); radius 0.75 overlaps by 0.25.
        assertTrue(Circle2(1.3f, 1.4f, 0.75f).penetration(Box2(0f, 0f, 1f, 1f), into))

        assertEquals(0.6f, into.normalX, EPS)
        assertEquals(0.8f, into.normalY, EPS)
        assertEquals(0.25f, into.depth, EPS)
    }

    @Test
    fun aCircleCentredInsideABoxLeavesByTheNearestFace() {
        // Box x 0..4, y 0..4; circle centred (3.5, 2) radius 0.25: the right face is nearest.
        assertTrue(Circle2(3.5f, 2f, 0.25f).penetration(Box2(2f, 2f, 2f, 2f), into))

        assertEquals(1f, into.normalX, EPS)
        assertEquals(0f, into.normalY, EPS)
        assertEquals(0.75f, into.depth, EPS, "from centre 3.5, to clear the right face at 4 it must reach 4.25")
    }

    @Test
    fun aCircleApartFromABoxLeavesTheResultAlone() {
        into.set(0.5f, 0.5f, 0.5f)

        assertFalse(Circle2(10f, 10f, 1f).penetration(Box2(0f, 0f, 1f, 1f), into))
        assertFalse(Box2(0f, 0f, 1f, 1f).penetration(Circle2(10f, 10f, 1f), into))

        assertEquals(0.5f, into.depth)
    }

    // --- properties

    private val boxes = listOf(Box2(0f, 0f, 1f, 1f), Box2(1.2f, 0.4f, 0.8f, 1.5f), Box2(-0.3f, 1.7f, 1f, 0.5f))
    private val circles = listOf(Circle2(0.2f, 0.3f, 0.9f), Circle2(1.4f, -0.2f, 1.1f), Circle2(-1.5f, 1.2f, 1.5f))

    @Test
    fun movingABoxByItsPenetrationOutOfABoxSeparatesThemAndNoShorterMoveDoes() {
        var checked = 0
        for (a in boxes) {
            for (b in boxes) {
                if (a === b || !a.penetration(b, into)) continue
                assertMove(into, "$a out of $b") { dx, dy -> Box2(a.x + dx, a.y + dy, a.halfWidth, a.halfHeight).overlaps(b) }
                checked++
            }
        }
        assertTrue(checked >= 4, "the fixtures must really overlap, or this proves nothing; only $checked did")
    }

    @Test
    fun movingACircleByItsPenetrationOutOfACircleSeparatesThemAndNoShorterMoveDoes() {
        var checked = 0
        for (a in circles) {
            for (b in circles) {
                if (a === b || !a.penetration(b, into)) continue
                assertMove(into, "$a out of $b") { dx, dy -> Circle2(a.x + dx, a.y + dy, a.radius).overlaps(b) }
                checked++
            }
        }
        assertTrue(checked >= 4, "the fixtures must really overlap, or this proves nothing; only $checked did")
    }

    @Test
    fun movingABoxByItsPenetrationOutOfACircleSeparatesThemAndNoShorterMoveDoes() {
        var checked = 0
        for (a in boxes) {
            for (c in circles) {
                if (!a.penetration(c, into)) continue
                assertMove(into, "$a out of $c") { dx, dy -> Box2(a.x + dx, a.y + dy, a.halfWidth, a.halfHeight).overlaps(c) }
                checked++
            }
        }
        assertTrue(checked >= 4, "the fixtures must really overlap, or this proves nothing; only $checked did")
    }

    @Test
    fun movingACircleByItsPenetrationOutOfABoxSeparatesThemAndNoShorterMoveDoes() {
        var checked = 0
        for (c in circles) {
            for (a in boxes) {
                if (!c.penetration(a, into)) continue
                assertMove(into, "$c out of $a") { dx, dy -> Circle2(c.x + dx, c.y + dy, c.radius).overlaps(a) }
                checked++
            }
        }
        assertTrue(checked >= 4, "the fixtures must really overlap, or this proves nothing; only $checked did")
    }

    @Test
    fun askingFromTheOtherShapesSideGivesTheOppositeNormalAndTheSameDepth() {
        val box = Box2(0.4f, 0.2f, 1f, 0.7f)
        val circle = Circle2(1.1f, 0.5f, 0.8f)
        val fromBox = Overlap2()
        val fromCircle = Overlap2()

        assertTrue(box.penetration(circle, fromBox))
        assertTrue(circle.penetration(box, fromCircle))

        assertEquals(-fromBox.normalX, fromCircle.normalX, EPS)
        assertEquals(-fromBox.normalY, fromCircle.normalY, EPS)
        assertEquals(fromBox.depth, fromCircle.depth, EPS)
    }

    @Test
    fun penetrationAgreesWithOverlapsAndTheNormalIsAUnitVector() {
        val box = Box2(0f, 0f, 1f, 1f)
        for (cx in listOf(-3f, -1.4f, -0.2f, 0.9f, 2.1f, 4f)) {
            for (cy in listOf(-2.5f, -0.7f, 0.3f, 1.8f)) {
                val circle = Circle2(cx, cy, 0.9f)
                val overlapping = box.overlaps(circle)

                assertEquals(overlapping, box.penetration(circle, into), "at ($cx, $cy)")
                if (overlapping) {
                    assertEquals(1f, sqrt(into.normalX * into.normalX + into.normalY * into.normalY), EPS, "unit normal at ($cx, $cy)")
                    assertTrue(into.depth >= 0f)
                }
            }
        }
    }

    // --- the shapes themselves

    @Test
    fun setRewritesAShapeInPlaceAndReturnsIt() {
        val box = Box2()
        val circle = Circle2()

        assertSame(box, box.set(1f, 2f, 3f, 4f))
        assertSame(circle, circle.set(5f, 6f, 7f))

        assertEquals(listOf(1f, 2f, 3f, 4f), listOf(box.x, box.y, box.halfWidth, box.halfHeight))
        assertEquals(listOf(5f, 6f, 7f), listOf(circle.x, circle.y, circle.radius))
    }

    @Test
    fun theDefaultsAreAUnitBoxAndAHalfUnitCircleAtTheOrigin() {
        assertEquals(Box2(0f, 0f, 0.5f, 0.5f).toString(), Box2().toString())
        assertEquals(Circle2(0f, 0f, 0.5f).toString(), Circle2().toString())
    }

    @Test
    fun aNegativeSizeIsRefusedHoweverItIsSet() {
        assertFailsWith<IllegalArgumentException> { Box2(0f, 0f, -1f, 1f) }
        assertFailsWith<IllegalArgumentException> { Box2(0f, 0f, 1f, -1f) }
        assertFailsWith<IllegalArgumentException> { Box2().set(0f, 0f, -1f, 1f) }
        assertFailsWith<IllegalArgumentException> { Box2().halfHeight = -0.1f }
        assertFailsWith<IllegalArgumentException> { Circle2(0f, 0f, -1f) }
        assertFailsWith<IllegalArgumentException> { Circle2().radius = -0.1f }
    }

    @Test
    fun aZeroSizeIsAPoint() {
        assertTrue(Box2(0f, 0f, 0f, 0f).overlaps(Circle2(0f, 0f, 0f)))
        assertTrue(Circle2(1f, 1f, 0f).overlaps(Box2(1f, 1f, 0f, 0f)))
        assertFalse(Circle2(1f, 1f, 0f).overlaps(Box2(2f, 2f, 0.5f, 0.5f)))
    }

    /**
     * Asserts that moving the queried shape by [result]'s normal times a hair more than its depth leaves
     * it clear of the other (so the depth is enough), and that moving by a hair less does not (so it is
     * not more than needed). [overlapsAfterMoving] says whether the moved shape still overlaps the other.
     */
    private fun assertMove(result: Overlap2, what: String, overlapsAfterMoving: (Float, Float) -> Boolean) {
        val past = result.depth + SLACK
        assertFalse(
            overlapsAfterMoving(result.normalX * past, result.normalY * past),
            "$what: moving by the depth must clear it (depth ${result.depth}, normal ${result.normalX}, ${result.normalY})",
        )
        if (result.depth > SLACK) {
            val short = result.depth - SLACK
            assertTrue(
                overlapsAfterMoving(result.normalX * short, result.normalY * short),
                "$what: moving by less than the depth must not clear it",
            )
        }
    }

    private companion object {
        const val EPS = 1e-4f
        const val SLACK = 0.001f
    }
}
