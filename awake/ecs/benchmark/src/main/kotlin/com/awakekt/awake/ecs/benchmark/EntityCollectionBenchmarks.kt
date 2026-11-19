/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs.benchmark

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.EntityList
import com.awakekt.awake.ecs.EntitySet
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
 * Compares unboxed [EntityList] and [EntitySet] against boxed `ArrayList<Entity>` and
 * `HashSet<Entity>` for per-tick scratch buffers, membership caches, and steady-state entity churn.
 *
 * Run: `./gradlew :awake:ecs:benchmark:mainBenchmark`.
 */
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class EntityCollectionBenchmarks {
    /** Per-tick scratch list cycle with unboxed [EntityList]. */
    @Benchmark
    fun entityListTickCycle(state: EntityCollectionState): Long {
        val list = state.entityList
        list.clear()
        val raw = state.packedEntities
        for (index in raw.indices) {
            list.add(Entity(raw[index]))
        }
        var sum = 0L
        list.forEach { entity -> sum += entity.packed }
        return sum
    }

    /** Per-tick scratch list cycle with boxed `ArrayList<Entity>`. */
    @Benchmark
    fun arrayListTickCycle(state: EntityCollectionState): Long {
        val list = state.boxedList
        list.clear()
        val raw = state.packedEntities
        for (index in raw.indices) {
            list.add(Entity(raw[index]))
        }
        var sum = 0L
        for (index in 0 until list.size) {
            sum += list[index].packed
        }
        return sum
    }

    /** Per-tick membership cache cycle with unboxed [EntitySet]. */
    @Benchmark
    fun entitySetTickCycle(state: EntityCollectionState): Long {
        val set = state.entitySet
        set.clear()
        val raw = state.packedEntities
        for (index in raw.indices) {
            set.add(Entity(raw[index]))
        }
        var sum = 0L
        for (index in raw.indices) {
            if (Entity(raw[index]) in set) sum += 1L
        }
        set.forEach { entity -> sum += entity.packed }
        return sum
    }

    /** Per-tick membership cache cycle with boxed `HashSet<Entity>`. */
    @Benchmark
    fun hashSetTickCycle(state: EntityCollectionState): Long {
        val set = state.boxedSet
        set.clear()
        val raw = state.packedEntities
        for (index in raw.indices) {
            set.add(Entity(raw[index]))
        }
        var sum = 0L
        for (index in raw.indices) {
            if (Entity(raw[index]) in set) sum += 1L
        }
        for (entity in set) {
            sum += entity.packed
        }
        return sum
    }

    /** Steady-state add/remove/contains churn with unboxed [EntitySet]. */
    @Benchmark
    fun entitySetChurn(state: EntityCollectionState): Int {
        val set = state.churnEntitySet
        var hits = 0
        val count = state.entityCount
        var cursor = state.entityCursor
        for (step in 0 until CHURN_STEPS) {
            val evicted = Entity.of(cursor - count, 0)
            val inserted = Entity.of(cursor++, 0)
            if (set.remove(evicted)) hits++
            if (set.add(inserted)) hits++
            if (inserted in set) hits++
        }
        state.entityCursor = cursor
        return hits
    }

    /** Steady-state add/remove/contains churn with boxed `HashSet<Entity>`. */
    @Benchmark
    fun hashSetChurn(state: EntityCollectionState): Int {
        val set = state.churnBoxedSet
        var hits = 0
        val count = state.entityCount
        var cursor = state.boxedCursor
        for (step in 0 until CHURN_STEPS) {
            val evicted = Entity.of(cursor - count, 0)
            val inserted = Entity.of(cursor++, 0)
            if (set.remove(evicted)) hits++
            if (set.add(inserted)) hits++
            if (inserted in set) hits++
        }
        state.boxedCursor = cursor
        return hits
    }

    private companion object {
        const val CHURN_STEPS = 256
    }
}

@State(Scope.Thread)
open class EntityCollectionState {
    @Param("512")
    var entityCount: Int = 0

    lateinit var packedEntities: LongArray
    lateinit var entityList: EntityList
    lateinit var boxedList: ArrayList<Entity>
    lateinit var entitySet: EntitySet
    lateinit var boxedSet: HashSet<Entity>
    lateinit var churnEntitySet: EntitySet
    lateinit var churnBoxedSet: HashSet<Entity>
    var entityCursor: Int = 0
    var boxedCursor: Int = 0

    @Setup
    fun setup() {
        packedEntities = LongArray(entityCount) { index -> Entity.of(index, index and 3).packed }
        entityList = EntityList(entityCount)
        boxedList = ArrayList(entityCount)
        entitySet = EntitySet(entityCount)
        boxedSet = HashSet(entityCount * 2)
        churnEntitySet = EntitySet(entityCount)
        churnBoxedSet = HashSet(entityCount * 2)
        for (index in 0 until entityCount) {
            val entity = Entity.of(index, 0)
            churnEntitySet.add(entity)
            churnBoxedSet.add(entity)
        }
        entityCursor = entityCount
        boxedCursor = entityCount
    }
}
