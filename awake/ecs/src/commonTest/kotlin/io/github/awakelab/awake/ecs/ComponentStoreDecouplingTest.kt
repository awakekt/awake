/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ComponentStoreDecouplingTest {
    @Test
    fun payloadAndTagStrategiesCrossGrowthBoundaries() {
        for (capacity in listOf(15, 16, 17, 100, 100_000)) {
            verifyPayloadBoundary(capacity)
            verifyTagBoundary(capacity)
        }
    }

    @Test
    fun payloadStoreMatchesReferenceModelAcrossRandomOperations() {
        val store = ComponentStore(ModelValue::class)
        val expected = mutableMapOf<Int, ModelValue>()
        val random = DeterministicRandom(0x5EED)

        repeat(10_000) { operation ->
            val id = random.nextInt(256)
            val entity = Entity.of(id, 0)
            when (random.nextInt(5)) {
                0, 1 -> {
                    val value = ModelValue(operation)
                    assertEquals(expected.put(id, value), store.add(entity, value))
                }
                2 -> assertEquals(expected.remove(id), store.remove(entity))
                3 -> assertEquals(expected[id], store.get(entity))
                else -> if (operation % 997 == 0) {
                    expected.clear()
                    store.clear()
                }
            }

            if (operation % 100 == 0) {
                assertEquals(expected.size, store.size)
                for (sampleId in 0 until 256 step 17) {
                    assertEquals(expected[sampleId], store.get(Entity.of(sampleId, 0)))
                }
            }
        }
    }

    @Test
    fun worldMatchesReferenceModelAcrossRandomLifecycleOperations() {
        val scenario = WorldReferenceScenario()
        repeat(5_000) { operation -> scenario.step(operation) }
    }

    @Test
    fun worldDestroyAndClearPreservePoolAndRecycledEntitySemantics() {
        val world = World()
        world.registerPool(PooledValue::class) { PooledValue() }
        val first = world.create()
        val value = world.add<PooledValue>(first).also { it.value = 41 }

        world.destroy(first)
        val recycled = world.create()
        val reused = world.add<PooledValue>(recycled)

        assertEquals(first.id, recycled.id)
        assertTrue(recycled.generation > first.generation)
        assertSame(value, reused)
        assertEquals(0, reused.value)

        world.clear()
        assertEquals(0, world.componentCount(PooledValue::class))
        assertFalse(world.isAlive(recycled))
    }

    private fun verifyPayloadBoundary(capacity: Int) {
        val store = ComponentStore(ModelValue::class)
        repeat(capacity) { id -> store.add(Entity.of(id, 0), ModelValue(id)) }
        assertEquals(capacity, store.size)
        assertEquals(ComponentStorageKind.SparseSet, store.storageKind)
        assertTrue(store.hasPayloadStorage)

        val middle = capacity / 2
        assertEquals(ModelValue(middle), store.remove(Entity.of(middle, 0)))
        assertNull(store.get(Entity.of(middle, 0)))
        assertEquals(ModelValue(capacity - 1), store.get(Entity.of(capacity - 1, 0)))
        assertEquals(capacity - 1, store.size)
    }

    private fun verifyTagBoundary(capacity: Int) {
        val store = ComponentStore(BoundaryTag::class)
        repeat(capacity) { id -> store.add(Entity.of(id, 0), BoundaryTag) }
        assertEquals(capacity, store.size)
        assertEquals(ComponentStorageKind.TagSparseSet, store.storageKind)
        assertFalse(store.hasPayloadStorage)

        val middle = capacity / 2
        assertSame(BoundaryTag, store.remove(Entity.of(middle, 0)))
        assertNull(store.get(Entity.of(middle, 0)))
        assertSame(BoundaryTag, store.get(Entity.of(capacity - 1, 0)))
        assertEquals(capacity - 1, store.size)
    }
}

private data class ModelValue(val value: Int)

private data object BoundaryTag : EcsTag

private class PooledValue : Poolable {
    var value: Int = 0

    override fun reset() {
        value = 0
    }
}

private class DeterministicRandom(seed: Int) {
    private var state = seed

    fun nextInt(bound: Int): Int {
        state = state * 1_664_525 + 1_013_904_223
        return (state ushr 1) % bound
    }
}

private class WorldReferenceScenario {
    private val world = World()
    private val alive = mutableListOf<Entity>()
    private val expected = mutableMapOf<Entity, ModelValue>()
    private val destroyedGenerations = mutableMapOf<Int, Int>()
    private val random = DeterministicRandom(0xC0FFEE)

    fun step(operation: Int) {
        when (random.nextInt(7)) {
            0 -> create()
            1, 2 -> add(operation)
            3 -> remove()
            4 -> get()
            5 -> destroy()
            else -> clear(operation)
        }
        if (operation % VERIFY_INTERVAL == 0) verify()
    }

    private fun create() {
        if (alive.size >= MAX_ALIVE_ENTITIES) return
        val entity = world.create()
        destroyedGenerations.remove(entity.id)?.let { generation ->
            assertTrue(entity.generation > generation)
        }
        alive += entity
    }

    private fun add(operation: Int) {
        randomEntity()?.let { entity ->
            val value = ModelValue(operation)
            assertEquals(expected.put(entity, value), world.add(entity, value))
        }
    }

    private fun remove() {
        randomEntity()?.let { entity ->
            assertEquals(expected.remove(entity), world.remove<ModelValue>(entity))
        }
    }

    private fun get() {
        randomEntity()?.let { entity ->
            assertEquals(expected[entity], world.get<ModelValue>(entity))
        }
    }

    private fun destroy() {
        if (alive.isEmpty()) return
        val entity = alive.removeAt(random.nextInt(alive.size))
        destroyedGenerations[entity.id] = entity.generation
        expected.remove(entity)
        assertTrue(world.destroy(entity))
        assertFalse(world.isAlive(entity))
    }

    private fun clear(operation: Int) {
        if (operation == 0 || operation % CLEAR_INTERVAL != 0) return
        world.clear()
        alive.clear()
        expected.clear()
        destroyedGenerations.clear()
    }

    private fun verify() {
        assertEquals(expected.size, world.componentCount(ModelValue::class))
        alive.forEach { entity ->
            assertTrue(world.isAlive(entity))
            assertEquals(expected[entity], world.get<ModelValue>(entity))
        }
    }

    private fun randomEntity(): Entity? = if (alive.isEmpty()) null else alive[random.nextInt(alive.size)]

    private companion object {
        const val MAX_ALIVE_ENTITIES = 64
        const val CLEAR_INTERVAL = 503
        const val VERIFY_INTERVAL = 100
    }
}
