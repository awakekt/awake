/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs.benchmark

import com.awakekt.awake.ecs.EcsTag
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
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * Query controls for 256 optional-tag combinations across the same stable render rows.
 *
 * The query requires tag zero and excludes tag one, matching exactly one quarter of the
 * entities. Awake measures its production maintained family, the hybrid control intersects
 * independent sparse tag sets, and the pure-archetype control skips whole non-matching tables.
 */
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
open class StorageFragmentationBenchmarks {
    @Benchmark
    fun currentMaintainedFamilyQuery(state: AwakeFragmentationState, blackhole: Blackhole): Int {
        state.family.forEach { entity -> blackhole.consume(entity.packed) }
        return state.family.size
    }

    @Benchmark
    fun hybridSparseTagQuery(state: HybridFragmentationState, blackhole: Blackhole): Int {
        val required = state.tags[REQUIRED_TAG_INDEX]
        val excluded = state.tags[EXCLUDED_TAG_INDEX]
        var matches = 0
        var index = 0
        while (index < required.size) {
            val entityId = required.entityAt(index)
            if (!excluded.contains(entityId)) {
                blackhole.consume(entityId.toLong())
                matches += 1
            }
            index += 1
        }
        return matches
    }

    @Benchmark
    fun pureArchetypeTableQuery(state: PureArchetypeFragmentationState, blackhole: Blackhole): Int {
        val storage = state.storage
        var matches = 0
        var tableIndex = 0
        while (tableIndex < storage.tableCount) {
            val table = storage.tableAt(tableIndex)
            if (table.matches(PRIMARY_TAG, EXCLUDED_TAG)) {
                var row = 0
                while (row < table.size) {
                    blackhole.consume(table.entityAt(row).toLong())
                    matches += 1
                    row += 1
                }
            }
            tableIndex += 1
        }
        return matches
    }
}

@State(Scope.Benchmark)
open class PureArchetypeFragmentationState {
    @Param("10000", "100000")
    var entityCount: Int = 0

    lateinit var storage: PureArchetypeStorage

    @Setup(Level.Trial)
    fun setup() {
        val perTableCapacity = entityCount / SIGNATURE_COUNT + TABLE_CAPACITY_SLACK
        storage = PureArchetypeStorage(entityCount, perTableCapacity)
        repeat(entityCount) { id ->
            storage.add(
                entityId = id,
                transform = Transform(),
                meshRenderer = MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material),
                tagSignature = id and ALL_TAGS_MASK,
            )
        }
        check(storage.tableCount == SIGNATURE_COUNT)
    }
}

@State(Scope.Benchmark)
open class HybridFragmentationState {
    @Param("10000", "100000")
    var entityCount: Int = 0

    lateinit var table: StableRenderArchetypeTable
    lateinit var tags: Array<SparseTagSet>

    @Setup(Level.Trial)
    fun setup() {
        table = StableRenderArchetypeTable(entityCount)
        tags = Array(TAG_COUNT) { SparseTagSet() }
        repeat(entityCount) { id ->
            table.add(id, Transform(), MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material))
            addSparseTags(tags, id, id and ALL_TAGS_MASK)
        }
    }
}

@State(Scope.Benchmark)
open class AwakeFragmentationState {
    @Param("10000", "100000")
    var entityCount: Int = 0

    lateinit var world: World
    lateinit var family: Family

    @Setup(Level.Trial)
    fun setup() {
        world = World()
        repeat(entityCount) { id ->
            val entity = world.create()
            world.add(entity, Transform())
            world.add(entity, MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material))
            addAwakeTags(world, entity, id and ALL_TAGS_MASK)
        }
        family = world.family {
            all(BenchmarkTag0::class)
            exclude(BenchmarkTag1::class)
        }
        check(family.size == entityCount / EXPECTED_MATCH_DIVISOR)
    }
}

private fun addSparseTags(tags: Array<SparseTagSet>, entityId: Int, signature: Int) {
    var tagIndex = 0
    while (tagIndex < TAG_COUNT) {
        if (signature and (1 shl tagIndex) != 0) tags[tagIndex].add(entityId)
        tagIndex += 1
    }
}

private fun addAwakeTags(world: World, entity: Entity, signature: Int) {
    if (signature and TAG_0_MASK != 0) world.add(entity, BenchmarkTag0)
    if (signature and TAG_1_MASK != 0) world.add(entity, BenchmarkTag1)
    if (signature and TAG_2_MASK != 0) world.add(entity, BenchmarkTag2)
    if (signature and TAG_3_MASK != 0) world.add(entity, BenchmarkTag3)
    if (signature and TAG_4_MASK != 0) world.add(entity, BenchmarkTag4)
    if (signature and TAG_5_MASK != 0) world.add(entity, BenchmarkTag5)
    if (signature and TAG_6_MASK != 0) world.add(entity, BenchmarkTag6)
    if (signature and TAG_7_MASK != 0) world.add(entity, BenchmarkTag7)
}

internal data object BenchmarkTag0 : EcsTag

internal data object BenchmarkTag1 : EcsTag

internal data object BenchmarkTag2 : EcsTag

internal data object BenchmarkTag3 : EcsTag

internal data object BenchmarkTag4 : EcsTag

internal data object BenchmarkTag5 : EcsTag

internal data object BenchmarkTag6 : EcsTag

internal data object BenchmarkTag7 : EcsTag

private const val TAG_COUNT = 8
private const val SIGNATURE_COUNT = 1 shl TAG_COUNT
private const val ALL_TAGS_MASK = SIGNATURE_COUNT - 1
private const val TAG_0_MASK = 1 shl 0
private const val TAG_1_MASK = 1 shl 1
private const val TAG_2_MASK = 1 shl 2
private const val TAG_3_MASK = 1 shl 3
private const val TAG_4_MASK = 1 shl 4
private const val TAG_5_MASK = 1 shl 5
private const val TAG_6_MASK = 1 shl 6
private const val TAG_7_MASK = 1 shl 7
private const val EXCLUDED_TAG = TAG_1_MASK
private const val REQUIRED_TAG_INDEX = 0
private const val EXCLUDED_TAG_INDEX = 1
private const val EXPECTED_MATCH_DIVISOR = 4
private const val TABLE_CAPACITY_SLACK = 2
