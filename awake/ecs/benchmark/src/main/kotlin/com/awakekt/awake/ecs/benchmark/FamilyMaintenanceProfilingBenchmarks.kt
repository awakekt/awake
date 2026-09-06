/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs.benchmark

import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family1
import com.awakekt.awake.ecs.Family2
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/** Decomposes identical pooled component churn by maintained-family arity. */
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
open class FamilyMaintenanceProfilingBenchmarks {
    @Benchmark
    fun pooledTransformChurn(state: FamilyMaintenanceState): Int {
        val world = state.world
        val typeId = state.transformTypeId
        for (packed in state.packedEntities) {
            world.remove<Transform>(Entity(packed), typeId)
        }
        for (packed in state.packedEntities) {
            world.add<Transform>(Entity(packed), typeId)
        }
        return state.resultSize()
    }
}

@State(Scope.Benchmark)
open class FamilyMaintenanceState {
    @Param("10000", "100000")
    var entityCount: Int = 0

    @Param("0", "1", "2")
    var familyArity: Int = 0

    lateinit var world: World
    lateinit var packedEntities: LongArray
    lateinit var family1: Family1<Transform>
    lateinit var family2: Family2<Transform, MeshRenderer>
    var transformTypeId: ComponentTypeId = ComponentTypeId(0)

    @Setup(Level.Trial)
    fun setup() {
        world = World()
        world.registerPool(Transform::class) { Transform() }
        packedEntities = LongArray(entityCount)
        repeat(entityCount) { id ->
            val entity = world.create()
            packedEntities[id] = entity.packed
            world.add(entity, Transform())
            world.add(entity, MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material))
        }
        when (familyArity) {
            0 -> Unit
            1 -> family1 = world.family<Transform>()
            2 -> family2 = world.family<Transform, MeshRenderer>()
            else -> error("Unsupported family arity: $familyArity")
        }
        transformTypeId = world.typeId(Transform::class)
    }

    fun resultSize(): Int =
        when (familyArity) {
            0 -> world.componentCount(Transform::class)
            1 -> family1.size
            2 -> family2.size
            else -> error("Unsupported family arity: $familyArity")
        }
}
