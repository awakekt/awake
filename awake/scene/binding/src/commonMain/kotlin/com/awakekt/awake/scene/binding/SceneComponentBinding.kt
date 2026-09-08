/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * Strongly-typed, bi-directional binding contract connecting an ECS component type [C]
 * with a serializable [SceneComponent] representation [S].
 *
 * Automatically resolves components by checking [schemaClass], and routes them to [attachTyped]
 * without requiring manual casting.
 *
 * @param C The live ECS component class type.
 * @param S The serializable [SceneComponent] representation.
 */
interface SceneComponentBinding<C : Any, S : SceneComponent> : SceneComponentResolver {
    /** Target live ECS component Kotlin class. */
    val componentClass: KClass<C>

    /** Target serializable document component Kotlin class. */
    val schemaClass: KClass<S>

    /** Optional serializer for polymorphic JSON document serialization. */
    val serializer: KSerializer<S>? get() = null

    /** Auto-resolves: checks whether [component] matches [schemaClass]. */
    override fun canResolve(component: SceneComponent): Boolean =
        schemaClass.isInstance(component)

    /** Automatically routes and safely casts [component] to [attachTyped]. */
    @Suppress("UNCHECKED_CAST")
    override fun attach(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ) {
        if (schemaClass.isInstance(component)) {
            attachTyped(world, entity, component as S, context)
        }
    }

    /**
     * Attaches [component] (already typed as [S]) onto [entity] in [world].
     * Zero manual casting needed by implementers.
     */
    fun attachTyped(
        world: World,
        entity: Entity,
        component: S,
        context: SceneResolutionContext,
    )

    /** Exports live ECS [component] into a serializable [SceneComponent] [S]. */
    fun export(world: World, entity: Entity, component: C): S?

    /** Exports [entity]'s component of type [C] from [world] if present. */
    fun exportFrom(world: World, entity: Entity): S? {
        val component = world.get(entity, componentClass) ?: return null
        return export(world, entity, component)
    }
}
