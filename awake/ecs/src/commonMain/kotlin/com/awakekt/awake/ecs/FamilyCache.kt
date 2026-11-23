/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

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
internal val EMPTY_SIGNATURE_MASK = LongArray(0)

/** Encodes [types] as a signature-shaped bit set, one 64-bit word per 64 component type IDs. */
internal fun buildSignatureMask(world: World, types: Set<KClass<out Any>>): LongArray {
    if (types.isEmpty()) return EMPTY_SIGNATURE_MASK
    var maxId = -1
    for (type in types) {
        val id = world.typeId(type).value
        if (id > maxId) maxId = id
    }
    val mask = LongArray((maxId ushr 6) + 1)
    for (type in types) {
        val id = world.typeId(type).value
        mask[id ushr 6] = mask[id ushr 6] or (1L shl (id and 63))
    }
    return mask
}
