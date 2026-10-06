/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs.benchmark

import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.firstOrNull
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

/** Includes family resolution in each invocation, as a per-frame system does. */
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.SECONDS)
open class TypedQueryBenchmarks {
    @Benchmark
    fun single(state: TypedQueryState): Int {
        var sum = 0
        state.world.queryEach(QueryPosition::class) { _, position -> sum += position.x }
        return sum
    }

    @Benchmark
    fun pair(state: TypedQueryState): Int {
        var sum = 0
        state.world.queryEach(QueryPosition::class, QueryVelocity::class) { _, position, velocity ->
            sum += position.x + velocity.x
        }
        return sum
    }

    @Benchmark
    fun first(state: TypedQueryState): Int = state.world.firstOrNull<QueryPosition>()!!.x

    @Benchmark
    fun reifiedSingle(state: TypedQueryState): Int {
        var sum = 0
        state.world.queryEach<QueryPosition> { _, position -> sum += position.x }
        return sum
    }

    @Benchmark
    fun reifiedPair(state: TypedQueryState): Int {
        var sum = 0
        state.world.queryEach<QueryPosition, QueryVelocity> { _, position, velocity ->
            sum += position.x + velocity.x
        }
        return sum
    }
}

@State(Scope.Thread)
open class TypedQueryState {
    @Param("32", "10000")
    var entityCount: Int = 0

    lateinit var world: World

    @Setup
    fun setup() {
        world = World()
        repeat(entityCount) {
            val entity = world.create()
            world.add(entity, QueryPosition(it))
            world.add(entity, QueryVelocity(1))
        }
        world.family(QueryPosition::class)
        world.family(QueryPosition::class, QueryVelocity::class)
    }
}

class QueryPosition(val x: Int)
class QueryVelocity(val x: Int)
