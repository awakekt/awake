/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs.benchmark

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * What `PhysicsSystem` itself costs a server tick (#1428), with a backend that simulates nothing
 * and reports every body awake ([AwakeBodiesPhysicsWorld]).
 *
 * A tick is a 30 Hz frame: two 60 Hz fixed steps ([fixedStep] twice) and one [PhysicsSystem.interpolate].
 * Run: `./gradlew :awake:ecs:benchmark:mainBenchmark`.
 */
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class PhysicsTickBenchmarks {
    /** One fixed step: the scan for bodies to build or rebuild, the step, and the pose readback. */
    @Benchmark
    fun fixedStep(state: PhysicsTickState): Float {
        state.system.update(state.world, FIXED_STEP)
        return state.probe.position.x
    }

    /** One render-frame blend of every body's last two poses. */
    @Benchmark
    fun interpolate(state: PhysicsTickState): Float {
        state.system.interpolate(state.world, 0.5f)
        return state.probe.position.x
    }

    /** A whole 30 Hz tick. */
    @Benchmark
    fun serverTick(state: PhysicsTickState): Float {
        state.system.update(state.world, FIXED_STEP)
        state.system.update(state.world, FIXED_STEP)
        state.system.interpolate(state.world, 0.5f)
        return state.probe.position.x
    }
}

@State(Scope.Thread)
open class PhysicsTickState {
    /** About the region the issue profiled: 1,244 monsters, each a body. */
    @Param("1200")
    var bodyCount: Int = 0

    lateinit var world: World
    lateinit var system: PhysicsSystem
    lateinit var probe: Transform

    @Setup
    fun setup() {
        world = World()
        system = PhysicsSystem(AwakeBodiesPhysicsWorld())
        repeat(bodyCount) { index ->
            val entity = world.create()
            val transform = Transform(position = Vec3f(index.toFloat(), 0f, 0f))
            world.add(entity, transform)
            world.add(entity, PhysicsBody(SphereShape(radius = 0.5f), MotionType.DYNAMIC))
            if (index == 0) probe = transform
        }
        // Builds every body, and records a first pose for each, outside the measurement.
        system.update(world, FIXED_STEP)
        system.update(world, FIXED_STEP)
    }
}

private const val FIXED_STEP = 1f / 60f
