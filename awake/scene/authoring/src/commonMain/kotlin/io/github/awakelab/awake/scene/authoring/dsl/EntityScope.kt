/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.authoring.dsl

import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.ecs.ensure
import kotlin.reflect.KClass

/**
 * Component-agnostic scoped configurator for an ECS entity and its child hierarchy.
 *
 * Provides methods to attach components (`with`), configure/ensure components (`configure`),
 * and spawn child entities (`entity`).
 *
 * @property world The backing [World] store owning this entity.
 * @property entity The current [Entity] handle being configured.
 */
@AwakeSceneDsl
class EntityScope internal constructor(
    val world: World,
    val entity: Entity,
    private val childBuilder: SceneBuilder,
) {
    /**
     * Attaches a component instance directly to the entity.
     *
     * @param component The component instance to attach.
     */
    fun with(component: Any) {
        @Suppress("UNCHECKED_CAST")
        world.add(entity, component::class as KClass<Any>, component)
    }

    /**
     * Ensures an instance of [T] exists on the entity and applies [setup] to it.
     *
     * @param T The component type to ensure on the entity.
     * @param factory Constructor supplier for [T].
     * @param setup Lambda block to initialize or mutate the component.
     */
    inline fun <reified T : Any> configure(
        noinline factory: () -> T,
        crossinline setup: T.() -> Unit,
    ) {
        world.ensure(entity, factory).setup()
    }

    /**
     * Spawns a child entity parented to this entity.
     *
     * @param name The optional descriptive name for the child entity.
     * @param block The configuration block executed within an [EntityScope] for the child.
     * @return The newly spawned child [Entity] handle.
     */
    fun entity(
        name: String? = null,
        block: EntityScope.() -> Unit = {},
    ): Entity = childBuilder.entity(name, block)
}
