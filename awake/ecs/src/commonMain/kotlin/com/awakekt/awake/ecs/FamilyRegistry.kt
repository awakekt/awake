/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.jvm.JvmInline
import kotlin.reflect.KClass

/**
 * Owns every maintained [FamilyCache] -- the typed [Family1Cache]/[Family2Cache] instances
 * plus the arbitrary-arity [FamilySpecCache] ones -- and keeps them all in sync with
 * [World]'s structural changes (entity destroy, component add/replace/remove).
 *
 * Extracted out of [World] so entity/component lifecycle and family-cache bookkeeping don't
 * live in the same 400+ line file; this class needs read access to [World]'s stores and
 * type-id assignment helpers.
 */
internal class FamilyRegistry(
    private val world: World,
    private val queryCollector: QueryCollector,
) {
    private val families = mutableMapOf<FamilyKey, FamilyCache>()
    private val familySpecCaches = mutableMapOf<FamilySpec, FamilySpecCache>()
    private val excludedFamily1s = mutableMapOf<ExcludedFamilyKey, Family1<*>>()
    private val excludedFamily2s = mutableMapOf<ExcludedFamilyKey, Family2<*, *>>()
    private val excludedCaches = ArrayList<FamilyCache>()

    // Index wrappers by type ID so a warmed lookup allocates neither a wrapper nor a boxed
    // FamilyKey. Pair rows are ordered: (A, B) and (B, A) expose different typed columns.
    private var singleFamilies = arrayOfNulls<Family1<*>>(16)
    private var pairFamilies = arrayOfNulls<Array<Family2<*, *>?>>(16)

    /** Index of families that care about a specific component type, indexed by
     * [ComponentTypeId.value]. Using an array of lists avoids [KClass] map lookups
     * on the structural-change hot path. */
    private var familiesByComponentId = arrayOfNulls<MutableList<FamilyCache>>(16)

    fun clear() {
        families.clear()
        familySpecCaches.clear()
        excludedFamily1s.clear()
        excludedFamily2s.clear()
        excludedCaches.clear()
        familiesByComponentId.fill(null)
        singleFamilies.fill(null)
        pairFamilies.fill(null)
    }

    @Suppress("UNCHECKED_CAST") // The ID was resolved from A in this world generation.
    fun <A : Any> cachedFamily(typeId: ComponentTypeId): Family1<A>? =
        singleFamilies.getOrNull(typeId.value) as Family1<A>?

    @Suppress("UNCHECKED_CAST") // The ordered IDs were resolved from A and B in this generation.
    fun <A : Any, B : Any> cachedFamily(typeIdA: ComponentTypeId, typeIdB: ComponentTypeId): Family2<A, B>? =
        pairFamilies.getOrNull(typeIdA.value)?.getOrNull(typeIdB.value) as Family2<A, B>?

    // A type ID identifies exactly A until clear(), which also discards these wrappers.
    @Suppress("UNCHECKED_CAST")
    fun <A : Any> family(type: KClass<A>): Family1<A> {
        val typeId = world.typeId(type)
        val id = typeId.value
        if (id >= singleFamilies.size) {
            singleFamilies = singleFamilies.copyOf(maxOf(id + 1, singleFamilies.size * 2))
        }
        val existing = singleFamilies[id]
        if (existing != null) return existing as Family1<A>
        val key = FamilyKey.single(typeId)
        val cache = buildFamily(type, typeId).also(::indexFamily)
        families[key] = cache
        return Family1(cache).also { singleFamilies[id] = it }
    }

    // Ordered IDs identify exactly A and B until clear(), which discards every pair row.
    @Suppress("UNCHECKED_CAST")
    fun <A : Any, B : Any> family(typeA: KClass<A>, typeB: KClass<B>): Family2<A, B> {
        val typeIdA = world.typeId(typeA)
        val typeIdB = world.typeId(typeB)
        val idA = typeIdA.value
        val idB = typeIdB.value
        val row = pairRow(idA, idB)
        val existing = row[idB]
        if (existing != null) return existing as Family2<A, B>
        val key = FamilyKey.pair(typeIdA, typeIdB)
        val cache = buildFamily(typeA, typeIdA, typeB, typeIdB).also(::indexFamily)
        families[key] = cache
        return Family2(cache).also { row[idB] = it }
    }

    private fun pairRow(idA: Int, idB: Int): Array<Family2<*, *>?> {
        if (idA >= pairFamilies.size) {
            pairFamilies = pairFamilies.copyOf(maxOf(idA + 1, pairFamilies.size * 2))
        }
        val existing = pairFamilies[idA]
        val row = when {
            existing == null -> arrayOfNulls(maxOf(16, idB + 1))
            idB >= existing.size -> existing.copyOf(maxOf(idB + 1, existing.size * 2))
            else -> return existing
        }
        pairFamilies[idA] = row
        return row
    }

    fun familySpecCache(spec: FamilySpec): FamilySpecCache = familySpecCaches.getOrPut(spec) {
        buildFamilySpecCache(spec).also(::indexFamily)
    }

    // The key binds exact type and exclude IDs within this world generation until clear().
    @Suppress("UNCHECKED_CAST")
    fun <A : Any> familyExcluding(type: KClass<A>, exclude: Set<KClass<out Any>>): Family1<A> {
        if (exclude.isEmpty()) return family(type)
        val typeId = world.typeId(type)
        val key = excludedKey(typeId.value, SINGLE_TYPE_SENTINEL, exclude)
        val existing = excludedFamily1s[key] as Family1<A>?
        return existing ?: Family1(
            buildFamily(type, typeId, exclude).also {
                indexFamily(it)
                excludedCaches.add(it)
            },
        ).also { excludedFamily1s[key] = it }
    }

    // The key binds exact ordered pair and exclude IDs within this world generation until clear().
    @Suppress("UNCHECKED_CAST")
    fun <A : Any, B : Any> familyExcluding(
        typeA: KClass<A>,
        typeB: KClass<B>,
        exclude: Set<KClass<out Any>>,
    ): Family2<A, B> {
        if (exclude.isEmpty()) return family(typeA, typeB)
        val typeIdA = world.typeId(typeA)
        val typeIdB = world.typeId(typeB)
        val key = excludedKey(typeIdA.value, typeIdB.value, exclude)
        val existing = excludedFamily2s[key] as Family2<A, B>?
        return existing ?: Family2(
            buildFamily(typeA, typeIdA, typeB, typeIdB, exclude).also {
                indexFamily(it)
                excludedCaches.add(it)
            },
        ).also { excludedFamily2s[key] = it }
    }

    private fun excludedKey(typeIdA: Int, typeIdB: Int, exclude: Set<KClass<out Any>>): ExcludedFamilyKey {
        val ids = IntArray(exclude.size)
        var index = 0
        for (excludedType in exclude) {
            ids[index++] = world.typeId(excludedType).value
        }
        ids.sort()
        return ExcludedFamilyKey(typeIdA, typeIdB, ids)
    }

    fun removeEntity(entity: Entity) {
        // Entity destruction affects every cache (it must be removed if it was present)
        forEachCache { it.remove(entity) }
    }

    fun addComponent(entity: Entity, typeId: ComponentTypeId, component: Any) {
        forEachRelevantCache(typeId) { it.addComponent(world, entity, typeId, component) }
    }

    fun replaceComponent(entity: Entity, typeId: ComponentTypeId, component: Any) {
        forEachRelevantCache(typeId) { it.replaceComponent(world, entity, typeId, component) }
    }

    fun removeComponent(entity: Entity, typeId: ComponentTypeId) {
        forEachRelevantCache(typeId) { it.removeComponent(world, entity, typeId) }
    }

    private fun indexFamily(cache: FamilyCache) {
        cache.types().forEach { type ->
            val id = world.typeId(type).value
            ensureCapacity(id)
            val list = familiesByComponentId[id] ?: mutableListOf<FamilyCache>().also { familiesByComponentId[id] = it }
            list.add(cache)
        }
    }

    private fun ensureCapacity(id: Int) {
        if (id >= familiesByComponentId.size) {
            familiesByComponentId = familiesByComponentId.copyOf(maxOf(id + 1, familiesByComponentId.size * 2))
        }
    }

    /** Runs on every maintained cache without allocating a combined [Sequence] -- profiling
     * showed `families.values.asSequence() + familySpecCaches.values.asSequence()` costing
     * real CPU (asSequence/SequencesKt.plus wrapper allocation) on every structural change,
     * since this runs once per entity per add/remove/destroy. */
    private inline fun forEachCache(action: (FamilyCache) -> Unit) {
        families.values.forEach(action)
        familySpecCaches.values.forEach(action)
        for (index in 0 until excludedCaches.size) {
            action(excludedCaches[index])
        }
    }

    private inline fun forEachRelevantCache(typeId: ComponentTypeId, action: (FamilyCache) -> Unit) {
        val id = typeId.value
        if (id < familiesByComponentId.size) {
            val caches = familiesByComponentId[id] ?: return
            when (caches.size) {
                0 -> return
                1 -> action(caches[0])
                else -> {
                    for (index in 0 until caches.size) {
                        action(caches[index])
                    }
                }
            }
        }
    }

    private fun <A : Any> buildFamily(
        type: KClass<A>,
        typeId: ComponentTypeId,
        exclude: Set<KClass<out Any>> = emptySet(),
    ): Family1Cache<A> {
        val store = world.store(typeId, type)
        val cache = if (exclude.isEmpty()) {
            Family1Cache(type, typeId, store)
        } else {
            Family1Cache(type, typeId, store, exclude.toSet(), buildSignatureMask(world, exclude))
        }
        store.forEach { entity, component ->
            if (!cache.isExcluded(world, entity)) {
                cache.add(entity, component)
            }
        }
        return cache
    }

    private fun <A : Any, B : Any> buildFamily(
        typeA: KClass<A>,
        typeIdA: ComponentTypeId,
        typeB: KClass<B>,
        typeIdB: ComponentTypeId,
        exclude: Set<KClass<out Any>> = emptySet(),
    ): Family2Cache<A, B> {
        val storeA = world.store(typeIdA, typeA)
        val storeB = world.store(typeIdB, typeB)
        val cache = if (exclude.isEmpty()) {
            Family2Cache(typeA, typeIdA, storeA, typeB, typeIdB, storeB)
        } else {
            Family2Cache(typeA, typeIdA, storeA, typeB, typeIdB, storeB, exclude.toSet(), buildSignatureMask(world, exclude))
        }
        fillFamily(cache, storeA, storeB)
        return cache
    }

    private fun buildFamilySpecCache(spec: FamilySpec): FamilySpecCache {
        val cache = FamilySpecCache(world, spec)
        queryCollector.collect(emptySet()).forEach { entity ->
            if (cache.matches(world, entity)) {
                cache.add(entity)
            }
        }
        return cache
    }

    private fun <A : Any, B : Any> fillFamily(
        cache: Family2Cache<A, B>,
        storeA: ComponentStore<A>,
        storeB: ComponentStore<B>,
    ) {
        if (storeA.size <= storeB.size) {
            addMatchesFromA(cache, storeA, storeB)
        } else {
            addMatchesFromB(cache, storeA, storeB)
        }
    }

    private fun <A : Any, B : Any> addMatchesFromA(
        cache: Family2Cache<A, B>,
        storeA: ComponentStore<A>,
        storeB: ComponentStore<B>,
    ) {
        storeA.forEach { entity, componentA ->
            if (!cache.isExcluded(world, entity)) {
                storeB.get(entity)?.let { componentB ->
                    cache.add(entity, componentA, componentB)
                }
            }
        }
    }

    private fun <A : Any, B : Any> addMatchesFromB(
        cache: Family2Cache<A, B>,
        storeA: ComponentStore<A>,
        storeB: ComponentStore<B>,
    ) {
        storeB.forEach { entity, componentB ->
            if (!cache.isExcluded(world, entity)) {
                storeA.get(entity)?.let { componentA ->
                    cache.add(entity, componentA, componentB)
                }
            }
        }
    }

    private companion object {
        const val SINGLE_TYPE_SENTINEL = -1
    }
}

private class ExcludedFamilyKey(
    private val typeIdA: Int,
    private val typeIdB: Int,
    private val excludeIds: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExcludedFamilyKey) return false
        return typeIdA == other.typeIdA &&
            typeIdB == other.typeIdB &&
            excludeIds.contentEquals(other.excludeIds)
    }

    override fun hashCode(): Int {
        var result = typeIdA
        result = 31 * result + typeIdB
        result = 31 * result + excludeIds.contentHashCode()
        return result
    }
}

/**
 * Internal unique identifier for a component type in a [World].
 *
 * @property value The raw integer ID.
 */
@JvmInline
value class ComponentTypeId(
    /** The raw integer ID. */
    val value: Int,
)

@JvmInline
internal value class FamilyKey(
    val packed: Long,
) {
    companion object {
        private const val SINGLE_SENTINEL = -1
        private const val INT_BITS = 32
        private const val LOW_INT_MASK = 0xFFFF_FFFFL

        fun single(type: ComponentTypeId): FamilyKey = FamilyKey(pack(type.value, SINGLE_SENTINEL))

        fun pair(typeA: ComponentTypeId, typeB: ComponentTypeId): FamilyKey = FamilyKey(pack(typeA.value, typeB.value))

        private fun pack(first: Int, second: Int): Long = (first.toLong() shl INT_BITS) or (second.toLong() and LOW_INT_MASK)
    }
}
