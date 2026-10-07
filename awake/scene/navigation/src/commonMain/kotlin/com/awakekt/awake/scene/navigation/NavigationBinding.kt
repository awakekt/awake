/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.navigation

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.grid.NavGrid
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/**
 * A scene's [SceneNavigation] once it is in a world: the authored description, kept so the scene
 * exports unchanged, and the [grid] built from it that agents route over.
 */
class NavigationGrid(
    /** The authored description, kept so the scene exports unchanged. */
    val source: SceneNavigation,
) {
    /** The grid agents route over, built once from [source]. */
    val grid: NavGrid = source.toGrid()
}

/** Binds [NavigationGrid] to [SceneNavigation]. */
object NavigationBinding : SceneComponentBinding<NavigationGrid, SceneNavigation> {
    override val componentClass: KClass<NavigationGrid> = NavigationGrid::class
    override val schemaClass: KClass<SceneNavigation> = SceneNavigation::class
    override val serializer = SceneNavigation.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneNavigation,
        context: SceneResolutionContext,
    ) {
        world.add(entity, NavigationGrid(component))
    }

    override fun export(world: World, entity: Entity, component: NavigationGrid): SceneNavigation = component.source
}
