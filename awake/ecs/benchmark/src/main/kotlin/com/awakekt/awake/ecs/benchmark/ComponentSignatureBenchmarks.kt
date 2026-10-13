/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs.benchmark

import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family
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
import kotlin.reflect.KClass

/**
 * The paths that read an entity's component signature: `has` and the churn of an `all`/`one`/
 * `exclude` family. [SignatureState.fillerTypes] registers that many types first, so the measured
 * types land in the signature's first word (0) or its third (128).
 */
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
open class ComponentSignatureBenchmarks {
    @Benchmark
    fun hasProbe(state: SignatureState): Int {
        val world = state.world
        val typeId = state.meshTypeId
        var hits = 0
        for (packed in state.packedEntities) {
            if (world.has(Entity(packed), typeId)) hits++
        }
        return hits
    }

    @Benchmark
    fun familySpecChurn(state: SignatureState): Int {
        val world = state.world
        val typeId = state.transformTypeId
        for (packed in state.packedEntities) {
            world.remove<Transform>(Entity(packed), typeId)
        }
        for (packed in state.packedEntities) {
            world.add<Transform>(Entity(packed), typeId)
        }
        return state.family.size
    }
}

@State(Scope.Benchmark)
open class SignatureState {
    @Param("10000", "100000")
    var entityCount: Int = 0

    @Param("0", "128")
    var fillerTypes: Int = 0

    lateinit var world: World
    lateinit var packedEntities: LongArray
    lateinit var family: Family
    var transformTypeId: ComponentTypeId = ComponentTypeId(0)
    var meshTypeId: ComponentTypeId = ComponentTypeId(0)

    @Setup(Level.Trial)
    fun setup() {
        world = World()
        fillerTypes(fillerTypes).forEach(world::typeId)
        world.registerPool(Transform::class) { Transform() }
        transformTypeId = world.typeId(Transform::class)
        meshTypeId = world.typeId(MeshRenderer::class)
        packedEntities = LongArray(entityCount)
        repeat(entityCount) { index ->
            val entity = world.create()
            packedEntities[index] = entity.packed
            world.add(entity, Transform())
            // Every other entity, so `has` answers both ways.
            if (index % 2 == 0) world.add(entity, MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material))
        }
        family = world.family {
            all(Transform::class)
            one(MeshRenderer::class, SignatureMarker::class)
            exclude(SignatureExcluded::class)
        }
    }
}

/** [count] distinct classes without declaring them: `Filler[]`, `Filler[][]`, and so on. */
private fun fillerTypes(count: Int): List<KClass<out Any>> =
    generateSequence<Class<*>>(Filler::class.java) { java.lang.reflect.Array.newInstance(it, 0).javaClass }
        .take(count)
        .map { it.kotlin }
        .toList()

private class Filler

private class SignatureMarker

private class SignatureExcluded
