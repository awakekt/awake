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
 * after [World.clear] increments [World.generation]. Override [begin] and [end] for per-tick setup
 * or flushing around the entity loop.
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

    /** Called once per tick before [process] runs for matching entities. */
    protected open fun begin(world: World, delta: Float) {}

    /** Called once per matching [entity] in dense family order. */
    protected abstract fun process(world: World, delta: Float, entity: Entity)

    /** Called once per tick after [process] has run for every matching entity. */
    protected open fun end(world: World, delta: Float) {}

    final override fun update(world: World, delta: Float) {
        val activeFamily = family(world)
        begin(world, delta)
        activeFamily.forEach { entity ->
            process(world, delta, entity)
        }
        end(world, delta)
    }
}

/**
 * Base [System] that iterates a maintained one-component [Family1] directly over its packed
 * entity and component bags, optionally excluding entities carrying [exclude] types.
 *
 * Because the component bag is packed alongside the entity bag, [process] receives the component
 * directly with zero per-entity [World.get] lookups and zero per-frame allocations.
 */
abstract class Family1System<A : Any>(
    private val type: KClass<A>,
    private val exclude: Set<KClass<out Any>> = emptySet(),
) : System {
    /** Creates a system iterating [type] while incrementally excluding entities carrying [exclude]. */
    constructor(type: KClass<A>, exclude: KClass<out Any>) : this(type, setOf(exclude))

    private var boundWorld: World? = null
    private var boundGeneration: Int = UNBOUND_GENERATION
    private var cachedFamily: Family1<A>? = null

    /** Returns the cached [Family1] bound to [world], rebinding automatically after [World.clear]. */
    protected fun family(world: World): Family1<A> {
        val existing = cachedFamily
        if (existing != null && boundWorld === world && boundGeneration == world.generation) {
            return existing
        }
        val resolved = if (exclude.isEmpty()) {
            world.family(type)
        } else {
            world.familyExcluding(type, exclude)
        }
        boundWorld = world
        boundGeneration = world.generation
        cachedFamily = resolved
        return resolved
    }

    /** Called once per tick before [process] runs for matching entities. */
    protected open fun begin(world: World, delta: Float) {}

    /** Called once per matching [entity] and packed [component] in dense family order. */
    protected abstract fun process(world: World, delta: Float, entity: Entity, component: A)

    /** Called once per tick after [process] has run for every matching entity. */
    protected open fun end(world: World, delta: Float) {}

    final override fun update(world: World, delta: Float) {
        val activeFamily = family(world)
        begin(world, delta)
        activeFamily.forEach { entity, component ->
            process(world, delta, entity, component)
        }
        end(world, delta)
    }
}

/**
 * Base [System] that iterates a maintained two-component [Family2] directly over its packed
 * entity and component bags, optionally excluding entities carrying [exclude] types.
 *
 * Because both component columns are packed alongside the dense entity bag, [process] receives
 * both components directly with zero per-entity [World.get] lookups and zero per-frame allocations.
 */
abstract class Family2System<A : Any, B : Any>(
    private val typeA: KClass<A>,
    private val typeB: KClass<B>,
    private val exclude: Set<KClass<out Any>> = emptySet(),
) : System {
    /** Creates a system iterating [typeA] and [typeB] while incrementally excluding entities carrying [exclude]. */
    constructor(
        typeA: KClass<A>,
        typeB: KClass<B>,
        exclude: KClass<out Any>,
    ) : this(typeA, typeB, setOf(exclude))

    private var boundWorld: World? = null
    private var boundGeneration: Int = UNBOUND_GENERATION
    private var cachedFamily: Family2<A, B>? = null

    /** Returns the cached [Family2] bound to [world], rebinding automatically after [World.clear]. */
    protected fun family(world: World): Family2<A, B> {
        val existing = cachedFamily
        if (existing != null && boundWorld === world && boundGeneration == world.generation) {
            return existing
        }
        val resolved = if (exclude.isEmpty()) {
            world.family(typeA, typeB)
        } else {
            world.familyExcluding(typeA, typeB, exclude)
        }
        boundWorld = world
        boundGeneration = world.generation
        cachedFamily = resolved
        return resolved
    }

    /** Called once per tick before [process] runs for matching entities. */
    protected open fun begin(world: World, delta: Float) {}

    /** Called once per matching [entity] and packed components [first], [second] in dense family order. */
    protected abstract fun process(world: World, delta: Float, entity: Entity, first: A, second: B)

    /** Called once per tick after [process] has run for every matching entity. */
    protected open fun end(world: World, delta: Float) {}

    final override fun update(world: World, delta: Float) {
        val activeFamily = family(world)
        begin(world, delta)
        activeFamily.forEach { entity, first, second ->
            process(world, delta, entity, first, second)
        }
        end(world, delta)
    }
}

private const val UNBOUND_GENERATION = -1
