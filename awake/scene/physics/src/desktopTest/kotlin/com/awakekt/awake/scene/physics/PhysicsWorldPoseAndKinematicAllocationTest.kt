/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyTransform
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Zero-allocation gates for parented world-pose resolution, kinematic `moveKinematic` sync, and
 * [PhysicsDebugWriter] under `-XX:-DoEscapeAnalysis`.
 */
class PhysicsWorldPoseAndKinematicAllocationTest {
    @Test
    fun steadyStepWithParentedStaticBodiesAllocatesNothing() {
        val backend = RecordingPhysicsWorld()
        val world = World()
        val system = PhysicsSystem(backend)
        addParentedStaticBodies(world, STATIC_BODIES)
        system.update(world, STEP)
        assertEquals(STATIC_BODIES, backend.created.size)

        val reading = readAllocation(MEASURED_STEPS) {
            repeat(MEASURED_STEPS) {
                system.update(world, STEP)
                system.interpolate(world, 0.5f)
            }
        }
        assertAllocatesNothing(reading, "steady-state PhysicsSystem step with $STATIC_BODIES parented static bodies")
    }

    @Test
    fun movingParentedKinematicBodiesAllocateNothing() {
        val backend = RecordingPhysicsWorld()
        val world = World()
        val system = PhysicsSystem(backend)
        val roots = addParentedKinematicPlatforms(world, KINEMATIC_BODIES)
        system.update(world, STEP)
        backend.scriptedTransforms = backend.created.mapIndexed { index, handle ->
            BodyTransform(handle, Vec3f(index.toFloat(), 1f, 0f), Quat())
        }
        var phase = 0f

        fun tickMoving() {
            phase += STEP
            for (index in roots.indices) {
                roots[index].position.y = 2f + phase
                roots[index].rotation.y = phase * 0.5f
            }
            system.update(world, STEP)
            system.interpolate(world, 0.5f)
        }

        repeat(WARMUP_STEPS) { tickMoving() }
        val movesBefore = backend.kinematicMoveCount
        val reading = readAllocation(MEASURED_STEPS) {
            repeat(MEASURED_STEPS) { tickMoving() }
        }
        assertTrue(backend.kinematicMoveCount > movesBefore, "moving kinematic platforms must drive moveKinematic")
        assertAllocatesNothing(reading, "PhysicsSystem step driving $KINEMATIC_BODIES parented kinematic platforms")
    }

    @Test
    fun stationaryParentedKinematicBodiesAllocateNothingAndSkipRedundantMoves() {
        val backend = RecordingPhysicsWorld()
        val world = World()
        val system = PhysicsSystem(backend)
        addParentedKinematicPlatforms(world, KINEMATIC_BODIES)
        system.update(world, STEP)
        repeat(WARMUP_STEPS) { system.update(world, STEP) }

        val movesBefore = backend.kinematicMoveCount
        val reading = readAllocation(MEASURED_STEPS) {
            repeat(MEASURED_STEPS) { system.update(world, STEP) }
        }
        assertEquals(movesBefore, backend.kinematicMoveCount, "parked kinematic bodies must not call moveKinematic")
        assertAllocatesNothing(reading, "PhysicsSystem step with $KINEMATIC_BODIES parked parented kinematic platforms")
    }

    @Test
    fun debugWriterOverParentedCollidersAllocatesNothing() {
        val world = World()
        addParentedStaticBodies(world, DEBUG_BODIES / 2)
        addParentedKinematicPlatforms(world, DEBUG_BODIES / 2)
        val writer = PhysicsDebugWriter()
        var segments = 0
        val sink = DebugLines { _, _, _ -> segments++ }

        repeat(WARMUP_STEPS) { writer.write(world, sink) }
        val reading = readAllocation(DEBUG_FRAMES) {
            repeat(DEBUG_FRAMES) { writer.write(world, sink) }
        }
        assertTrue(segments > 0, "debug writer must emit collider wireframe segments")
        assertAllocatesNothing(reading, "PhysicsDebugWriter.write over $DEBUG_BODIES parented colliders")
    }

    private fun addParentedStaticBodies(world: World, count: Int) {
        repeat(count) { index ->
            val parent = world.create()
            world.add(
                parent,
                Transform(
                    position = Vec3f(index * 4f, 0f, -index * 2f),
                    rotation = Vec3f(0f, 0.25f, 0f),
                ),
            )
            val child = world.create()
            world.add(child, Transform(position = Vec3f(0f, 0.5f, 0f), parent = parent))
            world.add(child, PhysicsBody(BoxShape(Vec3f(1f, 0.25f, 1f)), MotionType.STATIC))
        }
    }

    private fun addParentedKinematicPlatforms(world: World, count: Int): ArrayList<Transform> {
        val roots = ArrayList<Transform>(count)
        repeat(count) { index ->
            val root = world.create()
            val rootTransform = Transform(
                position = Vec3f(index * 5f, 2f, -index * 3f),
                rotation = Vec3f(0f, 0.3f, 0f),
            )
            world.add(root, rootTransform)
            roots += rootTransform

            val mid = world.create()
            world.add(mid, Transform(position = Vec3f(0f, 0.5f, 1f), parent = root))

            val collider = world.create()
            world.add(collider, Transform(position = Vec3f(0f, -0.1f, 0f), parent = mid))
            world.add(collider, PhysicsBody(SphereShape(radius = 0.75f), MotionType.KINEMATIC))
        }
        return roots
    }

    private companion object {
        const val STATIC_BODIES = 1000
        const val KINEMATIC_BODIES = 64
        const val DEBUG_BODIES = 256
        const val STEP = 1f / 60f
        const val WARMUP_STEPS = 100
        const val MEASURED_STEPS = 300
        const val DEBUG_FRAMES = 200
    }
}
