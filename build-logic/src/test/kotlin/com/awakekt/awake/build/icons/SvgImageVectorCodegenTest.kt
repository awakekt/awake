/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.icons

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SvgImageVectorCodegenTest {

    @Test
    fun valNamesComeFromKebabCaseFilenames() {
        assertEquals("chevronDown", camelCase("chevron-down"))
        assertEquals("arrowDownTray", camelCase("arrow-down-tray"))
        assertEquals("plus", camelCase("plus"))
        assertEquals("square2Stack", camelCase("square-2-stack"))
    }

    @Test
    fun implicitLineToFollowsMoveTo() {
        assertEquals(listOf(op('M', 1, 2), op('L', 3, 4)), parsePathData("M1 2 3 4"))
        assertEquals(listOf(op('M', 1, 2), op('L', 4, 6)), parsePathData("m1 2 3 4"))
    }

    @Test
    fun closeResetsTheCurrentPoint() {
        val commands = parsePathData("M1 1H4V5ZL2 2")
        assertEquals(op('L', 4, 1), commands[1])
        assertEquals(op('L', 4, 5), commands[2])
        assertEquals(op('L', 2, 2), commands[4])
    }

    @Test
    fun concatenatedArcFlagsHitTheExactEndpoint() {
        // Older Heroicons data writes `a.75.75 0 011.06.02`: flags, then a number, with no separators.
        val commands = parsePathData("M5.23 7.21a.75.75 0 011.06.02")
        val end = commands.last().args.takeLast(2)
        assertTrue(abs(end[0] - 6.29) < 1e-9 && abs(end[1] - 7.23) < 1e-9, "$end")
        assertTrue(commands.drop(1).all { it.op == 'C' }, "$commands")

        val spaced = parsePathData("M5.22 8.22a.75.75 0 0 1 1.06 0").last().args.takeLast(2)
        assertTrue(abs(spaced[0] - 6.28) < 1e-9 && abs(spaced[1] - 8.22) < 1e-9, "$spaced")
    }

    @Test
    fun smoothCubicReflectsThePreviousControlPoint() {
        assertEquals(listOf(1.0, -1.0), parsePathData("M0 0C0 1 1 1 1 0S2 -1 2 0")[2].args.take(2))
    }

    @Test
    fun aFullCircleOfArcsStaysOnItsRadius() {
        var px = 0.0
        var py = -1.0
        for (command in parsePathData("M0 -1A1 1 0 1 1 0 1A1 1 0 1 1 0 -1Z").filter { it.op == 'C' }) {
            val (c1x, c1y, c2x, c2y, x, y) = command.args
            val mx = 0.125 * px + 0.375 * c1x + 0.375 * c2x + 0.125 * x
            val my = 0.125 * py + 0.375 * c1y + 0.375 * c2y + 0.125 * y
            assertTrue(abs(hypot(mx, my) - 1.0) < 3e-4, "midpoint ($mx, $my) is off the unit circle")
            px = x
            py = y
        }
    }

    @Test
    fun evenOddHolesNestAndCrossingSubpathsAreRefused() {
        assertNull(classifyFillRule(splitSubpaths(parsePathData("M0 0h1v1h-1Z M2 0h1v1h-1Z")), "evenodd"))
        assertEquals("EvenOdd", classifyFillRule(splitSubpaths(parsePathData("M0 0h10v10h-10Z M2 2h6v6h-6Z")), "evenodd"))
        assertFailsWith<IllegalStateException> {
            classifyFillRule(splitSubpaths(parsePathData("M0 0h6v6h-6Z M3 3h6v6h-6Z")), "evenodd")
        }
    }

    @Test
    fun numbersAreTrimmedAndNeverNegativeZero() {
        assertEquals("4f", fmt(4.0))
        assertEquals("4.8f", fmt(4.80))
        assertEquals("0f", fmt(-0.0))
        assertEquals("0f", fmt(-0.00001))
        assertEquals("1.2346f", fmt(1.23456))
        assertEquals("10f", fmt(10.0))
    }

    @Test
    fun strokeStyleInheritsFromTheRoot() {
        // Heroicons' outline tier declares fill/stroke/width on <svg>; the path carries only cap and join.
        val svg = parseSvg(
            """<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" """ +
                """stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" d="M4 10 L20 10"/></svg>""",
        )
        assertEquals(listOf<SvgShape>(SvgShape.Stroke("M4 10 L20 10", 1.5, "round", "round")), svg.shapes)
    }

    @Test
    fun circlesAndRoundedRectsKeepTheirStroke() {
        val svg = parseSvg(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" """ +
                """stroke-width="2"><circle cx="12" cy="13" r="3"/><rect x="3" y="10" width="18" height="12" rx="2"/></svg>""",
        )
        assertTrue(svg.shapes.all { it is SvgShape.Stroke })
        val (circle, rect) = svg.shapes.map { parsePathData(it.d) }
        assertEquals(op('M', 15, 13), circle.first())
        assertEquals(4, circle.count { it.op == 'C' })
        assertEquals(op('M', 5, 10), rect.first())
        assertEquals(4, rect.count { it.op == 'C' })
    }

    @Test
    fun unsupportedInputIsRefusedNotApproximated() {
        val both = assertFailsWith<IllegalArgumentException> {
            parseSvg("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="black" stroke="red" d="M0 0h1v1Z"/></svg>""")
        }
        assertTrue("fill and a stroke" in both.message.orEmpty(), both.message)
        assertFailsWith<IllegalArgumentException> {
            parseSvg("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path transform="scale(2)" d="M0 0h1v1Z"/></svg>""")
        }
        assertFailsWith<IllegalArgumentException> {
            parseSvg("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><ellipse rx="1" ry="2"/></svg>""")
        }
    }

    @Test
    fun externalEntitiesAreNeverFetched() {
        // A default JDK parser opens this file and fails with FileNotFoundException; a hardened one
        // never touches it, so the parse succeeds.
        val missing = java.io.File(System.getProperty("java.io.tmpdir"), "awake-icon-codegen-missing-${System.nanoTime()}")
        val svg = """<?xml version="1.0"?><!DOCTYPE svg [<!ENTITY x SYSTEM "${missing.toURI()}">]>""" +
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><desc>&x;</desc><path d="M0 0h1v1Z"/></svg>"""
        assertEquals(1, parseSvg(svg).shapes.size)
    }

    private fun op(op: Char, vararg args: Number) = PathOp(op, args.map(Number::toDouble))
}

private operator fun <T> List<T>.component6(): T = this[5]
