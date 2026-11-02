/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.math.GridOrigin
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhysicsDebugSourceTest {

    /** A static box at the origin, a dynamic sphere sensor 5 m up, and a kinematic capsule, none built yet. */
    private fun knownWorld(): World = World().apply {
        body(BoxShape(Vec3f(1f, 2f, 3f)), Vec3f(0f, 0f, 0f), MotionType.STATIC)
        body(SphereShape(2f), Vec3f(0f, 5f, 0f), MotionType.DYNAMIC, sensor = true)
        body(CapsuleShape(halfHeight = 0.9f, radius = 0.3f), Vec3f(4f, 0f, 0f), MotionType.KINEMATIC)
    }

    private fun World.body(
        shape: PhysicsShape,
        position: Vec3f,
        motionType: MotionType,
        sensor: Boolean = false,
    ): PhysicsBody {
        val entity = create()
        add(entity, Transform(position = position))
        val body = PhysicsBody(shape = shape, motionType = motionType, sensor = sensor)
        add(entity, body)
        return body
    }

    @Test
    fun itsIdIsPhysics() {
        assertEquals("physics", PhysicsDebugSource(World()).id)
        assertEquals("physics", PhysicsDebugSource.ID)
    }

    @Test
    fun theRowsCountBodiesByKindAndWarnOfUnbuiltOnes() {
        val world = knownWorld()
        val rows = RecordedRows()

        PhysicsDebugSource(world).rows(rows)

        assertEquals(
            listOf("Static" to 1, "Dynamic" to 1, "Kinematic" to 1, "Sensors" to 1, "Unbuilt" to 3),
            rows.rows,
        )
        assertEquals(listOf("Unbuilt"), rows.warned)
    }

    @Test
    fun aBuiltBodyIsNoLongerUnbuilt() {
        val world = World()
        world.body(BoxShape(Vec3f(1f, 1f, 1f)), Vec3f(0f, 0f, 0f), MotionType.STATIC).handle = BodyHandle(1L)
        val rows = RecordedRows()

        PhysicsDebugSource(world).rows(rows)

        assertEquals("Unbuilt" to 0, rows.rows.last())
        assertTrue(rows.warned.isEmpty())
    }

    @Test
    fun theLinesAreEachCollidersWireframeInItsMotionTypesColour() {
        val lines = RecordedLines()

        PhysicsDebugSource(knownWorld()).lines(lines)

        val ring = PhysicsDebugWriter.CIRCLE_SEGMENTS
        // A box is twelve edges, a sphere three rings, a capsule two rings, the line between its extremes and four sides.
        assertEquals(BOX_EDGES + 3 * ring + 2 * ring + 1 + 4, lines.drawn.size)
        assertTrue(lines.drawn.take(BOX_EDGES).all { it.color == PhysicsDebugSource.STATIC })
        assertTrue(lines.drawn.drop(BOX_EDGES).take(3 * ring).all { it.color == PhysicsDebugSource.DYNAMIC })
        assertTrue(lines.drawn.drop(BOX_EDGES + 3 * ring).all { it.color == PhysicsDebugSource.KINEMATIC })
        // The box's first edge runs along x, from its (-1, -2, -3) corner.
        val first = lines.drawn.first()
        assertTrue(first.a.near(Vec3f(-1f, -2f, -3f)) && first.b.near(Vec3f(1f, -2f, -3f)), "$first")
    }

    @Test
    fun aRotatedBodyIsDrawnAtTheRotationItsTransformDescribes() {
        val world = World()
        val entity = world.create()
        val euler = Vec3f(0.3f, 0.7f, -0.2f)
        world.add(entity, Transform(position = Vec3f(2f, 3f, 4f), rotation = euler))
        world.add(entity, PhysicsBody(shape = BoxShape(Vec3f(1f, 2f, 3f)), motionType = MotionType.DYNAMIC))
        val lines = RecordedLines()

        PhysicsDebugSource(world).lines(lines)

        // The writer turns the rotation into a quaternion in place; it must be the one Quat.fromEuler makes.
        val rotation = Quat.fromEuler(euler)
        val corner = Vec3f(2f, 3f, 4f) + rotation.rotate(Vec3f(-1f, -2f, -3f))
        val along = Vec3f(2f, 3f, 4f) + rotation.rotate(Vec3f(1f, -2f, -3f))
        val first = lines.drawn.first()
        assertTrue(first.a.near(corner) && first.b.near(along), "$first, not $corner to $along")
        assertEquals(physicsDebugLines(world).map { it.start }, lines.drawn.map { it.a })
    }

    @Test
    fun aRotatedCornerHeightfieldIsTurnedAboutItsBodyLikeTheCollider() {
        // The backend turns a heightfield by its body's rotation about the body position, which for a
        // corner field is its first sample -- not the middle of the field.
        val world = World()
        val entity = world.create()
        val euler = Vec3f(0f, 0.6f, 0f)
        world.add(entity, Transform(position = Vec3f(2f, 0f, 4f), rotation = euler))
        val shape = HeightFieldShape(
            heights = FloatArray(16),
            sampleCount = 4,
            scale = Vec3f(1f, 1f, 2f),
            origin = GridOrigin.Corner,
        )
        world.add(entity, PhysicsBody(shape = shape, motionType = MotionType.STATIC))
        val lines = RecordedLines()

        PhysicsDebugSource(world).lines(lines)

        val rotation = Quat.fromEuler(euler)
        val expected = listOf(Vec3f(0f, 0f, 0f), Vec3f(3f, 0f, 0f), Vec3f(3f, 0f, 6f), Vec3f(0f, 0f, 6f))
            .map { Vec3f(2f, 0f, 4f) + rotation.rotate(it) }
        assertEquals(4, lines.drawn.size)
        lines.drawn.forEachIndexed { index, line ->
            assertTrue(line.a.near(expected[index]), "corner $index at ${line.a}, not ${expected[index]}")
        }
        assertEquals(physicsDebugLines(world).map { it.start }, lines.drawn.map { it.a })
    }

    @Test
    fun aSteadySecondFrameWritesTheSameLines() {
        val source = PhysicsDebugSource(knownWorld())
        val first = RecordedLines().also(source::lines)

        val second = RecordedLines().also(source::lines)

        assertEquals(first.drawn.size, second.drawn.size)
        assertEquals(first.drawn, second.drawn)
    }

    @Test
    fun aWorldWithNoBodiesDrawsNothing() {
        val world = World()
        world.add(world.create(), Transform(position = Vec3f(1f, 2f, 3f)))
        val lines = RecordedLines()

        PhysicsDebugSource(world).lines(lines)

        assertEquals(0, lines.drawn.size)
    }

    private data class Drawn(val a: Vec3f, val b: Vec3f, val color: Color)

    /** Copies each line's points, as a host does: the source reuses its own. */
    private class RecordedLines : DebugLines {
        val drawn = ArrayList<Drawn>()

        override fun line(a: Vec3f, b: Vec3f, color: Color) {
            drawn += Drawn(a.copy(), b.copy(), color)
        }
    }

    private class RecordedRows : DebugRows {
        val rows = ArrayList<Pair<String, Int>>()
        val warned = ArrayList<String>()

        override fun row(label: String, value: Int, warn: Boolean) {
            rows += label to value
            if (warn) warned += label
        }

        override fun row(label: String, value: Float, decimals: Int, warn: Boolean) = error("no float rows")

        override fun row(label: String, value: String, warn: Boolean) = error("no text rows")
    }

    private fun Vec3f.near(other: Vec3f): Boolean =
        kotlin.math.abs(x - other.x) < EPSILON && kotlin.math.abs(y - other.y) < EPSILON &&
            kotlin.math.abs(z - other.z) < EPSILON

    private companion object {
        const val BOX_EDGES = 12
        const val EPSILON = 1e-4f
    }
}
