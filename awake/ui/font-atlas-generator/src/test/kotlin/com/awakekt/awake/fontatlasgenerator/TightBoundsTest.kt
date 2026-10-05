/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A glyph's measured box must not depend on which JDK measured it: JDK 17's `getBounds2D` boxes a
 * curve's control points and JDK 21's boxes the curve. The arches and the bowl have a control point
 * well outside the curve, so a control-point answer fails each of them; the ramp and the lines cover
 * the rest.
 */
class TightBoundsTest {

    private fun assertBox(expected: Rectangle2D, actual: Rectangle2D) {
        val message = "expected $expected but was $actual"
        assertEquals(expected.x, actual.x, EPS, message)
        assertEquals(expected.y, actual.y, EPS, message)
        assertEquals(expected.width, actual.width, EPS, message)
        assertEquals(expected.height, actual.height, EPS, message)
    }

    @Test
    fun aQuadraticIsBoxedWhereItReachesNotWhereItsControlPointIs() {
        // Control point at y = 10, but the curve peaks at y = 5, halfway up.
        val arch = Path2D.Double().apply {
            moveTo(0.0, 0.0)
            quadTo(5.0, 10.0, 10.0, 0.0)
        }

        assertBox(Rectangle2D.Double(0.0, 0.0, 10.0, 5.0), tightBounds(arch))
    }

    @Test
    fun aCubicIsBoxedWhereItReachesNotWhereItsControlPointsAre() {
        // Control points at y = 10; the curve peaks at 30 * 0.5 * 0.5 = 7.5.
        val arch = Path2D.Double().apply {
            moveTo(0.0, 0.0)
            curveTo(0.0, 10.0, 10.0, 10.0, 10.0, 0.0)
        }

        assertBox(Rectangle2D.Double(0.0, 0.0, 10.0, 7.5), tightBounds(arch))
    }

    /** A quadratic written as a cubic has no cubic term, which takes the linear branch of the extremum. */
    @Test
    fun aCubicThatIsReallyAQuadraticIsBoxedLikeTheQuadratic() {
        val arch = Path2D.Double().apply {
            moveTo(0.0, 0.0)
            curveTo(10.0 / 3, 20.0 / 3, 20.0 / 3, 20.0 / 3, 10.0, 0.0)
        }

        assertBox(Rectangle2D.Double(0.0, 0.0, 10.0, 5.0), tightBounds(arch))
    }

    @Test
    fun aCurveThatNeverTurnsRoundIsBoxedByItsEndPoints() {
        val ramp = Path2D.Double().apply {
            moveTo(0.0, 0.0)
            quadTo(5.0, 1.0, 10.0, 8.0)
        }

        assertBox(Rectangle2D.Double(0.0, 0.0, 10.0, 8.0), tightBounds(ramp))
    }

    @Test
    fun aCurveBulgesLeftAndDownToo() {
        // Mirrors the arch: the extremum on each axis is a minimum, and it is not an end point.
        val bowl = Path2D.Double().apply {
            moveTo(10.0, 10.0)
            quadTo(-10.0, 0.0, 10.0, -10.0)
        }

        assertBox(Rectangle2D.Double(0.0, -10.0, 10.0, 20.0), tightBounds(bowl))
    }

    @Test
    fun straightLinesAreBoxedByTheirPoints() {
        val triangle = Path2D.Double().apply {
            moveTo(1.0, 2.0)
            lineTo(4.0, -3.0)
            lineTo(-2.0, 0.0)
            closePath()
        }

        assertBox(Rectangle2D.Double(-2.0, -3.0, 6.0, 5.0), tightBounds(triangle))
    }

    @Test
    fun anEmptyPathIsAnEmptyBox() {
        assertTrue(tightBounds(Path2D.Double()).isEmpty)
    }

    private companion object {
        const val EPS = 1e-9
    }
}
