/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs.benchmark

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.MeshCollider
import com.awakekt.awake.scene.physics.MeshColliderSystem
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
 * What `MeshColliderSystem` costs a server tick once every collider has its body (#1428), and the
 * whole physics part of a 30 Hz tick: the collider system and `PhysicsSystem` run each 60 Hz fixed
 * step, twice, then one interpolation. The backend simulates nothing ([AwakeBodiesPhysicsWorld]).
 *
 * Run: `./gradlew :awake:ecs:benchmark:mainBenchmark`.
 */
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class MeshColliderTickBenchmarks {
    /** One fixed step of `MeshColliderSystem` with nothing left to build. */
    @Benchmark
    fun colliderStep(state: MeshColliderTickState): Int {
        state.colliders.update(state.world, FIXED_STEP)
        return state.world.generation
    }

    /** A whole 30 Hz tick of both physics systems. */
    @Benchmark
    fun serverTick(state: MeshColliderTickState): Float {
        repeat(2) {
            state.colliders.update(state.world, FIXED_STEP)
            state.physics.update(state.world, FIXED_STEP)
        }
        state.physics.interpolate(state.world, 0.5f)
        return state.probe.position.x
    }
}

@State(Scope.Thread)
open class MeshColliderTickState {
    /** About the region the issue profiled: static mesh colliders and moving bodies, 1,200 in all. */
    @Param("600")
    var colliderCount: Int = 0

    @Param("600")
    var bodyCount: Int = 0

    lateinit var world: World
    lateinit var colliders: MeshColliderSystem
    lateinit var physics: PhysicsSystem
    lateinit var probe: Transform

    @Setup
    fun setup() {
        world = World()
        colliders = MeshColliderSystem { _, _ -> TRIANGLE }
        physics = PhysicsSystem(AwakeBodiesPhysicsWorld())
        repeat(colliderCount) { index ->
            val entity = world.create()
            world.add(entity, Transform(position = Vec3f(index.toFloat(), 0f, 0f)))
            world.add(entity, MeshCollider("props/rock.glb"))
        }
        repeat(bodyCount) { index ->
            val entity = world.create()
            val transform = Transform(position = Vec3f(index.toFloat(), 1f, 0f))
            world.add(entity, transform)
            world.add(entity, PhysicsBody(SphereShape(radius = 0.5f), MotionType.DYNAMIC))
            if (index == 0) probe = transform
        }
        // Builds every collider's body and every body, outside the measurement.
        colliders.update(world, FIXED_STEP)
        physics.update(world, FIXED_STEP)
        physics.update(world, FIXED_STEP)
    }
}

private const val FIXED_STEP = 1f / 60f
private val TRIANGLE = MeshShape(floatArrayOf(1f, 0f, 0f, 2f, 1f, 0f, 1f, 1f, 1f), intArrayOf(0, 1, 2))
