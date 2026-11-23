/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs.benchmark

import com.awakekt.awake.ecs.ComponentType
import com.awakekt.awake.ecs.EcsTag
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family
import com.awakekt.awake.ecs.Family2
import com.awakekt.awake.ecs.Family2System
import com.awakekt.awake.ecs.FamilySpec
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.componentType
import com.awakekt.awake.ecs.family
import com.awakekt.awake.ecs.get
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * Compares packed [FamilySpec]-filtered queries and [Family2System] (monomorphic and megamorphic)
 * against unpacked [Family] + `World.get` and direct `queryEach`.
 */
@Fork(1)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Suppress("TooManyFunctions")
open class FilteredFamilyAndSystemBenchmarks {
    @Benchmark
    fun unfilteredQueryEachPair(state: FilteredEcsState): Int {
        var sum = 0
        state.world.queryEach<BenchPos, BenchVel> { _, pos, vel ->
            sum += pos.x + vel.vx
        }
        return sum
    }

    @Benchmark
    fun unfilteredFamily2SystemMono(state: FilteredEcsState): Int {
        state.monoSystem.sum = 0
        state.monoSystem.update(state.world, 0.016f)
        return state.monoSystem.sum
    }

    @Benchmark
    fun unfilteredFamily2SystemMega(state: FilteredEcsState): Int {
        val system = state.megaSystems[state.megaIndex++ % state.megaSystems.size]
        system.sum = 0
        system.update(state.world, 0.016f)
        return system.sum
    }

    @Benchmark
    fun unfilteredFamily2SystemMegaRunTick(state: FilteredEcsState): Int {
        val system = state.runTickSystems[state.runTickIndex++ % state.runTickSystems.size]
        system.sum = 0
        system.update(state.world, 0.016f)
        return system.sum
    }

    @Benchmark
    fun excludedUnpackedFamilySpec(state: FilteredEcsState): Int {
        val world = state.world
        val posType = state.posType
        val velType = state.velType
        var sum = 0
        state.unpackedExcluded.forEach { entity ->
            val pos = world.get(entity, posType) ?: return@forEach
            val vel = world.get(entity, velType) ?: return@forEach
            sum += pos.x + vel.vx
        }
        return sum
    }

    @Benchmark
    fun excludedPackedFamily2(state: FilteredEcsState): Int {
        var sum = 0
        state.packedExcluded.forEach { _, pos, vel ->
            sum += pos.x + vel.vx
        }
        return sum
    }

    @Benchmark
    fun excludedPackedFamily2LookupAndIterate(state: FilteredEcsState): Int {
        var sum = 0
        state.world.family(state.posType, state.velType, AWAKE_SPEC).forEach { _, pos, vel ->
            sum += pos.x + vel.vx
        }
        return sum
    }

    @Benchmark
    fun excludedFamily2System(state: FilteredEcsState): Int {
        state.excludedSystem.sum = 0
        state.excludedSystem.update(state.world, 0.016f)
        return state.excludedSystem.sum
    }

    @Benchmark
    fun toggleExcludeTagChurn(state: FilteredEcsState): Int {
        val world = state.world
        val entities = state.toggleEntities
        for (index in entities.indices) {
            world.add(Entity(entities[index]), BenchAsleep)
        }
        for (index in entities.indices) {
            world.remove<BenchAsleep>(Entity(entities[index]))
        }
        return state.packedExcluded.size
    }
}

@State(Scope.Thread)
open class FilteredEcsState {
    @Param("10000")
    var entityCount: Int = 0

    lateinit var world: World
    lateinit var unpackedExcluded: Family
    lateinit var packedExcluded: Family2<BenchPos, BenchVel>
    lateinit var monoSystem: BenchSumSystemA
    lateinit var megaSystems: Array<BenchSumBase>
    lateinit var runTickSystems: Array<BenchSumBase>
    lateinit var excludedSystem: BenchExcludedSystem
    lateinit var toggleEntities: LongArray
    var megaIndex: Int = 0
    var runTickIndex: Int = 0

    val posType: ComponentType<BenchPos> = componentType()
    val velType: ComponentType<BenchVel> = componentType()

    @Setup
    fun setup() {
        world = World()
        val toggles = LongArray(TOGGLE_COUNT)
        repeat(entityCount) { index ->
            val entity = world.create()
            world.add(entity, BenchPos(index))
            world.add(entity, BenchVel(1))
            if (index % 2 == 0) {
                world.add(entity, BenchAsleep)
            }
            if (index < TOGGLE_COUNT) {
                toggles[index] = entity.packed
            }
        }
        toggleEntities = toggles
        world.family<BenchPos, BenchVel>()
        unpackedExcluded = world.family {
            all(BenchPos::class, BenchVel::class)
            exclude(BenchAsleep::class)
        }
        packedExcluded = world.family(posType, velType, AWAKE_SPEC)
        monoSystem = BenchSumSystemA()
        megaSystems = arrayOf(
            BenchSumSystemA(),
            BenchSumSystemB(),
            BenchSumSystemC(),
            BenchSumSystemD(),
            BenchSumSystemE(),
            BenchSumSystemF(),
        )
        runTickSystems = arrayOf(
            BenchRunTickSystemA(),
            BenchRunTickSystemB(),
            BenchRunTickSystemC(),
            BenchRunTickSystemD(),
            BenchRunTickSystemE(),
            BenchRunTickSystemF(),
        )
        excludedSystem = BenchExcludedSystem()
        // Warm every subclass so JVM sees a genuinely megamorphic call site.
        for (system in megaSystems) {
            system.update(world, 0.016f)
        }
        for (system in runTickSystems) {
            system.update(world, 0.016f)
        }
    }

    private companion object {
        const val TOGGLE_COUNT = 256
    }
}

abstract class BenchSumBase : Family2System<BenchPos, BenchVel>(BenchPos::class, BenchVel::class) {
    var sum: Int = 0
}

class BenchSumSystemA : BenchSumBase() {
    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchSumSystemB : BenchSumBase() {
    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchSumSystemC : BenchSumBase() {
    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchSumSystemD : BenchSumBase() {
    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchSumSystemE : BenchSumBase() {
    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchSumSystemF : BenchSumBase() {
    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchRunTickSystemA : BenchSumBase() {
    override fun update(world: World, delta: Float) = runTick(world, delta) { entity, first, second ->
        process(world, delta, entity, first, second)
    }

    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchRunTickSystemB : BenchSumBase() {
    override fun update(world: World, delta: Float) = runTick(world, delta) { entity, first, second ->
        process(world, delta, entity, first, second)
    }

    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchRunTickSystemC : BenchSumBase() {
    override fun update(world: World, delta: Float) = runTick(world, delta) { entity, first, second ->
        process(world, delta, entity, first, second)
    }

    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchRunTickSystemD : BenchSumBase() {
    override fun update(world: World, delta: Float) = runTick(world, delta) { entity, first, second ->
        process(world, delta, entity, first, second)
    }

    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchRunTickSystemE : BenchSumBase() {
    override fun update(world: World, delta: Float) = runTick(world, delta) { entity, first, second ->
        process(world, delta, entity, first, second)
    }

    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchRunTickSystemF : BenchSumBase() {
    override fun update(world: World, delta: Float) = runTick(world, delta) { entity, first, second ->
        process(world, delta, entity, first, second)
    }

    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchExcludedSystem : Family2System<BenchPos, BenchVel>(BenchPos::class, BenchVel::class, AWAKE_SPEC) {
    var sum: Int = 0

    override fun process(world: World, delta: Float, entity: Entity, first: BenchPos, second: BenchVel) {
        sum += first.x + second.vx
    }
}

class BenchPos(val x: Int)
class BenchVel(val vx: Int)
data object BenchAsleep : EcsTag

private val AWAKE_SPEC = FamilySpec.exclude(BenchAsleep::class)
