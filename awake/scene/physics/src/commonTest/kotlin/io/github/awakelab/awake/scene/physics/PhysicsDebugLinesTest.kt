/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.CapsuleShape
import io.github.awakelab.awake.physics.ConvexHullShape
import io.github.awakelab.awake.physics.HeightFieldShape
import io.github.awakelab.awake.physics.MeshShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsShape
import io.github.awakelab.awake.physics.SphereShape
import io.github.awakelab.awake.render.renderer.LineSegment
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.physics.PhysicsBody
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That the wireframe is drawn where the collider actually is.
 *
 * A debug overlay is only worth having if it is trusted, and one drawn from the mesh's transform
 * instead of the collider's -- or at the wrong scale -- hides exactly the bug it exists to show.
 * So these assert extents and placement rather than that some lines came back.
 */
class PhysicsDebugLinesTest {

    private fun worldWith(
        shape: PhysicsShape,
        position: Vec3f = Vec3f(0f, 0f, 0f),
        motionType: MotionType = MotionType.DYNAMIC,
    ): World = World().apply {
        val entity = create()
        add(entity, Transform(position = position))
        add(entity, PhysicsBody(shape = shape, motionType = motionType))
    }

    private fun List<LineSegment>.bounds(): Pair<Vec3f, Vec3f> {
        val points = flatMap { listOf(it.start, it.end) }
        return Vec3f(
            points.minOf { it.x },
            points.minOf { it.y },
            points.minOf { it.z },
        ) to Vec3f(
            points.maxOf { it.x },
            points.maxOf { it.y },
            points.maxOf { it.z },
        )
    }

    @Test
    fun aBoxIsDrawnAtItsOwnHalfExtents() {
        val lines = physicsDebugLines(worldWith(BoxShape(Vec3f(1f, 2f, 3f)), Vec3f(10f, 0f, 0f)))

        val (min, max) = lines.bounds()
        assertTrue(abs(min.x - 9f) < 1e-4f && abs(max.x - 11f) < 1e-4f, "x $min..$max")
        assertTrue(abs(min.y + 2f) < 1e-4f && abs(max.y - 2f) < 1e-4f, "y $min..$max")
        assertTrue(abs(min.z + 3f) < 1e-4f && abs(max.z - 3f) < 1e-4f, "z $min..$max")
    }

    @Test
    fun aBoxHasTwelveEdges() {
        val lines = physicsDebugLines(worldWith(BoxShape(Vec3f(1f, 1f, 1f))))

        // Not decoration: a box drawn with a missing or doubled edge is the first sign the corner
        // enumeration is wrong, and a wrong corner is a wrong extent.
        assertEquals(12, lines.size)
    }

    @Test
    fun aSphereIsDrawnAtItsOwnRadius() {
        val lines = physicsDebugLines(worldWith(SphereShape(2.5f), Vec3f(0f, 5f, 0f)))

        val (min, max) = lines.bounds()
        // Rings are polygons, so they sit just inside the true radius; the extent must still be
        // close enough that a mis-scaled collider is obvious.
        assertTrue(max.x in 2.4f..2.5f, "x extent ${max.x}")
        assertTrue(abs(min.y - 2.5f) < 0.11f && abs(max.y - 7.5f) < 0.11f, "y $min..$max")
    }

    @Test
    fun aCapsuleReachesARadiusBeyondItsCylinder() {
        // Total half-height is halfHeight + radius: a capsule drawn only to its cylinder looks
        // shorter than it collides, which reads as the character floating.
        val lines = physicsDebugLines(worldWith(CapsuleShape(halfHeight = 0.9f, radius = 0.3f)))

        val (min, max) = lines.bounds()
        assertTrue(abs(max.y - 1.2f) < 1e-4f, "top at ${max.y}, expected 1.2")
        assertTrue(abs(min.y + 1.2f) < 1e-4f, "bottom at ${min.y}, expected -1.2")
    }

    @Test
    fun aHeightfieldIsDrawnAsItsCentredFootprint() {
        // Laid out from the centre, matching the engine's convention. Getting this wrong by half a
        // tile is the exact bug the overlay is meant to expose, so it must not reproduce it.
        val shape = HeightFieldShape(
            heights = FloatArray(16),
            sampleCount = 4,
            scale = Vec3f(2f, 1f, 2f),
        )

        val lines = physicsDebugLines(worldWith(shape))

        val (min, max) = lines.bounds()
        // Three cells of two units each: six across, centred on the origin.
        assertTrue(abs(min.x + 3f) < 1e-4f && abs(max.x - 3f) < 1e-4f, "x $min..$max")
        assertTrue(abs(min.z + 3f) < 1e-4f && abs(max.z - 3f) < 1e-4f, "z $min..$max")
        assertEquals(4, lines.size, "a footprint is four edges, not a sample grid")
    }

    @Test
    fun motionTypeIsVisibleAsColour() {
        val staticLines = physicsDebugLines(
            worldWith(BoxShape(Vec3f(1f, 1f, 1f)), motionType = MotionType.STATIC),
        )
        val dynamicLines = physicsDebugLines(
            worldWith(BoxShape(Vec3f(1f, 1f, 1f)), motionType = MotionType.DYNAMIC),
        )

        // A static body that should have been dynamic looks identical until something fails to
        // fall; the colour is the cheapest way to see it.
        assertTrue(staticLines.first().color != dynamicLines.first().color)
    }

    @Test
    fun anEntityWithNoBodyDrawsNothing() {
        val world = World()
        val entity = world.create()
        world.add(entity, Transform(position = Vec3f(1f, 2f, 3f)))

        assertTrue(physicsDebugLines(world).isEmpty())
    }

    @Test
    fun aMeshIsDrawnAsTheBoxEnclosingItsVertices() {
        // Not its triangles: a level mesh is thousands of them, and the question this overlay
        // answers is where the collider is, not what its interior looks like.
        val mesh = MeshShape(
            vertices = floatArrayOf(
                -2f, 0f, -3f,
                2f, 0f, -3f,
                2f, 1f, 3f,
                -2f, 1f, 3f,
            ),
            indices = intArrayOf(0, 2, 1, 0, 3, 2),
        )

        val lines = physicsDebugLines(worldWith(mesh, motionType = MotionType.STATIC))

        val (min, max) = lines.bounds()
        assertEquals(12, lines.size, "a box is twelve edges")
        assertTrue(abs(min.x + 2f) < 1e-4f && abs(max.x - 2f) < 1e-4f, "x $min..$max")
        assertTrue(abs(min.z + 3f) < 1e-4f && abs(max.z - 3f) < 1e-4f, "z $min..$max")
        assertTrue(abs(max.y - 1f) < 1e-4f, "y $min..$max")
    }

    @Test
    fun aConvexHullIsDrawnAroundItsOwnPoints() {
        val hull = ConvexHullShape(
            floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 2f),
        )

        val lines = physicsDebugLines(worldWith(hull, position = Vec3f(10f, 0f, 0f)))

        val (min, max) = lines.bounds()
        // The cloud spans 0..2 on each axis, centred at 1, and the body sits at x = 10.
        assertTrue(abs(min.x - 10f) < 1e-4f && abs(max.x - 12f) < 1e-4f, "x $min..$max")
        assertTrue(abs(min.y) < 1e-4f && abs(max.y - 2f) < 1e-4f, "y $min..$max")
    }
}
