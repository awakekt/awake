/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs.benchmark

import com.awakekt.awake.ecs.ComponentType
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.componentType
import com.awakekt.awake.ecs.get
import com.awakekt.awake.ecs.has
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
import kotlin.reflect.KClass

/**
 * One pass of `world.get` over every entity of a server region (#1428): what a system that looks a
 * component up per entity, rather than iterating a family, pays per step. The world carries a
 * dozen component types, as a played scene does, so a class lookup probes a populated table.
 *
 * Run: `./gradlew :awake:ecs:benchmark:mainBenchmark`.
 */
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class ComponentLookupBenchmarks {
    /** `world.get<T>(entity)`: the reified sugar game code and systems call. */
    @Benchmark
    fun reifiedGet(state: ComponentLookupState): Int {
        var sum = 0
        for (entity in state.entities) sum += state.world.get<LookupPosition>(entity)!!.x
        return sum
    }

    /** `world.get(entity, T::class)`: the class form. */
    @Benchmark
    fun classGet(state: ComponentLookupState): Int {
        var sum = 0
        for (entity in state.entities) sum += state.world.get(entity, LookupPosition::class)!!.x
        return sum
    }

    /** `world.get(entity, handle)`: the [ComponentType] handle form. */
    @Benchmark
    fun handleGet(state: ComponentLookupState): Int {
        var sum = 0
        for (entity in state.entities) sum += state.world.get(entity, LOOKUP_POSITION)!!.x
        return sum
    }

    /** `world.has<T>(entity)` on a type half the entities lack, as a "not built yet" check does. */
    @Benchmark
    fun reifiedHas(state: ComponentLookupState): Int {
        var count = 0
        for (entity in state.entities) if (state.world.has<LookupMarker>(entity)) count++
        return count
    }

    /** `world.has(entity, handle)`: the handle form of [reifiedHas]. */
    @Benchmark
    fun handleHas(state: ComponentLookupState): Int {
        var count = 0
        for (entity in state.entities) if (state.world.has(entity, LOOKUP_MARKER)) count++
        return count
    }

    /** The floor: the same components by family iteration, with no lookup at all. */
    @Benchmark
    fun familyIteration(state: ComponentLookupState): Int {
        var sum = 0
        state.world.queryEach<LookupPosition> { _, position -> sum += position.x }
        return sum
    }
}

@State(Scope.Thread)
open class ComponentLookupState {
    /** About the region the issue profiled: 1,244 monsters and their bodies. */
    @Param("1200")
    var entityCount: Int = 0

    lateinit var world: World
    lateinit var entities: Array<Entity>

    @Setup
    fun setup() {
        world = World()
        entities = Array(entityCount) { index ->
            world.create().also { entity ->
                world.add(entity, LookupPosition(index))
                if (index % 2 == 0) world.add(entity, LookupMarker)
                FILLER_TYPES.forEach { filler -> filler.addTo(world, entity) }
            }
        }
    }
}

class LookupPosition(val x: Int)

data object LookupMarker

private val LOOKUP_POSITION: ComponentType<LookupPosition> = componentType()
private val LOOKUP_MARKER: ComponentType<LookupMarker> = componentType()

private class Filler<T : Any>(val type: KClass<T>, val instance: () -> T) {
    fun addTo(world: World, entity: Entity) {
        world.add(entity, type, instance())
    }
}

private class F0
private class F1
private class F2
private class F3
private class F4
private class F5
private class F6
private class F7
private class F8
private class F9

private val FILLER_TYPES = listOf(
    Filler(F0::class, ::F0), Filler(F1::class, ::F1), Filler(F2::class, ::F2), Filler(F3::class, ::F3),
    Filler(F4::class, ::F4), Filler(F5::class, ::F5), Filler(F6::class, ::F6), Filler(F7::class, ::F7),
    Filler(F8::class, ::F8), Filler(F9::class, ::F9),
)
