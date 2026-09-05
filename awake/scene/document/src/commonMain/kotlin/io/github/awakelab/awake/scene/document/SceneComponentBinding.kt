/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.document

import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import kotlin.reflect.KClass

interface SceneComponentBinding<C : Any, S : SceneComponent> : SceneComponentResolver {
    val componentClass: KClass<C>

    fun export(world: World, entity: Entity, component: C): S?

    fun exportFrom(world: World, entity: Entity): SceneComponent? {
        val component = world.get(entity, componentClass) ?: return null
        return export(world, entity, component)
    }
}
