/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

/**
 * A component class resolved once, for code that looks the same type up on many entities every frame.
 *
 * `world.get<T>(entity)` finds the type's id in that world from the class each time it runs: a hash
 * on Kotlin/Native and Kotlin/Wasm, a per-class table probe on the JVM. A handle carries its class's
 * process-wide index, so `world.get(entity, handle)` reads the world's id from an array instead. One
 * handle serves every [World], on any thread, and stays valid across [World.clear].
 *
 * Hold it in a field or a top-level `val` and pass it on the hot path:
 * ```
 * private val transformType = componentType<Transform>()
 *
 * world.get(entity, transformType)?.position?.set(x, y, z)
 * ```
 *
 * Two handles for one class are equal.
 *
 * @property type The component class.
 */
class ComponentType<T : Any> internal constructor(val type: KClass<T>) {
    /** The class's process-wide slot in every world's id array; see [componentTypeIndexOf]. */
    internal val index: Int = componentTypeIndexOf(componentTypeKeyOf(type))

    override fun equals(other: Any?): Boolean = other is ComponentType<*> && other.index == index

    override fun hashCode(): Int = index

    override fun toString(): String = "ComponentType(${type.simpleName})"
}

/** The [ComponentType] handle for [type]. */
fun <T : Any> componentType(type: KClass<T>): ComponentType<T> = ComponentType(type)

/** The [ComponentType] handle for [T]. */
inline fun <reified T : Any> componentType(): ComponentType<T> = componentType(T::class)

/** The ID [type] has in this world, registering it first if no entity has carried it yet. */
fun World.typeId(type: ComponentType<*>): ComponentTypeId = components.typeIdOrNull(type) ?: typeId(type.type)

/** The [type] component of [entity], or `null` when it has none; like `get<T>` without resolving the class. */
fun <T : Any> World.get(entity: Entity, type: ComponentType<T>): T? {
    val typeId = components.typeIdOrNull(type) ?: return null
    return getInternal(entity, typeId)
}

/** Whether [entity] has a [type] component; like `has<T>` without resolving the class. */
fun World.has(entity: Entity, type: ComponentType<*>): Boolean {
    val typeId = components.typeIdOrNull(type) ?: return false
    return hasInternal(entity, typeId)
}

/** Adds [component] to [entity] as its [type] component, returning the one it replaced, if any. */
fun <T : Any> World.add(entity: Entity, type: ComponentType<T>, component: T): T? {
    val typeId = typeId(type)
    return addInternal(entity, typeId, components.store(typeId, type.type), component)
}

/** Removes and recycles [entity]'s [type] component, returning it, or `null` when it had none. */
fun <T : Any> World.remove(entity: Entity, type: ComponentType<T>): T? {
    val typeId = components.typeIdOrNull(type) ?: return null
    return removeInternal(entity, typeId)
}

/** The maintained [Family1] of [type]; the same view [World.family] returns for its class. */
fun <A : Any> World.family(type: ComponentType<A>): Family1<A> {
    val typeId = components.typeIdOrNull(type)
    if (typeId != null) cachedFamily<A>(typeId)?.let { return it }
    return family(type.type)
}

/** The maintained [Family2] of [typeA] and [typeB]; the same view [World.family] returns for their classes. */
fun <A : Any, B : Any> World.family(typeA: ComponentType<A>, typeB: ComponentType<B>): Family2<A, B> {
    val typeIdA = components.typeIdOrNull(typeA)
    val typeIdB = components.typeIdOrNull(typeB)
    if (typeIdA != null && typeIdB != null) cachedFamily<A, B>(typeIdA, typeIdB)?.let { return it }
    return family(typeA.type, typeB.type)
}
