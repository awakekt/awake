/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring.dsl

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Structural orchestrator for building an ECS scene graph.
 *
 * Provides the root entry point for declaring entities. To configure components on an entity
 * or add child entities, use the [EntityScope] trailing lambda passed to [entity].
 */
@AwakeSceneDsl
class SceneBuilder internal constructor(
    private val world: World,
    val parentEntity: Entity? = null,
) {
    /**
     * Spawns an entity with an optional name and configures its components and children.
     *
     * @param name The optional descriptive name for the entity.
     * @param block The configuration block executed within an [EntityScope].
     * @return The newly spawned [Entity] handle.
     */
    fun entity(
        name: String? = null,
        block: EntityScope.() -> Unit = {},
    ): Entity {
        val currentEntity = world.create()

        if (name != null) {
            world.add(currentEntity, Name(name))
        }

        val childBuilder = SceneBuilder(world, parentEntity = currentEntity)
        val scope = EntityScope(world, currentEntity, childBuilder)
        scope.block()

        if (parentEntity != null) {
            val transform = world.get<Transform>(currentEntity) ?: world.add(currentEntity, Transform())
            transform?.parent = parentEntity
        }

        return currentEntity
    }
}

/**
 * Entry point for building a scene against a [World].
 */
fun World.scene(block: SceneBuilder.() -> Unit) {
    SceneBuilder(this).block()
}

/**
 * Spawns an entity directly in this [World] using the [EntityScope] DSL.
 *
 * @param name The optional descriptive name for the entity.
 * @param block The configuration block executed within an [EntityScope].
 * @return The newly spawned [Entity] handle.
 */
fun World.entity(
    name: String? = null,
    block: EntityScope.() -> Unit = {},
): Entity = SceneBuilder(this).entity(name, block)
