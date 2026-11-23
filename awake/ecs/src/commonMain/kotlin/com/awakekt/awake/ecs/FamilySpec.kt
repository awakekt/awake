/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

/**
 * An arbitrary-arity family predicate: match entities that have **all** of [all], **any**
 * (at least one) of [one] (if non-empty), and **none** of [exclude]. This is the same
 * `all()/one()/exclude()` shape Ashley's ECS uses, adopted deliberately for the API
 * ergonomics (one generic mechanism instead of hand-writing `Family1Cache`, `Family2Cache`,
 * `Family3Cache`, ... for every arity) -- but backed by [FamilySpecCache]'s own
 * sparse-set + incremental-maintenance approach, not Ashley's listener-based internals
 * (which this project's own benchmark showed to be the slowest of the four ECS libraries
 * measured; see `docs/ecs-benchmark-scorecard.md`).
 *
 * Data class (not a data-less builder result) specifically so it works as a `Map` key in
 * `World`'s family cache -- two `family { ... }` calls describing the same predicate return
 * the same underlying [FamilySpecCache] instance, same convention [FamilyKey] already
 * uses for [Family1]/[Family2].
 */
data class FamilySpec(
    /** Every entity must have **all** of these component types. */
    val all: Set<KClass<out Any>> = emptySet(),
    /** Every entity must have **at least one** of these component types (if non-empty). */
    val one: Set<KClass<out Any>> = emptySet(),
    /** Every entity must have **none** of these component types. */
    val exclude: Set<KClass<out Any>> = emptySet(),
) {
    /** Whether this spec imposes no component constraints. */
    val isEmpty: Boolean get() = all.isEmpty() && one.isEmpty() && exclude.isEmpty()

    /** Combines this spec's `all`, `one`, and `exclude` sets with [other]'s into a single spec. */
    operator fun plus(other: FamilySpec): FamilySpec = when {
        isEmpty -> other
        other.isEmpty -> this
        else -> FamilySpec(
            all = all + other.all,
            one = one + other.one,
            exclude = exclude + other.exclude,
        )
    }

    /** Factory helpers and the [None] sentinel for constructing [FamilySpec] values. */
    companion object {
        /** Empty predicate sentinel imposing no constraints. */
        val None: FamilySpec = FamilySpec()

        /** Matches only entities carrying every one of [types]. */
        fun all(vararg types: KClass<out Any>): FamilySpec =
            if (types.isEmpty()) None else FamilySpec(all = types.toSet())

        /** Matches only entities carrying at least one of [types]. */
        fun one(vararg types: KClass<out Any>): FamilySpec =
            if (types.isEmpty()) None else FamilySpec(one = types.toSet())

        /** Excludes entities carrying any of [types]. */
        fun exclude(vararg types: KClass<out Any>): FamilySpec =
            if (types.isEmpty()) None else FamilySpec(exclude = types.toSet())
    }
}

/** Builds a [FamilySpec] via `world.family { all(A::class); exclude(B::class) }` or [familySpec]. */
class FamilySpecBuilder @PublishedApi internal constructor() {
    private val all = mutableSetOf<KClass<out Any>>()
    private val one = mutableSetOf<KClass<out Any>>()
    private val exclude = mutableSetOf<KClass<out Any>>()

    /** Every entity must have **all** of these [types]. */
    fun all(vararg types: KClass<out Any>): FamilySpecBuilder = apply { all += types }

    /** Every entity must have **at least one** of these [types]. */
    fun one(vararg types: KClass<out Any>): FamilySpecBuilder = apply { one += types }

    /** Every entity must have **none** of these [types]. */
    fun exclude(vararg types: KClass<out Any>): FamilySpecBuilder = apply { exclude += types }

    @PublishedApi
    internal fun build(): FamilySpec = if (all.isEmpty() && one.isEmpty() && exclude.isEmpty()) {
        FamilySpec.None
    } else {
        FamilySpec(all.toSet(), one.toSet(), exclude.toSet())
    }
}

/** Builds an immutable [FamilySpec] via DSL (`familySpec { all(...); exclude(...) }`). */
inline fun familySpec(configure: FamilySpecBuilder.() -> Unit): FamilySpec =
    FamilySpecBuilder().apply(configure).build()

/**
 * The public, read-only view of a maintained arbitrary-arity family. Unlike [Family1]/
 * [Family2], this only exposes matched [Entity] handles, not their component values
 * directly -- an arbitrary number of heterogeneous component types can't be returned as a
 * typed tuple in Kotlin without per-arity codegen, so callers read components back via
 * `world.get<T>(entity)` (an O(1) sparse-set lookup, not free but not linear either). Prefer
 * [Family1]/[Family2] for the common 1-2-component hot path, where direct typed component
 * access avoids that extra lookup per entity per frame; reach for this when a query
 * genuinely needs 3+ types or `one`/`exclude` semantics neither of those support.
 */
class Family @PublishedApi internal constructor(
    @PublishedApi internal val cache: FamilySpecCache,
) {
    /** Number of entities currently matching this family. */
    val size: Int get() = cache.size

    /** Iterates each matching entity without allocating an intermediate list. */
    inline fun forEach(block: (Entity) -> Unit) {
        cache.forEach(block)
    }
}

/**
 * Maintains the entity set matching a [FamilySpec] incrementally: [World]'s structural-
 * change hooks call [addComponent]/[removeComponent] here directly instead of this cache
 * re-scanning every entity on every query, the same incremental-maintenance principle
 * [Family1Cache]/[Family2Cache] already use. Backed by [EntityIndexMap] for O(1)
 * membership add/remove, same as those two.
 */
@PublishedApi
internal class FamilySpecCache(
    private val world: World,
    private val spec: FamilySpec,
) : FamilyCache() {
    @PublishedApi
    internal var entities = LongArray(DEFAULT_FAMILY_CAPACITY)

    @PublishedApi
    internal var count: Int = 0
    private val sparse = EntityIndexMap()
    private val compiled = CompiledFamilySpec(world, spec)

    override fun types(): Set<KClass<out Any>> = compiled.allTypes()

    val size: Int get() = count

    @PublishedApi
    internal inline fun forEach(block: (Entity) -> Unit) {
        val localEntities = entities
        val localCount = count
        for (index in 0 until localCount) {
            block(Entity(localEntities[index]))
        }
    }

    /** Whether [entity] currently satisfies this family's `all`/`one`/`exclude` predicate,
     * read fresh from [world] (not from this cache's own membership) -- used both to decide
     * initial membership when the cache is first built and to re-check membership after a
     * structural change. */
    fun matches(world: World, entity: Entity): Boolean = compiled.matches(world, entity)

    fun add(entity: Entity) {
        if (indexOf(entity) >= 0) {
            return
        }
        ensureCapacity(count + 1)
        sparse.set(entity.id, count)
        entities[count] = entity.packed
        count += 1
    }

    override fun remove(entity: Entity) {
        removeAt(indexOf(entity))
    }

    override fun addComponent(world: World, entity: Entity, typeId: ComponentTypeId, component: Any) {
        sync(world, entity)
    }

    override fun replaceComponent(world: World, entity: Entity, typeId: ComponentTypeId, component: Any) {
        // Replacing a component's value (not adding/removing it) can't change which
        // all/one/exclude branch it satisfies -- the type is present either way -- so
        // membership can't change here. Nothing to do.
    }

    override fun removeComponent(world: World, entity: Entity, typeId: ComponentTypeId) {
        sync(world, entity)
    }

    /** Re-checks [entity] against [matches] and adds/removes it to match -- called after a
     * structural change to a component type this family's spec actually references. */
    private fun sync(world: World, entity: Entity) {
        val currentIndex = indexOf(entity)
        val shouldBeMember = matches(world, entity)
        if (shouldBeMember && currentIndex < 0) {
            add(entity)
        } else if (!shouldBeMember && currentIndex >= 0) {
            removeAt(currentIndex)
        }
    }

    private fun indexOf(entity: Entity): Int {
        val denseIndex = sparse.get(entity.id)
        return if (denseIndex >= 0 && denseIndex < count) {
            denseIndex
        } else {
            -1
        }
    }

    private fun removeAt(index: Int) {
        if (index < 0) {
            return
        }
        val removedEntityId = entities[index].toInt()
        val lastIndex = count - 1
        if (index != lastIndex) {
            val lastEntity = entities[lastIndex]
            entities[index] = lastEntity
            sparse.set(lastEntity.toInt(), index)
        }
        count -= 1
        sparse.remove(removedEntityId)
    }

    private fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity <= entities.size) {
            return
        }
        val newCapacity = maxOf(requiredCapacity, entities.size * CAPACITY_GROWTH_FACTOR)
        entities = entities.copyOf(newCapacity)
    }

    private companion object {
        const val DEFAULT_FAMILY_CAPACITY = 16
        const val CAPACITY_GROWTH_FACTOR = 2
    }
}
