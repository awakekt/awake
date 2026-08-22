// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ecs

import kotlin.reflect.KClass

/**
 * Incrementally maintained family storage notified by [FamilyRegistry] on structural changes.
 *
 * Implementations own their dense membership and component columns. The sealed contract keeps
 * [World] lifecycle routing independent from the public typed family views.
 */
@PublishedApi
internal sealed class FamilyCache {
    abstract fun types(): Set<KClass<out Any>>
    abstract fun remove(entity: Entity)
    abstract fun addComponent(world: World, entity: Entity, typeId: ComponentTypeId, component: Any)
    abstract fun replaceComponent(world: World, entity: Entity, typeId: ComponentTypeId, component: Any)
    abstract fun removeComponent(world: World, entity: Entity, typeId: ComponentTypeId)
}

internal const val DEFAULT_FAMILY_CAPACITY = 16
internal const val FAMILY_CAPACITY_GROWTH_FACTOR = 2
