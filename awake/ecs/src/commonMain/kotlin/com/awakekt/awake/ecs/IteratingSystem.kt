/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

/**
 * Base [System] that caches a maintained [Family] for a [FamilySpec] and iterates every matching
 * entity per tick with zero per-frame allocations.
 *
 * The cached family automatically rebinds when this system is driven with a different [World] or
 * after [World.clear] increments [World.generation]. Override [shouldProcess] to skip an entire
 * tick in O(1), and [begin]/[end] for per-tick setup or flushing around the entity loop.
 *
 * For 1- or 2-component hot loops, prefer [Family1System] or [Family2System], which iterate packed
 * component columns directly without per-entity [World.get] lookups.
 */
abstract class IteratingSystem(
    private val spec: FamilySpec,
) : System {
    /** Builds this system's [FamilySpec] using [FamilySpecBuilder]. */
    constructor(configure: FamilySpecBuilder.() -> Unit) : this(FamilySpecBuilder().apply(configure).build())

    private var boundWorld: World? = null
    private var boundGeneration: Int = UNBOUND_GENERATION
    private var cachedFamily: Family? = null

    /** Returns the cached [Family] bound to [world], rebinding automatically after [World.clear]. */
    protected fun family(world: World): Family {
        val existing = cachedFamily
        if (existing != null && boundWorld === world && boundGeneration == world.generation) {
            return existing
        }
        return world.family(spec).also {
            boundWorld = world
            boundGeneration = world.generation
            cachedFamily = it
        }
    }

    /** Guard checked once per tick before [begin], [process], and [end] run. Return `false` to skip the tick in O(1). */
    protected open fun shouldProcess(world: World, delta: Float): Boolean = true

    /** Called once per tick before [process] runs for matching entities. */
    protected open fun begin(world: World, delta: Float) {}

    /** Called once per matching [entity] in dense family order. */
    protected abstract fun process(world: World, delta: Float, entity: Entity)

    /** Called once per tick after [process] has run for every matching entity. */
    protected open fun end(world: World, delta: Float) {}

    @PublishedApi
    internal fun resolveFamily(world: World): Family = family(world)

    @PublishedApi
    internal fun checkShouldProcess(world: World, delta: Float): Boolean = shouldProcess(world, delta)

    @PublishedApi
    internal fun invokeBegin(world: World, delta: Float) = begin(world, delta)

    @PublishedApi
    internal fun invokeEnd(world: World, delta: Float) = end(world, delta)

    /**
     * Inlines the maintained-family loop directly into a subclass's [update], keeping [action]
     * monomorphic when many [IteratingSystem] subclasses share the same JVM or Wasm runtime.
     */
    protected inline fun runTick(
        world: World,
        delta: Float,
        action: (Entity) -> Unit,
    ) {
        if (!checkShouldProcess(world, delta)) return
        val activeFamily = resolveFamily(world)
        invokeBegin(world, delta)
        activeFamily.forEach(action)
        invokeEnd(world, delta)
    }

    override fun update(world: World, delta: Float) {
        runTick(world, delta) { entity ->
            process(world, delta, entity)
        }
    }
}

/**
 * Base [System] that iterates a maintained one-component [Family1] directly over its packed
 * entity and component bags, optionally constrained by a [FamilySpec].
 *
 * Because the component bag is packed alongside the entity bag, [process] receives the component
 * directly with zero per-entity [World.get] lookups and zero per-frame allocations.
 */
abstract class Family1System<A : Any>(
    private val type: KClass<A>,
    private val filter: FamilySpec,
) : System {
    /** Creates an unfiltered system iterating every entity carrying [type]. */
    constructor(type: KClass<A>) : this(type, FamilySpec.None)

    @Deprecated(
        message = "Use Family1System(type, FamilySpec(exclude = exclude)) instead.",
        level = DeprecationLevel.HIDDEN,
    )
    constructor(type: KClass<A>, exclude: Set<KClass<out Any>> = emptySet()) :
        this(type, if (exclude.isEmpty()) FamilySpec.None else FamilySpec(exclude = exclude))

    @Deprecated(
        message = "Use Family1System(type, FamilySpec.exclude(exclude)) instead.",
        level = DeprecationLevel.HIDDEN,
    )
    constructor(type: KClass<A>, exclude: KClass<out Any>) : this(type, FamilySpec.exclude(exclude))

    private var boundWorld: World? = null
    private var boundGeneration: Int = UNBOUND_GENERATION
    private var cachedFamily: Family1<A>? = null

    /** Returns the cached [Family1] bound to [world], rebinding automatically after [World.clear]. */
    protected fun family(world: World): Family1<A> {
        val existing = cachedFamily
        if (existing != null && boundWorld === world && boundGeneration == world.generation) {
            return existing
        }
        val resolved = if (filter.isEmpty) {
            world.family(type)
        } else {
            world.family(type, filter)
        }
        boundWorld = world
        boundGeneration = world.generation
        cachedFamily = resolved
        return resolved
    }

    /** Guard checked once per tick before [begin], [process], and [end] run. Return `false` to skip the tick in O(1). */
    protected open fun shouldProcess(world: World, delta: Float): Boolean = true

    /** Called once per tick before [process] runs for matching entities. */
    protected open fun begin(world: World, delta: Float) {}

    /** Called once per matching [entity] and packed [component] in dense family order. */
    protected abstract fun process(world: World, delta: Float, entity: Entity, component: A)

    /** Called once per tick after [process] has run for every matching entity. */
    protected open fun end(world: World, delta: Float) {}

    @PublishedApi
    internal fun resolveFamily(world: World): Family1<A> = family(world)

    @PublishedApi
    internal fun checkShouldProcess(world: World, delta: Float): Boolean = shouldProcess(world, delta)

    @PublishedApi
    internal fun invokeBegin(world: World, delta: Float) = begin(world, delta)

    @PublishedApi
    internal fun invokeEnd(world: World, delta: Float) = end(world, delta)

    /**
     * Inlines the packed [Family1] loop directly into a subclass's [update], keeping [action]
     * monomorphic when many [Family1System] subclasses share the same JVM or Wasm runtime.
     */
    protected inline fun runTick(
        world: World,
        delta: Float,
        action: (Entity, A) -> Unit,
    ) {
        if (!checkShouldProcess(world, delta)) return
        val activeFamily = resolveFamily(world)
        invokeBegin(world, delta)
        activeFamily.forEach(action)
        invokeEnd(world, delta)
    }

    override fun update(world: World, delta: Float) {
        runTick(world, delta) { entity, component ->
            process(world, delta, entity, component)
        }
    }
}

/**
 * Base [System] that iterates a maintained two-component [Family2] directly over its packed
 * entity and component bags, optionally constrained by a [FamilySpec].
 *
 * Because both component columns are packed alongside the dense entity bag, [process] receives
 * both components directly with zero per-entity [World.get] lookups and zero per-frame allocations.
 */
abstract class Family2System<A : Any, B : Any>(
    private val typeA: KClass<A>,
    private val typeB: KClass<B>,
    private val filter: FamilySpec,
) : System {
    /** Creates an unfiltered system iterating every entity carrying [typeA] and [typeB]. */
    constructor(typeA: KClass<A>, typeB: KClass<B>) : this(typeA, typeB, FamilySpec.None)

    @Deprecated(
        message = "Use Family2System(typeA, typeB, FamilySpec(exclude = exclude)) instead.",
        level = DeprecationLevel.HIDDEN,
    )
    constructor(
        typeA: KClass<A>,
        typeB: KClass<B>,
        exclude: Set<KClass<out Any>> = emptySet(),
    ) : this(typeA, typeB, if (exclude.isEmpty()) FamilySpec.None else FamilySpec(exclude = exclude))

    @Deprecated(
        message = "Use Family2System(typeA, typeB, FamilySpec.exclude(exclude)) instead.",
        level = DeprecationLevel.HIDDEN,
    )
    constructor(
        typeA: KClass<A>,
        typeB: KClass<B>,
        exclude: KClass<out Any>,
    ) : this(typeA, typeB, FamilySpec.exclude(exclude))

    private var boundWorld: World? = null
    private var boundGeneration: Int = UNBOUND_GENERATION
    private var cachedFamily: Family2<A, B>? = null

    /** Returns the cached [Family2] bound to [world], rebinding automatically after [World.clear]. */
    protected fun family(world: World): Family2<A, B> {
        val existing = cachedFamily
        if (existing != null && boundWorld === world && boundGeneration == world.generation) {
            return existing
        }
        val resolved = if (filter.isEmpty) {
            world.family(typeA, typeB)
        } else {
            world.family(typeA, typeB, filter)
        }
        boundWorld = world
        boundGeneration = world.generation
        cachedFamily = resolved
        return resolved
    }

    /** Guard checked once per tick before [begin], [process], and [end] run. Return `false` to skip the tick in O(1). */
    protected open fun shouldProcess(world: World, delta: Float): Boolean = true

    /** Called once per tick before [process] runs for matching entities. */
    protected open fun begin(world: World, delta: Float) {}

    /** Called once per matching [entity] and packed components [first], [second] in dense family order. */
    protected abstract fun process(world: World, delta: Float, entity: Entity, first: A, second: B)

    /** Called once per tick after [process] has run for every matching entity. */
    protected open fun end(world: World, delta: Float) {}

    @PublishedApi
    internal fun resolveFamily(world: World): Family2<A, B> = family(world)

    @PublishedApi
    internal fun checkShouldProcess(world: World, delta: Float): Boolean = shouldProcess(world, delta)

    @PublishedApi
    internal fun invokeBegin(world: World, delta: Float) = begin(world, delta)

    @PublishedApi
    internal fun invokeEnd(world: World, delta: Float) = end(world, delta)

    /**
     * Inlines the packed [Family2] loop directly into a subclass's [update], keeping [action]
     * monomorphic when many [Family2System] subclasses share the same JVM or Wasm runtime.
     */
    protected inline fun runTick(
        world: World,
        delta: Float,
        action: (Entity, A, B) -> Unit,
    ) {
        if (!checkShouldProcess(world, delta)) return
        val activeFamily = resolveFamily(world)
        invokeBegin(world, delta)
        activeFamily.forEach(action)
        invokeEnd(world, delta)
    }

    override fun update(world: World, delta: Float) {
        runTick(world, delta) { entity, first, second ->
            process(world, delta, entity, first, second)
        }
    }
}

private const val UNBOUND_GENERATION = -1
