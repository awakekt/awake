/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

/**
 * Diagnostic and introspection extensions for the ECS [World].
 *
 * Separates debugging, tooling, and serialization introspection from the core ECS state machine.
 */

/**
 * Returns the number of entities carrying a component of the specified [type].
 */
fun World.componentCount(type: KClass<out Any>): Int = components.componentCount(type)

/**
 * Returns the [ComponentStorageKind] currently selected for type [T].
 */
inline fun <reified T : Any> World.storageKind(): ComponentStorageKind = storageKind(T::class)

/**
 * Returns the [ComponentStorageKind] currently selected for the specified [type].
 */
fun World.storageKind(type: KClass<out Any>): ComponentStorageKind = components.storageKind(type)

/**
 * Returns diagnostic storage information for all registered component types.
 */
fun World.describeStorage(): List<ComponentStorageInfo> = components.storageInfo()

/**
 * Returns the component types attached to the [entity], in registration order, or an empty
 * list if the entity is not alive.
 *
 * This is the answer to "what is on this entity" -- an editor inspector, a debug overlay, a
 * serializer deciding what to write. [inspectStorage] returns the same set with storage kinds
 * and per-type counts attached, which is a question about performance rather than about the
 * entity.
 */
fun World.componentTypes(entity: Entity): List<KClass<out Any>> {
    if (!entities.isAlive(entity)) return emptyList()
    return components.componentTypes(entities.signature(entity.id))
}

/**
 * Returns diagnostic storage information for the components attached to the [entity].
 */
fun World.inspectStorage(entity: Entity): EntityStorageInfo? {
    if (!entities.isAlive(entity)) return null
    return EntityStorageInfo(entity, components.storageInfo(entities.signature(entity.id)))
}
