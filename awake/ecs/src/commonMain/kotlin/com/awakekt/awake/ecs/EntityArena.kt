/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

@PublishedApi
internal class EntityArena {
    private var entityGenerations = IntArray(DEFAULT_CAPACITY)
    private var entityAlive = LongArray(DEFAULT_CAPACITY / 64 + 1)

    // An entity's signature has one bit per component type ID, in 64-bit words. Word 0, type IDs
    // 0 to 63, lives here and is all a world with at most 64 types ever reads.
    private var entitySignatures = LongArray(DEFAULT_CAPACITY)

    // Words 1 and up, one array per word indexed like entitySignatures; none until a component with
    // type ID 64 or above is added.
    private var upperSignatures = emptyArray<LongArray>()
    private var entitiesCount = 0

    private val recycledEntityIds = EntityIdStack()

    internal val count: Int
        get() = entitiesCount

    fun create(): Entity {
        val recycledId = recycledEntityIds.pop()
        if (recycledId >= 0) {
            setAlive(recycledId, true)
            return Entity.of(recycledId, entityGenerations[recycledId])
        }

        val nextId = entitiesCount++
        ensureCapacity(nextId)
        entityGenerations[nextId] = 0
        setAlive(nextId, true)
        return Entity.of(nextId, 0)
    }

    fun destroy(entity: Entity): Boolean {
        if (!isAlive(entity)) {
            return false
        }

        val id = entity.id
        setAlive(id, false)
        entityGenerations[id] += 1
        entitySignatures[id] = 0L
        for (words in upperSignatures) words[id] = 0L
        recycledEntityIds.push(id)
        return true
    }

    fun isAlive(entity: Entity): Boolean {
        val id = entity.id
        if (id < 0 || id >= entitiesCount) return false
        return isAlive(id) && entityGenerations[id] == entity.generation
    }

    fun isAlive(id: Int): Boolean {
        val wordIndex = id ushr 6
        return if (wordIndex < entityAlive.size) {
            (entityAlive[wordIndex] and (1L shl (id and 63))) != 0L
        } else {
            false
        }
    }

    fun entity(id: Int): Entity = Entity.of(id, entityGenerations[id])

    fun has(entity: Entity, typeId: ComponentTypeId): Boolean =
        isAlive(entity) && hasComponent(entity.id, typeId.value)

    /** Whether the entity at [id], which must be in range, carries the component type [typeId]. */
    internal fun hasComponent(id: Int, typeId: Int): Boolean =
        (signatureWord(id, typeId ushr 6) and (1L shl (typeId and 63))) != 0L

    /** Word [word] of the signature at [id]: the bits of type IDs `64 * word` to `64 * word + 63`. */
    internal fun signatureWord(id: Int, word: Int): Long = when {
        word == 0 -> entitySignatures[id]
        word <= upperSignatures.size -> upperSignatures[word - 1][id]
        else -> 0L
    }

    /** Whether the entity at [id] carries any component. */
    internal fun hasAnyComponent(id: Int): Boolean = entitySignatures[id] != 0L || upperSignatures.any { it[id] != 0L }

    /** Whether the signature at [id] has every bit of [mask], laid out in words as the signature is. */
    internal fun containsAll(id: Int, mask: LongArray): Boolean {
        for (word in mask.indices) {
            val bits = mask[word]
            if ((signatureWord(id, word) and bits) != bits) return false
        }
        return true
    }

    /** Whether the signature at [id] has at least one bit of [mask]. */
    internal fun intersects(id: Int, mask: LongArray): Boolean {
        for (word in mask.indices) {
            if ((signatureWord(id, word) and mask[word]) != 0L) return true
        }
        return false
    }

    fun markComponentAdded(id: Int, typeId: ComponentTypeId) {
        val words = wordArray(typeId.value ushr 6)
        words[id] = words[id] or (1L shl (typeId.value and 63))
    }

    fun markComponentRemoved(id: Int, typeId: ComponentTypeId) {
        val words = wordArray(typeId.value ushr 6)
        words[id] = words[id] and (1L shl (typeId.value and 63)).inv()
    }

    fun clear() {
        entitiesCount = 0
        entityGenerations.fill(0)
        entityAlive.fill(0L)
        entitySignatures.fill(0L)
        upperSignatures = emptyArray()
        recycledEntityIds.clear()
    }

    private fun setAlive(id: Int, alive: Boolean) {
        val wordIndex = id ushr 6
        val bit = 1L shl (id and 63)
        if (alive) {
            entityAlive[wordIndex] = entityAlive[wordIndex] or bit
        } else {
            entityAlive[wordIndex] = entityAlive[wordIndex] and bit.inv()
        }
    }

    private fun ensureCapacity(id: Int) {
        if (id >= entityGenerations.size) {
            val newCapacity = maxOf(id + 1, entityGenerations.size * 2)
            entityGenerations = entityGenerations.copyOf(newCapacity)
            entitySignatures = entitySignatures.copyOf(newCapacity)
            for (word in upperSignatures.indices) upperSignatures[word] = upperSignatures[word].copyOf(newCapacity)
            entityAlive = entityAlive.copyOf(newCapacity / 64 + 1)
        }
    }

    /** The array holding signature word [word] of every entity, added the first time it is needed. */
    private fun wordArray(word: Int): LongArray {
        if (word == 0) return entitySignatures
        if (word > upperSignatures.size) {
            val existing = upperSignatures
            val capacity = entitySignatures.size
            upperSignatures = Array(word) { index -> existing.getOrElse(index) { LongArray(capacity) } }
        }
        return upperSignatures[word - 1]
    }

    private companion object {
        const val DEFAULT_CAPACITY = 16
    }
}
