/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.rendering.mesh.SceneRenderableRequest

/**
 * Live instantiated scene container.
 *
 * @property world The ECS [World] containing the instantiated scene entities.
 * @property roots Top-level instantiated scene node instances.
 * @property requests Arbitrary capability requests recorded during scene instantiation.
 */
data class Scene(
    val world: World,
    val roots: List<SceneNodeInstance>,
    val requests: List<Any> = emptyList(),
) {
    /** Filters and returns all recorded instantiation requests of type [T]. */
    inline fun <reified T : Any> requests(): List<T> = requests.filterIsInstance<T>()
}

/** Returns all renderable requests recorded in this [Scene]. */
val Scene.renderableRequests: List<SceneRenderableRequest>
    get() = requests()

/**
 * Handle to an instantiated scene node in a live scene hierarchy.
 *
 * @property name Node name identifier if named.
 * @property entity Associated live ECS [Entity].
 * @property children Nested child node handles.
 */
data class SceneNodeInstance(
    val name: String?,
    val entity: Entity,
    val children: List<SceneNodeInstance>,
)

/** Destroys all entities associated with this instantiated scene. */
fun Scene.destroy() {
    roots.forEach { it.destroyRecursively(world) }
}

private fun SceneNodeInstance.destroyRecursively(world: World) {
    children.forEach { it.destroyRecursively(world) }
    world.destroy(entity)
}
