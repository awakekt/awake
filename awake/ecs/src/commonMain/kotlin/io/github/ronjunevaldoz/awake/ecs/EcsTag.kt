// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ecs

import kotlin.reflect.KClass

/**
 * Marks a payload-free component whose presence is the entire value.
 *
 * Implement tags as singleton objects. Awake stores only matching entity IDs and retains one
 * reference to the singleton, instead of storing the same reference once per entity.
 */
interface EcsTag

/** The concrete storage selected for a component type in this [World]. */
enum class ComponentStorageKind {
    /** The type has not been registered in this world. */
    Unregistered,

    /** The type is registered and its store exists, but no value has selected a strategy yet. */
    Uninitialized,

    /** A normal component sparse set with a dense payload array. */
    SparseSet,

    /** A payload-free tag sparse set containing entity IDs only. */
    TagSparseSet,
}

/** Structured storage diagnostics for one registered component type. */
data class ComponentStorageInfo(
    val type: KClass<out Any>,
    val kind: ComponentStorageKind,
    val componentCount: Int,
)

/** Storage diagnostics for the components currently attached to one entity. */
data class EntityStorageInfo(
    val entity: Entity,
    val components: List<ComponentStorageInfo>,
)
