/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions")

package com.awakekt.awake.ecs

import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

// ---------------------------------------------------------------------------
// Ergonomic Utilities & Shortcuts
// ---------------------------------------------------------------------------

/**
 * Ensures the [entity] has a component of type [T]. If missing, [factory] is called
 * to create and add it. Returns the existing or new component instance.
 */
inline fun <reified T : Any> World.ensure(entity: Entity, factory: () -> T): T =
    getOrPut(entity, factory)

/**
 * Returns the component of type [T] attached to the entity, or computes and adds [defaultValue]
 * if missing.
 */
inline fun <reified T : Any> World.getOrPut(entity: Entity, defaultValue: () -> T): T {
    val existing = get<T>(entity)
    if (existing != null) return existing
    val new = defaultValue()
    add(entity, new)
    return new
}

/**
 * Returns the component of type [T] attached to [entity], throwing [IllegalStateException] if absent.
 */
inline fun <reified T : Any> World.require(entity: Entity): T =
    get<T>(entity) ?: error("Required component ${T::class.simpleName} is missing on $entity!")

/**
 * Atomically updates a component of type [T] on [entity] using [transform].
 *
 * Useful for immutable data classes: `world.update<Health>(player) { it.copy(hp = it.hp - 10) }`.
 * Returns the newly updated component, or null if no component was attached.
 */
inline fun <reified T : Any> World.update(entity: Entity, transform: (T) -> T): T? {
    val current = get<T>(entity) ?: return null
    val updated = transform(current)
    add(entity, updated)
    return updated
}

/**
 * In-place mutates a mutable component of type [T] on [entity] via [block].
 *
 * Useful for mutable components or POJOs: `world.mutate<Score>(player) { it.points += 100 }`.
 * Returns the mutated component, or null if no component was attached.
 */
inline fun <reified T : Any> World.mutate(entity: Entity, block: (T) -> Unit): T? {
    val current = get<T>(entity) ?: return null
    block(current)
    add(entity, current)
    return current
}

/**
 * Returns the single component of type [T] in the world, or null if zero or more than one exists.
 *
 * Avoids allocating a query list; optimal for global/singleton scene components (Skybox, Fog, etc.).
 */
inline fun <reified T : Any> World.singleOrNull(): T? {
    var result: T? = null
    var count = 0
    queryEach<T> { _, comp ->
        if (count == 0) {
            result = comp
        }
        count++
    }
    return if (count == 1) result else null
}

/**
 * Returns the single entity found carrying component [T], or null if zero or more than one exists.
 */
inline fun <reified T : Any> World.singleEntityOrNull(): Entity? {
    var result: Entity? = null
    var count = 0
    queryEach<T> { entity, _ ->
        if (count == 0) {
            result = entity
        }
        count++
    }
    return if (count == 1) result else null
}

/**
 * Returns the first component of type [T] found in the world, or null if none exists.
 */
inline fun <reified T : Any> World.firstOrNull(): T? {
    var result: T? = null
    queryEach<T> { _, comp ->
        if (result == null) {
            result = comp
        }
    }
    return result
}

/**
 * Returns the first entity found carrying component [T], or null if none exists.
 */
inline fun <reified T : Any> World.firstEntityOrNull(): Entity? {
    var result: Entity? = null
    queryEach<T> { entity, _ ->
        if (result == null) {
            result = entity
        }
    }
    return result
}

/**
 * Returns true if [entity] has both component [A] and component [B].
 */
@JvmName("hasAll2")
inline fun <reified A : Any, reified B : Any> World.hasAll(entity: Entity): Boolean =
    has<A>(entity) && has<B>(entity)

/**
 * Returns true if [entity] has components [A], [B], and [C].
 */
@JvmName("hasAll3")
inline fun <reified A : Any, reified B : Any, reified C : Any> World.hasAll(entity: Entity): Boolean =
    has<A>(entity) && has<B>(entity) && has<C>(entity)

/**
 * Returns true if [entity] has either component [A] or component [B].
 */
@JvmName("hasAny2")
inline fun <reified A : Any, reified B : Any> World.hasAny(entity: Entity): Boolean =
    has<A>(entity) || has<B>(entity)

// ---------------------------------------------------------------------------
// Fluent Entity Creation DSL
// ---------------------------------------------------------------------------

/**
 * Scope for constructing and configuring an [Entity] with initial components.
 */
class EntityBuilder(
    val world: World,
    val entity: Entity,
) {
    /** Adds [component] to the entity being created. */
    inline fun <reified T : Any> add(component: T): EntityBuilder {
        world.add(entity, component)
        return this
    }
}

/**
 * Creates a new entity and configures it within [builder].
 *
 * ```kotlin
 * val sun = world.create {
 *     add(Transform())
 *     add(Light(type = Light.Type.Directional))
 * }
 * ```
 */
inline fun World.create(builder: EntityBuilder.() -> Unit): Entity {
    val entity = create()
    EntityBuilder(this, entity).builder()
    return entity
}

// ---------------------------------------------------------------------------
// Entity Handle Extensions
// ---------------------------------------------------------------------------

/** Fetches component [T] attached to this entity from [world]. */
inline fun <reified T : Any> Entity.get(world: World): T? = world.get<T>(this)

/** Fetches component [T] attached to this entity from [world], throwing if missing. */
inline fun <reified T : Any> Entity.require(world: World): T = world.require<T>(this)

/** Returns true if this entity has component [T] in [world]. */
inline fun <reified T : Any> Entity.has(world: World): Boolean = world.has<T>(this)

/** Attaches [component] to this entity in [world]. */
inline fun <reified T : Any> Entity.add(world: World, component: T): T? = world.add(this, component)

/** Removes component [T] from this entity in [world]. */
inline fun <reified T : Any> Entity.remove(world: World): T? = world.remove<T>(this)

/** Destroys this entity in [world]. */
fun Entity.destroy(world: World): Boolean = world.destroy(this)

// ---------------------------------------------------------------------------
// Property Delegate
// ---------------------------------------------------------------------------

/**
 * Property delegate that fetches a component on demand from the ECS world.
 *
 * Each property access is a fresh lookup, so it reads like a local but costs a `KClass` hash
 * plus a sparse-set probe every time -- `spin.radians = spin.radians + x` is three lookups.
 * Bind it once outside a hot loop, or use [World.get] directly.
 */
class ComponentDelegate<T : Any>(
    @PublishedApi internal val world: World,
    @PublishedApi internal val entity: Entity,
    @PublishedApi internal val type: KClass<T>,
) {
    /** Returns the current component instance or throws if missing. */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
        world.get(entity, type) ?: error("Component ${type.simpleName} missing on $entity!")

    /** Replaces the current component with the provided [value]. */
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        world.add(entity, type, value)
    }
}

/**
 * Property delegate that fetches a component on demand from the ECS world.
 */
inline fun <reified T : Any> World.component(entity: Entity): ComponentDelegate<T> =
    ComponentDelegate(this, entity, T::class)
