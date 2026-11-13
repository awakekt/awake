/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyTransform
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.core.transform.Transform
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A [PhysicsSystem] step that reads every body's pose back, and the interpolation after it,
 * allocate nothing: the readback takes each handle as a raw id, so none is boxed per body (#1428).
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class PhysicsReadbackAllocationProbeTest {
    @Test
    fun aStepOfAwakeBodiesAllocatesNothing() {
        val backend = RecordingPhysicsWorld()
        val world = World()
        val system = PhysicsSystem(backend)
        val transforms = ArrayList<Transform>()
        repeat(BODIES) { index ->
            val entity = world.create()
            val transform = Transform(position = Vec3f(index.toFloat(), 0f, 0f))
            transforms += transform
            world.add(entity, transform)
            world.add(entity, PhysicsBody(SphereShape(radius = 0.5f), MotionType.DYNAMIC))
        }
        system.update(world, STEP)
        // Every body awake every step: the worst case for the readback.
        backend.scriptedTransforms = backend.created.mapIndexed { index, handle ->
            BodyTransform(handle, Vec3f(index.toFloat(), 1f, 0f), Quat())
        }
        repeat(WARMUP_STEPS) { tick(world, system) }

        // The claim is that the code path allocates nothing, so one clean window proves it: a path
        // that allocates does so in every window, while a one-off (a late JIT deopt, a lazily
        // initialised JDK structure) lands in only some. The minimum ignores the one-offs and still
        // fails a real per-step allocation, which every window repeats.
        val windows = List(WINDOWS) {
            val start = allocated()
            repeat(MEASURED_STEPS) { tick(world, system) }
            allocated() - start
        }
        println("physics readback bytes per window of $MEASURED_STEPS steps: $windows")

        assertEquals(0L, windows.min() / MEASURED_STEPS, "bytes per step, minimum of $WINDOWS windows")
        assertEquals(1f, transforms.last().position.y, "the readback reached the transforms")
    }

    private fun tick(world: World, system: PhysicsSystem) {
        system.update(world, STEP)
        system.interpolate(world, 0.5f)
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val BODIES = 1200
        const val STEP = 1f / 60f
        const val WARMUP_STEPS = 200
        const val MEASURED_STEPS = 400
        const val WINDOWS = 7
    }
}
