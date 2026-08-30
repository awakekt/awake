/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs.benchmark

import io.github.awakelab.awake.ecs.ComponentTypeId
import io.github.awakelab.awake.ecs.EcsTag
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.Family1
import io.github.awakelab.awake.ecs.Family2
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
 * Batch-size and family-density profiling for the adaptive bulk structural mutation plan
 * (`docs/tasks/2026-08-21-ecs-adaptive-bulk-mutation-plan.md`, phase 0).
 *
 * Every method here is the `immediate` control: `World.add`/`World.remove` applied one mutation at
 * a time. Forced-incremental, forced-rebuild, and adaptive controls ship with the code they
 * measure (plan phases 1-3); there is nothing to select between yet.
 *
 * `FamilyMaintenanceProfilingBenchmarks` stays the sequential remove-all/add-all anchor at 10k and
 * 100k. This class adds the axes that one lacks: batch sizes below the world size, family density,
 * isolated add/remove halves, and tag-family churn.
 */
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
open class BulkMutationProfilingBenchmarks {
    /** `batchSize` interleaved remove/add pairs. Self-restoring, so it needs no per-invocation setup. */
    @Benchmark
    fun immediateMixedInterleaved(state: BulkMutationState): Int {
        val fixture = state.fixture
        val world = fixture.world
        val typeId = fixture.transformTypeId
        for (packed in fixture.batch) {
            val entity = Entity(packed)
            world.remove<Transform>(entity, typeId)
            world.add<Transform>(entity, typeId)
        }
        return fixture.resultSize()
    }

    /** `batchSize` adds. The batch is emptied outside the timer by [BulkAddOnlyState.clearBatch]. */
    @Benchmark
    fun immediateAddOnly(state: BulkAddOnlyState): Int {
        val fixture = state.fixture
        val world = fixture.world
        val typeId = fixture.transformTypeId
        for (packed in fixture.batch) {
            world.add<Transform>(Entity(packed), typeId)
        }
        return fixture.resultSize()
    }

    /** `batchSize` removals. The batch is refilled outside the timer by [BulkRemoveOnlyState.fillBatch]. */
    @Benchmark
    fun immediateRemoveOnly(state: BulkRemoveOnlyState): Int {
        val fixture = state.fixture
        val world = fixture.world
        val typeId = fixture.transformTypeId
        for (packed in fixture.batch) {
            world.remove<Transform>(Entity(packed), typeId)
        }
        return fixture.resultSize()
    }

    /** Same interleaved shape against a singleton `EcsTag` and its maintained one-arity family. */
    @Benchmark
    fun immediateTagChurnInterleaved(state: BulkTagChurnState): Int {
        val world = state.world
        val typeId = state.tagTypeId
        for (packed in state.batch) {
            val entity = Entity(packed)
            world.remove<BulkChurnTag>(entity, typeId)
            world.add(entity, typeId, BulkChurnTag)
        }
        return state.family.size
    }
}

/**
 * A fixed 100k-entity world whose batch window is a prefix of the entity ids.
 *
 * `densityPercent` is the share of entities carrying `MeshRenderer`, laid out every
 * `100 / densityPercent` ids so any prefix has the same density as the whole world. It is a live
 * axis only at arity 2, where it decides how many of the batch's `Transform` mutations also move
 * an entity in or out of `Family2`. At arity 0 and 1 the two density rows measure the same work
 * on differently sized heaps and serve as a consistency check.
 */
internal class BulkMutationFixture(
    batchSize: Int,
    densityPercent: Int,
    private val familyArity: Int,
    // False leaves `World` with no maintained family, so `RebuildStrategyState` can own family
    // maintenance itself and swap strategies; [resultSize] then reports the `Transform` store size.
    maintainFamilies: Boolean = true,
) {
    val world = World()
    val batch = LongArray(batchSize)
    val transformTypeId: ComponentTypeId
    private var family1: Family1<Transform>? = null
    private var family2: Family2<Transform, MeshRenderer>? = null

    init {
        world.registerPool(Transform::class) { Transform() }
        val memberEvery = PERCENT / densityPercent
        repeat(WORLD_SIZE) { id ->
            val entity = world.create()
            world.add(entity, Transform())
            if (id % memberEvery == memberEvery - 1) {
                world.add(entity, MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material))
            }
            if (id < batchSize) {
                batch[id] = entity.packed
            }
        }
        when {
            !maintainFamilies || familyArity == 0 -> Unit
            familyArity == 1 -> family1 = world.family<Transform>()
            familyArity == 2 -> family2 = world.family<Transform, MeshRenderer>()
            else -> error("Unsupported family arity: $familyArity")
        }
        transformTypeId = world.typeId(Transform::class)
    }

    fun resultSize(): Int =
        when (familyArity) {
            0 -> world.componentCount(Transform::class)
            1 -> family1?.size ?: world.componentCount(Transform::class)
            2 -> family2?.size ?: world.componentCount(Transform::class)
            else -> error("Unsupported family arity: $familyArity")
        }

    internal companion object {
        const val WORLD_SIZE = 100_000
        private const val PERCENT = 100
    }
}

@State(Scope.Benchmark)
open class BulkMutationState {
    @Param("1", "100", "10000", "100000")
    var batchSize: Int = 0

    @Param("1", "100")
    var densityPercent: Int = 0

    @Param("0", "1", "2")
    var familyArity: Int = 0

    internal lateinit var fixture: BulkMutationFixture

    @Setup(Level.Trial)
    fun setup() {
        fixture = BulkMutationFixture(batchSize, densityPercent, familyArity)
    }
}

/**
 * Isolated add half.
 *
 * Restoring the precondition costs one mutation per measured mutation, so it has to run at
 * `Level.Invocation`. Small batches are deliberately absent: at 1 and 100 the untimed restore is
 * the same order as the timed work and JMH's own invocation overhead dominates both. Use
 * [BulkMutationState]'s self-restoring interleaved benchmark for small-batch evidence.
 */
@State(Scope.Benchmark)
open class BulkAddOnlyState {
    @Param("10000", "100000")
    var batchSize: Int = 0

    @Param("1", "100")
    var densityPercent: Int = 0

    @Param("0", "1", "2")
    var familyArity: Int = 0

    internal lateinit var fixture: BulkMutationFixture

    @Setup(Level.Trial)
    fun setup() {
        fixture = BulkMutationFixture(batchSize, densityPercent, familyArity)
    }

    @Setup(Level.Invocation)
    fun clearBatch() {
        val world = fixture.world
        val typeId = fixture.transformTypeId
        for (packed in fixture.batch) {
            world.remove<Transform>(Entity(packed), typeId)
        }
    }
}

/** Isolated remove half; see [BulkAddOnlyState] for why small batches are excluded. */
@State(Scope.Benchmark)
open class BulkRemoveOnlyState {
    @Param("10000", "100000")
    var batchSize: Int = 0

    @Param("1", "100")
    var densityPercent: Int = 0

    @Param("0", "1", "2")
    var familyArity: Int = 0

    internal lateinit var fixture: BulkMutationFixture

    @Setup(Level.Trial)
    fun setup() {
        fixture = BulkMutationFixture(batchSize, densityPercent, familyArity)
    }

    @Setup(Level.Invocation)
    fun fillBatch() {
        val world = fixture.world
        val typeId = fixture.transformTypeId
        for (packed in fixture.batch) {
            world.add<Transform>(Entity(packed), typeId)
        }
    }
}

/**
 * Tag churn against `Family1<BulkChurnTag>`.
 *
 * A tag family is one-arity by construction and every tagged entity is a member, so neither the
 * arity nor the density axis applies here. The tag is never pooled: it is a singleton, and
 * `Poolable.reset()` on a shared instance would be meaningless.
 */
@State(Scope.Benchmark)
open class BulkTagChurnState {
    @Param("1", "100", "10000", "100000")
    var batchSize: Int = 0

    lateinit var world: World
    lateinit var batch: LongArray
    lateinit var family: Family1<BulkChurnTag>
    var tagTypeId: ComponentTypeId = ComponentTypeId(0)

    @Setup(Level.Trial)
    fun setup() {
        world = World()
        batch = LongArray(batchSize)
        repeat(BulkMutationFixture.WORLD_SIZE) { id ->
            val entity = world.create()
            world.add(entity, BulkChurnTag)
            if (id < batchSize) {
                batch[id] = entity.packed
            }
        }
        family = world.family<BulkChurnTag>()
        tagTypeId = world.typeId(BulkChurnTag::class)
    }
}

/** Payload-free churn marker for [BulkTagChurnState]. */
data object BulkChurnTag : EcsTag
