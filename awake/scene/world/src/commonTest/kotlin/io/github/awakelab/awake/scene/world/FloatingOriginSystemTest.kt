/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.world

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The property every one of these tests is really about: an entity's ABSOLUTE position never
 * changes. A shift moves local coordinates and the origin by equal and opposite amounts, and
 * anything that reads absolute space -- streaming, a save file -- cannot tell one happened.
 */
class FloatingOriginSystemTest {

    @Test
    fun anObserverInsideTheThresholdDoesNotMoveTheWorld() {
        val world = World()
        val observer = world.observerAt(x = THRESHOLD - 1f)
        val prop = world.propAt(x = 500f)

        FloatingOriginSystem(threshold = THRESHOLD, quantum = QUANTUM).update(world, DELTA)

        assertEquals(THRESHOLD - 1f, world.positionOf(observer).x)
        assertEquals(500f, world.positionOf(prop).x)
        assertNull(
            world.findWorldOrigin(),
            "A scene that never crossed the threshold should not have paid for an origin entity.",
        )
    }

    @Test
    fun crossingTheThresholdRecentresTheObserverAndMovesEverythingWithIt() {
        val world = World()
        val observer = world.observerAt(x = 5_000f, z = -3_000f)
        val prop = world.propAt(x = 5_100f, z = -3_000f)

        FloatingOriginSystem(threshold = THRESHOLD, quantum = QUANTUM).update(world, DELTA)

        val origin = requireNotNull(world.findWorldOrigin())
        // 5000/1024 rounds to 5 steps, -3000/1024 to -3.
        assertEquals(5, origin.stepX)
        assertEquals(-3, origin.stepZ)
        val observerPosition = world.positionOf(observer)
        assertTrue(
            observerPosition.x in -QUANTUM..QUANTUM && observerPosition.z in -QUANTUM..QUANTUM,
            "The observer should land within one step of the local origin, not at $observerPosition.",
        )
        // The pair is what matters: their separation, and their absolute positions, are untouched.
        assertEquals(100f, world.positionOf(prop).x - observerPosition.x)
        assertEquals(5_100f, origin.toAbsolute(world.positionOf(prop)).x)
    }

    @Test
    fun aChildRidesItsParentRatherThanShiftingTwice() {
        val world = World()
        world.observerAt(x = 5_000f)
        val parent = world.propAt(x = 5_000f)
        val child = world.create()
        world.add(child, Transform(position = Vec3f(2f, 0f, 0f), parent = parent))

        FloatingOriginSystem(threshold = THRESHOLD, quantum = QUANTUM).update(world, DELTA)

        assertEquals(
            2f,
            world.positionOf(child).x,
            "A child's position is relative to a parent that already moved; shifting it too " +
                "would put it a whole shift away from what it is attached to.",
        )
    }

    @Test
    fun shiftsAccumulateInsteadOfResettingTheOrigin() {
        val world = World()
        val observer = world.observerAt(x = 5_000f)
        val system = FloatingOriginSystem(threshold = THRESHOLD, quantum = QUANTUM)

        system.update(world, DELTA)
        // Walk another 5km from wherever the first shift left it.
        world.positionOf(observer).x += 5_000f
        system.update(world, DELTA)

        val origin = requireNotNull(world.findWorldOrigin())
        assertEquals(
            10_000f,
            origin.toAbsolute(world.positionOf(observer)).x,
            ABSOLUTE_TOLERANCE,
            "Two shifts of 5km should read as 10km absolute. An origin that reset rather than " +
                "accumulated would report the second leg alone.",
        )
    }

    @Test
    fun aListenerIsToldByHowMuchTheWorldMoved() {
        val world = World()
        world.observerAt(x = 5_000f)
        var reported: Vec3f? = null
        val system = FloatingOriginSystem(threshold = THRESHOLD, quantum = QUANTUM)
        system.addListener { shift, _ -> reported = Vec3f(shift.x, shift.y, shift.z) }

        system.update(world, DELTA)

        assertEquals(
            -5 * QUANTUM,
            requireNotNull(reported).x,
            "A physics body or nav grid applies this to its own coordinates, so it has to be the " +
                "same offset the transforms got.",
        )
    }

    @Test
    fun streamingLoadsTheSameCellsAcrossAShift() {
        val config = WorldPartitionConfig(cellSize = 512f, loadingRadius = 600f, unloadRadius = 900f)
        val before = World().let { world ->
            world.observerAt(x = 5_000f)
            WorldPartitionSystem(config).also { it.update(world, DELTA) }.activeCells.toSet()
        }

        val world = World()
        world.observerAt(x = 5_000f)
        val partition = WorldPartitionSystem(config)
        FloatingOriginSystem(threshold = THRESHOLD, quantum = QUANTUM).update(world, DELTA)
        partition.update(world, DELTA)

        assertEquals(
            before,
            partition.activeCells.toSet(),
            "Cell coordinates are absolute, so moving the world under the observer must not " +
                "change which cells are loaded. Streaming off the local position would load the " +
                "cells around zero instead.",
        )
    }

    private fun World.observerAt(x: Float = 0f, y: Float = 0f, z: Float = 0f) = create().also {
        add(it, Transform(position = Vec3f(x, y, z)))
        add(it, StreamObserver)
    }

    private fun World.propAt(x: Float = 0f, y: Float = 0f, z: Float = 0f) = create().also {
        add(it, Transform(position = Vec3f(x, y, z)))
    }

    private fun World.positionOf(entity: io.github.awakelab.awake.ecs.Entity): Vec3f =
        requireNotNull(get<Transform>(entity)).position

    private companion object {
        const val THRESHOLD = 2_048f
        const val QUANTUM = 1_024f
        const val DELTA = 1f / 60f

        /** One step is exact in a float; the sum of two 5km legs is not, by a few ulps. */
        const val ABSOLUTE_TOLERANCE = 0.01f
    }
}
