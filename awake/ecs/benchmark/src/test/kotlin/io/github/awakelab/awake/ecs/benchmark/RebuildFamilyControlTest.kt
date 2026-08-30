/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs.benchmark

import io.github.awakelab.awake.ecs.ComponentTypeId
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.Family1
import io.github.awakelab.awake.ecs.Family2
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.components.Transform
import io.github.awakelab.awake.scene.rendering.components.MeshRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Correctness gate for the forced-rebuild control: a rebuild that is fast because it drops or
 * duplicates members would make the head-to-head meaningless. Every test asserts the rebuilt family
 * agrees with both the incremental control and the real [Family1]/[Family2] that [World] maintains.
 *
 * Dense *order* is not asserted -- production swap-removal already reorders a family. Membership,
 * per-entity value identity, and sparse-index repair are the contract.
 */
class RebuildFamilyControlTest {
    @Test
    fun rebuildMatchesIncrementalAndProductionForOneArityChurn() {
        val world = newWorld()
        val entities = populate(world, count = 64, meshEvery = 3)
        val transformTypeId = world.typeId(Transform::class)
        val production = world.family<Transform>()
        val incremental = transformRegistry(world, arity = 1)
        val rebuild = transformRegistry(world, arity = 1)

        churnTransforms(world, entities, transformTypeId, incremental, rebuild)
        rebuild.flush()

        assertEquals(production.size, rebuild.familyAt(0).size)
        assertFamiliesAgree(snapshotOf(production), incremental.familyAt(0), rebuild.familyAt(0))
        assertSparseRepaired(rebuild.familyAt(0), entities)
    }

    @Test
    fun rebuildMatchesIncrementalAndProductionForTwoArityChurn() {
        val world = newWorld()
        val entities = populate(world, count = 64, meshEvery = 3)
        val transformTypeId = world.typeId(Transform::class)
        val production = world.family<Transform, MeshRenderer>()
        val incremental = transformRegistry(world, arity = 2)
        val rebuild = transformRegistry(world, arity = 2)

        churnTransforms(world, entities, transformTypeId, incremental, rebuild)
        rebuild.flush()

        assertEquals(production.size, rebuild.familyAt(0).size)
        assertFamiliesAgree(snapshotOf(production), incremental.familyAt(0), rebuild.familyAt(0))
        assertSparseRepaired(rebuild.familyAt(0), entities)
    }

    /**
     * A rebuild reads packed entities straight out of the store, so a recycled id carrying a newer
     * generation must not resurrect the destroyed handle in the dense array or the sparse index.
     */
    @Test
    fun rebuildMatchesAcrossDestroyAndRecycledGenerations() {
        val world = newWorld()
        val entities = populate(world, count = 32, meshEvery = 2)
        val transformTypeId = world.typeId(Transform::class)
        val meshTypeId = world.typeId(MeshRenderer::class)
        val production = world.family<Transform, MeshRenderer>()
        val incremental = transformRegistry(world, arity = 2)
        val rebuild = transformRegistry(world, arity = 2)

        val destroyed = entities.filterIndexed { index, _ -> index % 4 == 0 }
        destroyed.forEach { entity ->
            listOf(transformTypeId, meshTypeId).forEach { typeId ->
                incremental.removeComponent(entity, typeId)
                rebuild.markDirty(typeId)
            }
            world.destroy(entity)
        }
        val recycled = destroyed.map { recreate(world, transformTypeId, meshTypeId, incremental, rebuild) }
        rebuild.flush()

        assertEquals(destroyed.map(Entity::id).toSet(), recycled.map(Entity::id).toSet())
        assertFamiliesAgree(snapshotOf(production), incremental.familyAt(0), rebuild.familyAt(0))
        // Checked on packed handles, not the sparse index: production family caches key on
        // `entity.id` alone and rely on `World.isAlive` to reject a stale generation.
        val members = denseHandles(rebuild.familyAt(0))
        assertTrue(destroyed.none { it.packed in members }, "A destroyed handle survived the rebuild")
        assertTrue(recycled.all { it.packed in members }, "A recycled handle is missing from the rebuild")
    }

    /** A tag column keeps one canonical singleton, so a rebuild must not materialize per-entity values. */
    @Test
    fun rebuildMatchesIncrementalAndProductionForTagFamily() {
        val world = World()
        val entities = List(TAG_ENTITY_COUNT) { world.create().also { entity -> world.add(entity, BulkChurnTag) } }
        val tagTypeId = world.typeId(BulkChurnTag::class)
        val production = world.family<BulkChurnTag>()
        val incremental = tagRegistry(world, tagTypeId)
        val rebuild = tagRegistry(world, tagTypeId)

        entities.filterIndexed { index, _ -> index % 3 != 0 }.forEach { entity ->
            world.remove<BulkChurnTag>(entity, tagTypeId)
            incremental.removeComponent(entity, tagTypeId)
            rebuild.markDirty(tagTypeId)
        }
        rebuild.flush()

        val rebuilt = rebuild.familyAt(0)
        assertEquals(production.size, rebuilt.size)
        assertFamiliesAgree(snapshotOf(production), incremental.familyAt(0), rebuilt)
        assertSparseRepaired(rebuilt, entities)
        repeat(rebuilt.size) { index -> assertSame(BulkChurnTag, rebuilt.valueAt(index)) }
    }

    private fun newWorld(): World = World().apply { registerPool(Transform::class) { Transform() } }

    private fun populate(world: World, count: Int, meshEvery: Int): List<Entity> = List(count) { index ->
        world.create().also { entity ->
            world.add(entity, Transform())
            if (index % meshEvery == 0) {
                world.add(entity, MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material))
            }
        }
    }

    private fun recreate(
        world: World,
        transformTypeId: ComponentTypeId,
        meshTypeId: ComponentTypeId,
        incremental: ControlFamilyRegistry,
        rebuild: ControlFamilyRegistry,
    ): Entity {
        val entity = world.create()
        val transform = Transform()
        val meshRenderer = MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material)
        // One notification per store mutation, as `World.addInternal` does; batching both
        // notifications after both adds appends the entity to a two-arity family twice.
        world.add(entity, transform)
        incremental.addComponent(entity, transformTypeId, transform)
        world.add(entity, meshRenderer)
        incremental.addComponent(entity, meshTypeId, meshRenderer)
        rebuild.markDirty(transformTypeId)
        return entity
    }

    private fun transformRegistry(world: World, arity: Int): ControlFamilyRegistry =
        buildControlRegistry(world, world.typeId(Transform::class), arity)

    private fun tagRegistry(world: World, tagTypeId: ComponentTypeId): ControlFamilyRegistry =
        ControlFamilyRegistry().apply {
            register(ControlFamily1(tagTypeId, world.store(BulkChurnTag::class)), tagTypeId)
            rebuildAll()
        }

    /**
     * Removes a scattered subset, re-adds part of it, then removes another subset, so the dense
     * arrays end up reordered by swap-removal rather than left in creation order.
     */
    private fun churnTransforms(
        world: World,
        entities: List<Entity>,
        transformTypeId: ComponentTypeId,
        incremental: ControlFamilyRegistry,
        rebuild: ControlFamilyRegistry,
    ) {
        entities.filterIndexed { index, _ -> index % 2 == 0 }.forEach { entity ->
            world.remove<Transform>(entity, transformTypeId)
            incremental.removeComponent(entity, transformTypeId)
            rebuild.markDirty(transformTypeId)
        }
        entities.filterIndexed { index, _ -> index % 4 == 0 }.forEach { entity ->
            incremental.addComponent(entity, transformTypeId, world.add<Transform>(entity, transformTypeId))
            rebuild.markDirty(transformTypeId)
        }
        entities.filterIndexed { index, _ -> index % 5 == 1 }.forEach { entity ->
            world.remove<Transform>(entity, transformTypeId)
            incremental.removeComponent(entity, transformTypeId)
            rebuild.markDirty(transformTypeId)
        }
    }

    private fun <A : Any> snapshotOf(family: Family1<A>): FamilySnapshot {
        val result = HashMap<Long, Pair<Any, Any?>>()
        family.forEach { entity, component -> result[entity.packed] = component to null }
        assertEquals(family.size, result.size, "Production family holds a duplicate entity")
        return result
    }

    private fun <A : Any, B : Any> snapshotOf(family: Family2<A, B>): FamilySnapshot {
        val result = HashMap<Long, Pair<Any, Any?>>()
        family.forEach { entity, first, second -> result[entity.packed] = first to second }
        assertEquals(family.size, result.size, "Production family holds a duplicate entity")
        return result
    }

    private fun snapshotOf(family: ControlFamily): FamilySnapshot {
        val result = HashMap<Long, Pair<Any, Any?>>()
        repeat(family.size) { index ->
            result[family.entityAt(index).packed] = family.valueAt(index) to family.secondValueAt(index)
        }
        assertEquals(family.size, result.size, "Control family holds a duplicate entity")
        return result
    }

    private fun assertFamiliesAgree(expected: FamilySnapshot, vararg controls: ControlFamily) {
        controls.forEach { control ->
            val actual = snapshotOf(control)
            assertEquals(expected.keys, actual.keys)
            expected.forEach { (packed, values) ->
                assertSame(values.first, actual.getValue(packed).first, "First column differs for $packed")
                assertSame(values.second, actual.getValue(packed).second, "Second column differs for $packed")
            }
        }
    }

    private fun denseHandles(family: ControlFamily): Set<Long> {
        val members = HashSet<Long>()
        repeat(family.size) { index -> members.add(family.entityAt(index).packed) }
        return members
    }

    /** A stale sparse slot would still address a live dense row, so check every id both ways. */
    private fun assertSparseRepaired(family: ControlFamily, allEntities: List<Entity>) {
        val members = denseHandles(family)
        allEntities.forEach { entity ->
            val index = family.indexOf(entity)
            if (entity.packed in members) {
                assertEquals(entity.packed, family.entityAt(index).packed)
            } else {
                assertEquals(ControlSparseIndex.ABSENT, index, "Stale sparse slot for $entity")
            }
        }
    }

    private companion object {
        const val TAG_ENTITY_COUNT = 48
    }
}

private typealias FamilySnapshot = Map<Long, Pair<Any, Any?>>
