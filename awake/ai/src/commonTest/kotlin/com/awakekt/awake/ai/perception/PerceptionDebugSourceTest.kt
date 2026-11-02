/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.ai.perception.PerceptionDebugSource.Companion.ARC_SEGMENTS
import com.awakekt.awake.ai.perception.PerceptionDebugSource.Companion.CIRCLE_SEGMENTS
import com.awakekt.awake.ai.perception.PerceptionDebugSource.Companion.CROSS_LINES
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRegistry
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerceptionDebugSourceTest {
    private val eyeHeight = 1.6f
    private val settings = PerceptionSettings(
        sight = SightSettings(focusRange = 15f, focusAngle = 60f, peripheralRange = 5f, peripheralAngle = 180f, eyeHeight = eyeHeight),
        hearing = HearingSettings(range = 10f),
        updateInterval = 0f,
    )

    /** One perceiver at the origin facing +Z, with [settings]. */
    private fun oneGuard(faceZ: Float? = 1f): Pair<PerceptionScene, Entity> {
        val scene = PerceptionScene()
        val guard = scene.spawn(0f, 0f, faceX = faceZ?.let { 0f }, faceZ = faceZ)
        scene.world.add(guard, Perceiver(FactionId("watch"), settings))
        return scene to guard
    }

    @Test
    fun itsIdIsSight() {
        val (scene, _) = oneGuard()
        assertEquals("sight", PerceptionDebugSource(scene.world, scene).id)
        assertEquals("sight", PerceptionDebugSource.ID)
    }

    @Test
    fun aFacingPerceiverDrawsTwoConesAndItsHearing() {
        val (scene, _) = oneGuard()
        val lines = RecordedLines()

        PerceptionDebugSource(scene.world, scene).lines(lines)

        val cone = 2 + ARC_SEGMENTS
        assertEquals(2 * cone + CIRCLE_SEGMENTS, lines.size)
        val eye = Vec3f(0f, eyeHeight, 0f)
        // The focus cone is 60 degrees: its edges are 30 degrees either side of +Z, 15 m out.
        lines.assertLine(0, eye, Vec3f(-7.5f, eyeHeight, 12.990381f), PerceptionDebugSource.FOCUS)
        lines.assertLine(cone - 1, eye, Vec3f(7.5f, eyeHeight, 12.990381f), PerceptionDebugSource.FOCUS)
        // The peripheral cone is 180 degrees: its edges point along -X and +X, 5 m out.
        lines.assertLine(cone, eye, Vec3f(-5f, eyeHeight, 0f), PerceptionDebugSource.PERIPHERAL)
        lines.assertLine(2 * cone - 1, eye, Vec3f(5f, eyeHeight, 0f), PerceptionDebugSource.PERIPHERAL)
        // Each arc joins its edges at the cone's range, and passes in front of the perceiver.
        for ((start, range) in listOf(0 to 15f, cone to 5f)) {
            val arc = lines.drawn.subList(start + 1, start + 1 + ARC_SEGMENTS)
            assertTrue(arc.first().a.near(lines.drawn[start].b), "the arc starts at the first edge")
            assertTrue(arc.last().b.near(lines.drawn[start + cone - 1].b), "the arc ends at the second edge")
            for (i in 1 until arc.size) assertTrue(arc[i].a.near(arc[i - 1].b), "the arc is joined")
            for (segment in arc) {
                assertEquals(range, segment.b.flatDistance(), EPSILON)
                assertTrue(segment.b.z >= -EPSILON)
            }
        }
        // The hearing circle goes all round at 10 m, in its own colour.
        val hearing = lines.drawn.drop(2 * cone)
        assertTrue(hearing.all { it.color == PerceptionDebugSource.HEARING })
        assertTrue(hearing.all { abs(it.a.flatDistance() - 10f) < EPSILON && it.a.y == eyeHeight })
        assertTrue(hearing.last().b.near(hearing.first().a), "the circle closes")
    }

    @Test
    fun withNoFacingThePeripheralConeIsACircleAndThereIsNoFocusCone() {
        val (scene, _) = oneGuard(faceZ = null)
        val lines = RecordedLines()

        PerceptionDebugSource(scene.world, scene).lines(lines)

        assertEquals(2 * CIRCLE_SEGMENTS, lines.size)
        val peripheral = lines.drawn.take(CIRCLE_SEGMENTS)
        assertTrue(peripheral.all { it.color == PerceptionDebugSource.PERIPHERAL && abs(it.a.flatDistance() - 5f) < EPSILON })
    }

    @Test
    fun aRememberedEntityIsLinkedInOneColourInSightAndGreyOtherwise() {
        val (scene, guard) = oneGuard()
        val seen = scene.world.create()
        val lost = scene.world.create()
        scene.world.get<Perceiver>(guard)!!.memory.restore(
            PerceptionMemorySnapshot(
                entities = listOf(
                    RememberedEntity(seen.packed, 0f, 1f, 8f, 0.0, Relation.Hostile, Sense.Focus, inSight = true),
                    RememberedEntity(lost.packed, 3f, 1f, -4f, 0.0, Relation.Hostile, Sense.Hearing, inSight = false),
                ),
            ),
        )
        val lines = RecordedLines()

        PerceptionDebugSource(scene.world, scene).lines(lines)

        val first = 2 * (2 + ARC_SEGMENTS) + CIRCLE_SEGMENTS
        val each = 1 + CROSS_LINES
        assertEquals(first + 2 * each, lines.size)
        val eye = Vec3f(0f, eyeHeight, 0f)
        lines.assertLine(first, eye, Vec3f(0f, 1f, 8f), PerceptionDebugSource.IN_SIGHT)
        lines.assertLine(first + each, eye, Vec3f(3f, 1f, -4f), PerceptionDebugSource.REMEMBERED)
        assertTrue(lines.drawn.subList(first + 1, first + each).all { it.color == PerceptionDebugSource.IN_SIGHT })
        assertTrue(lines.drawn.drop(first + each + 1).all { it.color == PerceptionDebugSource.REMEMBERED })
    }

    @Test
    fun theRowsCountPerceiversWhatTheySeeRememberAndHear() {
        val (scene, guard) = oneGuard()
        val target = scene.spawn(0f, 6f)
        val behind = scene.spawn(0f, -12f)
        scene.world.add(target, Perceivable(FactionId("thieves")))
        scene.world.add(behind, Perceivable(FactionId("thieves")))
        val system = PerceptionSystem(scene)
        system.hear(SoundEvent(Vec3f(0f, 0f, -9f), source = behind))
        system.update(scene.world, 0.1f)
        val rows = RecordedRows()

        PerceptionDebugSource(scene.world, scene).rows(rows)

        assertTrue(scene.world.get<Perceiver>(guard)!!.memory[target]!!.inSight)
        assertEquals(listOf<Pair<String, Any>>("Perceivers" to 1, "In sight" to 1, "Remembered" to 2, "Heard" to 1), rows.rows)
    }

    @Test
    fun forgettingKeepsTheIndexedMemoryInStep() {
        val (scene, guard) = oneGuard()
        val memory = scene.world.get<Perceiver>(guard)!!.memory
        val a = memory.remember(scene.world.create())
        val b = memory.remember(scene.world.create())
        a.lastSensed = 1.0
        b.lastSensed = 5.0

        memory.forgetOlderThan(2.0)
        assertEquals(listOf(b), memory.inOrder)
        memory.forget(b.entity)
        assertTrue(memory.inOrder.isEmpty())
        memory.remember(scene.world.create())
        memory.clear()
        assertTrue(memory.inOrder.isEmpty())
    }

    @Test
    fun aSourceThatIsOffIsNeverCalled() {
        val (scene, _) = oneGuard()
        val counted = CountingSource(PerceptionDebugSource(scene.world, scene))
        val registry = DebugRegistry(setOf("cells"))

        registry.register(counted)
        for (source in registry.active) {
            source.rows(RecordedRows())
            source.lines(RecordedLines())
        }

        assertTrue(registry.active.isEmpty())
        assertEquals(0, counted.calls)
    }

    @Test
    fun aSourceThatIsOnIsCalled() {
        val (scene, _) = oneGuard()
        val counted = CountingSource(PerceptionDebugSource(scene.world, scene))
        val registry = DebugRegistry(setOf("sight"))

        registry.register(counted)
        for (source in registry.active) {
            source.rows(RecordedRows())
            source.lines(RecordedLines())
        }

        assertEquals(2, counted.calls)
    }

    private class RecordedRows : DebugRows {
        val rows = ArrayList<Pair<String, Any>>()

        override fun row(label: String, value: Int, warn: Boolean) {
            rows += label to value
        }

        override fun row(label: String, value: Float, decimals: Int, warn: Boolean) {
            rows += label to value
        }

        override fun row(label: String, value: String, warn: Boolean) {
            rows += label to value
        }
    }

    private class Line(val a: Vec3f, val b: Vec3f, val color: Color)

    private class RecordedLines : DebugLines {
        val drawn = ArrayList<Line>()
        val size: Int get() = drawn.size

        override fun line(a: Vec3f, b: Vec3f, color: Color) {
            // The source reuses its points, so keep copies.
            drawn += Line(Vec3f(a.x, a.y, a.z), Vec3f(b.x, b.y, b.z), color)
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

        /** The distance from the origin on the ground plane. */
        fun Vec3f.flatDistance(): Float = sqrt(x * x + z * z)
    }
}
