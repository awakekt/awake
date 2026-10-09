/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneNodeInstance
import com.awakekt.awake.scene.binding.destroy
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.binding.renderableRequests
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode

/**
 * A node [spawn] put into the running scene: its [root] and every entity under it. [despawn] takes it
 * out again.
 */
class SpawnedNode internal constructor(
    private val runtime: SceneAppLifecycleRuntime,
    private val scene: Scene,
) {
    /** The spawned node's own entity. */
    val root: Entity get() = scene.roots.single().entity

    /** The node's entity and every child's, root first. */
    val entities: List<Entity> get() = buildList { scene.roots.forEach { addTree(it) } }

    private var despawned = false

    /**
     * Destroys the node's entities and releases what its renderers held in the scene's asset library,
     * as unloading a scene does: a mesh another entity still draws stays. Despawning twice is harmless.
     */
    fun despawn() {
        if (despawned) return
        despawned = true
        runtime.session.assetLibraryOrNull()?.let { library -> scene.renderableRequests.forEach(library::releaseRenderable) }
        scene.destroy()
    }

    private fun MutableList<Entity>.addTree(node: SceneNodeInstance) {
        add(node.entity)
        node.children.forEach { addTree(it) }
    }
}

/**
 * Puts [node] and its children into the running scene, as loading the scene put the scene's own: their
 * components attached with [componentRegistry] on top of the globally registered ones, and their models
 * given renderers through the scene's asset library. Use it for whatever joins a scene after it loads:
 * a player arriving over the network, a monster a server spawns, a drop, a projectile.
 *
 * Place it with the node's own `transform`, or move its [SpawnedNode.root] after. A model the scene's
 * asset library can't resolve fails here, naming it. [SpawnedNode.despawn] takes the node out.
 */
fun SceneAppLifecycleRuntime.spawn(
    node: SceneNode,
    componentRegistry: SceneComponentRegistry = SceneComponentRegistry(),
): SpawnedNode {
    val scene = SceneDocument(nodes = listOf(node)).instantiate(world = world, componentRegistry = componentRegistry)
    scene.attachRenderableComponents { request -> spec.renderableFactory(this, request) }
    return SpawnedNode(this, scene)
}
