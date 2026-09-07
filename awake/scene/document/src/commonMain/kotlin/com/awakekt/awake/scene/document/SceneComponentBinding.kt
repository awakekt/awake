/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlin.reflect.KClass

/**
 * Bi-directional binding contract connecting an ECS component type [C] with a serializable [SceneComponent] type [S].
 *
 * @param C The live ECS component class type.
 * @param S The serializable [SceneComponent] representation.
 */
interface SceneComponentBinding<C : Any, S : SceneComponent> : SceneComponentResolver {
    /** Target ECS component Kotlin class. */
    val componentClass: KClass<C>

    /** Exports live ECS [component] into a serializable [SceneComponent] [S]. */
    fun export(world: World, entity: Entity, component: C): S?

    /** Exports [entity]'s component of type [C] from [world] if present. */
    fun exportFrom(world: World, entity: Entity): SceneComponent? {
        val component = world.get(entity, componentClass) ?: return null
        return export(world, entity, component)
    }
}
