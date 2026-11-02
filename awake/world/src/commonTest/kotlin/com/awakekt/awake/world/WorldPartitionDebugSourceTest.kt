/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.world

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRegistry
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.world.WorldPartitionDebugSource.Companion.CELL_LINES
import com.awakekt.awake.world.WorldPartitionDebugSource.Companion.CIRCLE_SEGMENTS
import com.awakekt.awake.world.WorldPartitionDebugSource.Companion.LIFT
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorldPartitionDebugSourceTest {
    private val config = WorldPartitionConfig(cellSize = 10f, loadingRadius = 10f, unloadRadius = 20f)

    /** An observer at (5, 2, 5), in cell (0, 0), with one cell in each state. */
    private fun knownProbe() = WorldPartitionProbe(config).apply {
        begin(Vec3f(5f, 2f, 5f), Vec3f(5f, 2f, 5f))
        addCell(0, 0, WorldCellState.Shown)
        addCell(1, 0, WorldCellState.Reading)
        addCell(-1, -1, WorldCellState.Leaving)
    }

    @Test
    fun itsIdIsCells() {
        assertEquals("cells", WorldPartitionDebugSource(knownProbe()).id)
        assertEquals("cells", WorldPartitionDebugSource.ID)
    }

    @Test
    fun theRowsAreTheObserversCellAndTheCountsOfEachState() {
        val rows = RecordedRows()

        WorldPartitionDebugSource(knownProbe()).rows(rows)

        assertEquals(listOf("Cell" to "0, 0", "Reading" to "1", "Shown" to "1", "Leaving" to "1"), rows.rows)
    }

    @Test
    fun withNoObserverTheCellRowSaysSo() {
        val rows = RecordedRows()

        WorldPartitionDebugSource(WorldPartitionProbe(config)).rows(rows)

        assertEquals("Cell" to "--", rows.rows.first())
    }

    @Test
    fun theCellTextIsKeptUntilTheObserverChangesCell() {
        val probe = knownProbe()
        val source = WorldPartitionDebugSource(probe)
        val first = RecordedRows().also(source::rows).cellText()

        probe.begin(Vec3f(9f, 0f, 1f), Vec3f(9f, 0f, 1f)) // still in cell (0, 0)
        val same = RecordedRows().also(source::rows).cellText()
        probe.begin(Vec3f(-15f, 0f, 25f), Vec3f(-15f, 0f, 25f))
        val moved = RecordedRows().also(source::rows).cellText()

        assertSame(first, same, "an unchanged cell reuses its text")
        assertEquals("-2, 2", moved)
    }

    @Test
    fun theLinesAreEachCellsOutlineThenTheLoadAndUnloadCircles() {
        val lines = RecordedLines()

        WorldPartitionDebugSource(knownProbe()).lines(lines)

        assertEquals(3 * CELL_LINES + 2 * CIRCLE_SEGMENTS, lines.size)
        val y = 2f + LIFT
        // Cell (0, 0) spans x and z 0..10, outlined in four edges around it.
        lines.assertLine(0, Vec3f(0f, y, 0f), Vec3f(10f, y, 0f), WorldPartitionDebugSource.SHOWN)
        lines.assertLine(1, Vec3f(10f, y, 0f), Vec3f(10f, y, 10f), WorldPartitionDebugSource.SHOWN)
        lines.assertLine(2, Vec3f(10f, y, 10f), Vec3f(0f, y, 10f), WorldPartitionDebugSource.SHOWN)
        lines.assertLine(3, Vec3f(0f, y, 10f), Vec3f(0f, y, 0f), WorldPartitionDebugSource.SHOWN)
        // Cell (1, 0) spans x 10..20.
        lines.assertLine(4, Vec3f(10f, y, 0f), Vec3f(20f, y, 0f), WorldPartitionDebugSource.READING)
        // Cell (-1, -1) spans x and z -10..0.
        lines.assertLine(8, Vec3f(-10f, y, -10f), Vec3f(0f, y, -10f), WorldPartitionDebugSource.LEAVING)
        lines.assertLine(11, Vec3f(-10f, y, 0f), Vec3f(-10f, y, -10f), WorldPartitionDebugSource.LEAVING)
    }

    @Test
    fun theCirclesAreCentredOnTheObserverAtEachRadius() {
        val lines = RecordedLines()

        WorldPartitionDebugSource(knownProbe()).lines(lines)

        val circles = lines.drawn.drop(3 * CELL_LINES)
        val load = circles.take(CIRCLE_SEGMENTS)
        val unload = circles.drop(CIRCLE_SEGMENTS)
        assertEquals(CIRCLE_SEGMENTS, load.size)
        assertEquals(CIRCLE_SEGMENTS, unload.size)
        assertTrue(load.all { it.color == WorldPartitionDebugSource.LOAD })
        assertTrue(unload.all { it.color == WorldPartitionDebugSource.UNLOAD })
        for ((radius, circle) in listOf(10f to load, 20f to unload)) {
            for (segment in circle) {
                for (point in listOf(segment.a, segment.b)) {
                    val r = sqrt((point.x - 5f) * (point.x - 5f) + (point.z - 5f) * (point.z - 5f))
                    assertTrue(abs(r - radius) < EPSILON, "a point $point is $r from the observer, not $radius")
                    assertEquals(2f + LIFT, point.y)
                }
            }
            // Closed: the last segment ends where the first began.
            assertTrue(circle.last().b.near(circle.first().a), "the circle closes")
            // Joined: each segment starts where the one before ended.
            for (i in 1 until circle.size) assertTrue(circle[i].a.near(circle[i - 1].b))
        }
    }

    @Test
    fun withNoObserverThereAreNoLines() {
        val lines = RecordedLines()

        WorldPartitionDebugSource(WorldPartitionProbe(config)).lines(lines)

        assertEquals(0, lines.size)
    }

    @Test
    fun underAShiftedOriginTheLinesAreDrawnWhereTheWorldIsDrawn() {
        // The observer is 1000 m east in absolute space, and a floating origin has moved the world 1000 m west.
        val probe = WorldPartitionProbe(config).apply {
            begin(Vec3f(1005f, 0f, 5f), Vec3f(5f, 0f, 5f))
            addCell(100, 0, WorldCellState.Shown)
        }
        val lines = RecordedLines()

        WorldPartitionDebugSource(probe).lines(lines)

        assertEquals(100, probe.observerCellX, "cells are numbered in absolute space")
        lines.assertLine(0, Vec3f(0f, LIFT, 0f), Vec3f(10f, LIFT, 0f), WorldPartitionDebugSource.SHOWN)
        val firstCirclePoint = lines.drawn[CELL_LINES].a
        assertTrue(firstCirclePoint.near(Vec3f(15f, LIFT, 5f)), "circles are around the local observer: $firstCirclePoint")
    }

    @Test
    fun aSourceThatIsOffIsNeverCalled() {
        val counted = CountingSource(WorldPartitionDebugSource(knownProbe()))
        val registry = DebugRegistry(setOf("net"))

        registry.register(counted)
        for (source in registry.active) {
            source.rows(RecordedRows())
            source.lines(RecordedLines())
        }

        assertTrue(registry.active.isEmpty())
        assertEquals(0, counted.calls)
    }

    @Test
    fun theProbeCountsEachStateAndForgetsTheLastUpdatesCells() {
        val probe = knownProbe()
        assertEquals(3, probe.cellCount)
        assertEquals(listOf(1, 1, 1), listOf(probe.reading, probe.shown, probe.leaving))
        assertEquals(WorldCellState.Leaving, probe.stateAt(2))
        assertEquals(-1, probe.cellZAt(2))

        probe.begin(Vec3f(5f, 0f, 5f), Vec3f(5f, 0f, 5f))
        probe.addCell(0, 0, WorldCellState.Shown)

        assertEquals(1, probe.cellCount)
        assertEquals(listOf(0, 1, 0), listOf(probe.reading, probe.shown, probe.leaving))
        assertFailsWith<IndexOutOfBoundsException> { probe.cellXAt(1) }

        probe.lostObserver()
        assertEquals(1, probe.cellCount, "losing the observer keeps the cells the partition keeps")

        probe.reset()
        assertEquals(0, probe.cellCount)
    }

    @Test
    fun theProbeGrowsPastItsFirstArrays() {
        val probe = WorldPartitionProbe(config)
        probe.begin(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f))

        repeat(200) { probe.addCell(it, -it, WorldCellState.Shown) }

        assertEquals(200, probe.cellCount)
        assertEquals(199, probe.cellXAt(199))
        assertEquals(-199, probe.cellZAt(199))
    }

    private class RecordedRows : DebugRows {
        val rows = ArrayList<Pair<String, String>>()
        val values = ArrayList<Any>()

        override fun row(label: String, value: Int, warn: Boolean) {
            rows += label to value.toString()
            values += value
        }

        override fun row(label: String, value: Float, decimals: Int, warn: Boolean) {
            rows += label to value.toString()
            values += value
        }

        override fun row(label: String, value: String, warn: Boolean) {
            rows += label to value
            values += value
        }

        fun cellText(): String = values.first() as String
    }

    private class Line(val a: Vec3f, val b: Vec3f, val color: Color)

    private class RecordedLines : DebugLines {
        val drawn = ArrayList<Line>()
        val size: Int get() = drawn.size

        override fun line(a: Vec3f, b: Vec3f, color: Color) {
            // Copied, as a host does: the source reuses its points.
            drawn += Line(a.copy(), b.copy(), color)
        }

        fun assertLine(index: Int, a: Vec3f, b: Vec3f, color: Color) {
            val line = drawn[index]
            assertTrue(line.a.near(a) && line.b.near(b), "line $index is ${line.a} to ${line.b}, not $a to $b")
            assertEquals(color, line.color, "line $index's colour")
        }
    }

    private class CountingSource(private val source: DebugSource) : DebugSource by source {
        var calls = 0

        override fun rows(out: DebugRows) {
            calls++
            source.rows(out)
        }

        override fun lines(out: DebugLines) {
            calls++
            source.lines(out)
        }
    }

    private companion object {
        const val EPSILON = 1e-3f

        fun Vec3f.near(other: Vec3f): Boolean =
            abs(x - other.x) < EPSILON && abs(y - other.y) < EPSILON && abs(z - other.z) < EPSILON
    }
}
