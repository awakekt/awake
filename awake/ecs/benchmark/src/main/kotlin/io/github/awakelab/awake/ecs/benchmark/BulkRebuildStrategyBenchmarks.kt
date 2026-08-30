/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs.benchmark

import io.github.awakelab.awake.ecs.ComponentTypeId
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.components.Transform
import io.github.awakelab.awake.scene.rendering.components.MeshRenderer
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

/**
 * Head-to-head incremental vs forced-rebuild family maintenance, as a benchmark-only control.
 *
 * Tests the stop condition in `docs/tasks/2026-08-21-ecs-adaptive-bulk-mutation-plan.md` -- revert
 * if forced rebuild never beats incremental maintenance -- before phases 1 and 2 build a command
 * buffer. Both arms drive the same [ControlFamilyRegistry] over a `World` that maintains no family
 * of its own, so the only difference between them is when family maintenance happens.
 *
 * Run alongside `BulkMutationProfilingBenchmarks.immediateMixedInterleaved`, which supplies the
 * store-only floor (arity 0) and the production incremental anchor (arity 1 and 2) in the same
 * session. An incremental arm that does not land near that anchor means the replica is unfaithful
 * and its rebuild number says nothing.
 *
 * The workload is phase 0's self-restoring interleaved shape -- `2 x batchSize` mutations as
 * per-entity remove-then-add pairs -- so no invocation fixture distorts the measurement.
 */
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
open class BulkRebuildStrategyBenchmarks {
    /** One family-maintenance step per store mutation, mirroring `FamilyRegistry`. */
    @Benchmark
    fun controlIncrementalMixed(state: RebuildStrategyState): Int {
        val world = state.fixture.world
        val typeId = state.fixture.transformTypeId
        val registry = state.registry
        for (packed in state.fixture.batch) {
            val entity = Entity(packed)
            world.remove<Transform>(entity, typeId)
            registry.removeComponent(entity, typeId)
            registry.addComponent(entity, typeId, world.add<Transform>(entity, typeId))
        }
        return state.observedSize()
    }

    /** Stores now, one rebuild per affected family at the end of the batch. */
    @Benchmark
    fun controlRebuildMixed(state: RebuildStrategyState): Int {
        val world = state.fixture.world
        val typeId = state.fixture.transformTypeId
        val registry = state.registry
        for (packed in state.fixture.batch) {
            val entity = Entity(packed)
            world.remove<Transform>(entity, typeId)
            registry.markDirty(typeId)
            world.add<Transform>(entity, typeId)
            registry.markDirty(typeId)
        }
        registry.flush()
        return state.observedSize()
    }
}

/**
 * A phase-0 fixture with `World` family maintenance switched off, plus the control registry both
 * strategies share.
 *
 * At `familyArity = 0` no control family is registered, so both benchmarks measure store work plus
 * the strategy's own bookkeeping and nothing else -- that pair is the overhead floor for the
 * rebuild strategy's dirty-marking.
 */
@State(Scope.Benchmark)
open class RebuildStrategyState {
    @Param("100", "10000", "100000")
    var batchSize: Int = 0

    @Param("1", "100")
    var densityPercent: Int = 0

    @Param("0", "1", "2")
    var familyArity: Int = 0

    internal lateinit var fixture: BulkMutationFixture
    internal lateinit var registry: ControlFamilyRegistry

    @Setup(Level.Trial)
    fun setup() {
        fixture = BulkMutationFixture(batchSize, densityPercent, familyArity, maintainFamilies = false)
        registry = buildControlRegistry(fixture.world, fixture.transformTypeId, familyArity)
    }

    internal fun observedSize(): Int =
        if (registry.familyCount == 0) fixture.resultSize() else registry.familyAt(0).size
}

/** Registers the control family for [familyArity] and populates it, so invocation 1 starts correct. */
internal fun buildControlRegistry(
    world: World,
    transformTypeId: ComponentTypeId,
    familyArity: Int,
): ControlFamilyRegistry {
    val registry = ControlFamilyRegistry()
    when (familyArity) {
        0 -> Unit
        1 -> registry.register(
            ControlFamily1(transformTypeId, world.store(Transform::class)),
            transformTypeId,
        )
        2 -> {
            val meshTypeId = world.typeId(MeshRenderer::class)
            registry.register(
                ControlFamily2(
                    transformTypeId,
                    world.store(Transform::class),
                    meshTypeId,
                    world.store(MeshRenderer::class),
                ),
                transformTypeId,
                meshTypeId,
            )
        }
        else -> error("Unsupported family arity: $familyArity")
    }
    registry.rebuildAll()
    return registry
}
