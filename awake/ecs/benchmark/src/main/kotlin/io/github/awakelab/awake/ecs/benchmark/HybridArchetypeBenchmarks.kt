/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs.benchmark

import io.github.awakelab.awake.ecs.ComponentStore
import io.github.awakelab.awake.ecs.EcsTag
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.Family2
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.rendering.mesh.MeshRenderer
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
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * Decision benchmark for the hybrid-storage proposal.
 *
 * This is deliberately a benchmark-only prototype, not a second production ECS. Stable render
 * rows live in a contiguous table while [BenchmarkDynamicTag] remains a separate sparse set.
 * Its measurements decide whether a public storage migration is worthwhile; do not promote this
 * shape into `:awake:ecs` until it wins against the existing sparse-set World on the workloads
 * that matter.
 */
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
@Suppress("TooManyFunctions")
open class HybridArchetypeBenchmarks {

    @Benchmark
    fun pureArchetypeStableRowIteration(state: PureArchetypeState, blackhole: Blackhole): Int {
        val storage = state.storage
        var tableIndex = 0
        while (tableIndex < storage.tableCount) {
            val table = storage.tableAt(tableIndex)
            var row = 0
            while (row < table.size) {
                blackhole.consume(table.transformAt(row))
                blackhole.consume(table.meshRendererAt(row))
                row += 1
            }
            tableIndex += 1
        }
        return storage.size
    }

    @Benchmark
    fun hybridStableRowIteration(state: HybridArchetypeState, blackhole: Blackhole): Int {
        val table = state.table
        var index = 0
        while (index < table.size) {
            blackhole.consume(table.transformAt(index))
            blackhole.consume(table.meshRendererAt(index))
            index += 1
        }
        return table.size
    }

    @Benchmark
    fun currentStableFamilyIteration(state: AwakeQueryState, blackhole: Blackhole): Int {
        val family = state.family
        val transforms = family.componentsA()
        val meshRenderers = family.componentsB()
        var index = 0
        while (index < family.size) {
            blackhole.consume(transforms[index])
            blackhole.consume(meshRenderers[index])
            index += 1
        }
        return family.size
    }

    @Benchmark
    fun hybridDynamicTagChurn(state: HybridArchetypeState): Int {
        state.tags.removeAll(state.entityIds)
        state.tags.addAll(state.entityIds)
        return state.tags.size
    }

    @Benchmark
    fun pureArchetypeDynamicTagChurn(state: PureArchetypeState): Int {
        for (entityId in state.entityIds) state.storage.removeTag(entityId, PRIMARY_TAG)
        for (entityId in state.entityIds) state.storage.addTag(entityId, PRIMARY_TAG)
        return state.storage.size
    }

    @Benchmark
    fun currentRawSparseTagChurn(state: RawSparseTagState): Int {
        for (entity in state.entities) {
            state.tags.remove(entity)
        }
        for (entity in state.entities) {
            state.tags.add(entity, BenchmarkDynamicTag)
        }
        return state.tags.size
    }

    @Benchmark
    fun currentRawComponentChurn(state: RawComponentState): Int {
        for (entity in state.entities) {
            state.components.remove(entity)
        }
        for (entity in state.entities) {
            state.components.add(entity, BenchmarkDynamicComponent)
        }
        return state.components.size
    }

    @Benchmark
    fun currentWorldDynamicTagChurn(state: SparseSetDynamicTagState): Int {
        val world = state.world
        for (entity in state.entities) {
            world.remove<BenchmarkDynamicTag>(entity)
        }
        for (entity in state.entities) {
            world.add(entity, BenchmarkDynamicTag)
        }
        return world.componentCount(BenchmarkDynamicTag::class)
    }

    @Benchmark
    fun currentWorldComponentChurn(state: WorldComponentState): Int {
        val world = state.world
        for (entity in state.entities) {
            world.remove<BenchmarkDynamicComponent>(entity)
        }
        for (entity in state.entities) {
            world.add(entity, BenchmarkDynamicComponent)
        }
        return world.componentCount(BenchmarkDynamicComponent::class)
    }

    @Benchmark
    fun currentFamilyTagIteration(state: FamilyTagState, blackhole: Blackhole): Int {
        state.family.forEachComponents { component, tag ->
            blackhole.consume(component)
            blackhole.consume(tag)
        }
        return state.family.size
    }

    @Benchmark
    fun currentFamilyTagChurn(state: FamilyTagState): Int {
        for (entity in state.entities) {
            state.world.remove<BenchmarkDynamicTag>(entity)
        }
        for (entity in state.entities) {
            state.world.add(entity, BenchmarkDynamicTag)
        }
        return state.family.size
    }
}

@State(Scope.Benchmark)
open class PureArchetypeState {
    @Param("10000", "100000")
    var entityCount: Int = 0

    lateinit var storage: PureArchetypeStorage
    lateinit var entityIds: IntArray

    @Setup(Level.Trial)
    fun setup() {
        storage = PureArchetypeStorage(entityCount, entityCount)
        entityIds = IntArray(entityCount)
        repeat(entityCount) { id ->
            entityIds[id] = id
            storage.add(
                entityId = id,
                transform = Transform(),
                meshRenderer = MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material),
                tagSignature = PRIMARY_TAG,
            )
        }
    }
}

@State(Scope.Benchmark)
open class HybridArchetypeState {
    @Param("10000", "100000")
    var entityCount: Int = 0

    lateinit var table: StableRenderArchetypeTable
    lateinit var entityIds: IntArray
    lateinit var tags: SparseTagSet

    @Setup(Level.Trial)
    fun setup() {
        table = StableRenderArchetypeTable(entityCount)
        entityIds = IntArray(entityCount)
        tags = SparseTagSet()
        repeat(entityCount) { id ->
            entityIds[id] = id
            table.add(id, Transform(), MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material))
            tags.add(id)
        }
    }
}

@State(Scope.Benchmark)
open class SparseSetDynamicTagState {
    @Param("10000", "100000")
    var entityCount: Int = 0

    lateinit var world: World
    lateinit var entities: List<Entity>

    @Setup(Level.Iteration)
    fun setup() {
        world = World()
        entities = List(entityCount) {
            world.create().also { entity ->
                world.add(entity, Transform())
                world.add(entity, MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material))
                world.add(entity, BenchmarkDynamicTag)
            }
        }
    }
}

@State(Scope.Benchmark)
open class RawSparseTagState {
    @Param("10000", "100000")
    var entityCount: Int = 0

    lateinit var entities: List<Entity>
    lateinit var tags: ComponentStore<BenchmarkDynamicTag>

    @Setup(Level.Trial)
    fun setup() {
        tags = ComponentStore(BenchmarkDynamicTag::class)
        entities = List(entityCount) { id ->
            Entity.of(id, 0).also { tags.add(it, BenchmarkDynamicTag) }
        }
    }
}

@State(Scope.Benchmark)
open class RawComponentState {
    @Param("10000", "100000")
    var entityCount: Int = 0

    lateinit var entities: List<Entity>
    lateinit var components: ComponentStore<BenchmarkDynamicComponent>

    @Setup(Level.Iteration)
    fun setup() {
        components = ComponentStore(BenchmarkDynamicComponent::class)
        entities = List(entityCount) { id ->
            Entity.of(id, 0).also { components.add(it, BenchmarkDynamicComponent) }
        }
    }
}

@State(Scope.Benchmark)
open class WorldComponentState {
    @Param("10000", "100000")
    var entityCount: Int = 0

    lateinit var world: World
    lateinit var entities: List<Entity>

    @Setup(Level.Iteration)
    fun setup() {
        world = World()
        entities = List(entityCount) {
            world.create().also { entity ->
                world.add(entity, Transform())
                world.add(entity, MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material))
                world.add(entity, BenchmarkDynamicComponent)
            }
        }
    }
}

@State(Scope.Benchmark)
open class FamilyTagState {
    @Param("10000", "100000")
    var entityCount: Int = 0

    lateinit var world: World
    lateinit var entities: List<Entity>
    lateinit var family: Family2<BenchmarkDynamicComponent, BenchmarkDynamicTag>

    @Setup(Level.Iteration)
    fun setup() {
        world = World()
        entities = List(entityCount) {
            world.create().also { entity ->
                world.add(entity, BenchmarkDynamicComponent)
                world.add(entity, BenchmarkDynamicTag)
            }
        }
        family = world.family(BenchmarkDynamicComponent::class, BenchmarkDynamicTag::class)
    }
}

/** A dense, stable archetype row for the exact Transform + MeshRenderer benchmark shape. */
class StableRenderArchetypeTable(initialCapacity: Int) {
    private var entityIds = IntArray(initialCapacity)
    private var transforms = arrayOfNulls<Transform>(initialCapacity)
    private var meshRenderers = arrayOfNulls<MeshRenderer>(initialCapacity)

    var size: Int = 0
        private set

    fun add(entityId: Int, transform: Transform, meshRenderer: MeshRenderer) {
        require(size < entityIds.size) { "Benchmark table capacity exceeded" }
        entityIds[size] = entityId
        transforms[size] = transform
        meshRenderers[size] = meshRenderer
        size += 1
    }

    fun transformAt(index: Int): Transform = requireNotNull(transforms[index])

    fun meshRendererAt(index: Int): MeshRenderer = requireNotNull(meshRenderers[index])
}

/** Sparse-set tag storage: adding/removing a dynamic marker never mutates the archetype rows. */
class SparseTagSet {
    private var sparse = IntArray(0)
    private var dense = IntArray(INITIAL_CAPACITY)

    var size: Int = 0
        private set

    fun add(entityId: Int) {
        val denseIndex = indexOf(entityId)
        if (denseIndex >= 0) return
        ensureSparseCapacity(entityId)
        ensureDenseCapacity(size + 1)
        sparse[entityId] = size
        dense[size] = entityId
        size += 1
    }

    fun removeAll(entityIds: IntArray) {
        for (entityId in entityIds) remove(entityId)
    }

    fun addAll(entityIds: IntArray) {
        for (entityId in entityIds) add(entityId)
    }

    fun remove(entityId: Int) {
        val denseIndex = indexOf(entityId)
        if (denseIndex < 0) return
        val lastIndex = size - 1
        if (denseIndex != lastIndex) {
            val lastEntityId = dense[lastIndex]
            dense[denseIndex] = lastEntityId
            sparse[lastEntityId] = denseIndex
        }
        sparse[entityId] = ABSENT
        size -= 1
    }

    fun contains(entityId: Int): Boolean = indexOf(entityId) >= 0

    fun entityAt(index: Int): Int = dense[index]

    private fun indexOf(entityId: Int): Int =
        if (entityId >= 0 && entityId < sparse.size) sparse[entityId] else ABSENT

    private fun ensureSparseCapacity(entityId: Int) {
        if (entityId < sparse.size) return
        val oldSize = sparse.size
        val newSize = maxOf(entityId + 1, maxOf(INITIAL_CAPACITY, oldSize * GROWTH_FACTOR))
        sparse = sparse.copyOf(newSize)
        sparse.fill(ABSENT, oldSize, newSize)
    }

    private fun ensureDenseCapacity(required: Int) {
        if (required <= dense.size) return
        dense = dense.copyOf(maxOf(required, dense.size * GROWTH_FACTOR))
    }

    private companion object {
        const val ABSENT = -1
        const val INITIAL_CAPACITY = 16
        const val GROWTH_FACTOR = 2
    }
}

data object BenchmarkDynamicTag : EcsTag

data object BenchmarkDynamicComponent

internal const val PRIMARY_TAG: Int = 1
