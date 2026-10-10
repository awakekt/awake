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
import com.awakekt.awake.scene.rendering.mesh.SceneRenderableRequest

/**
 * A node [spawn] put into the running scene: its [root] and every entity under it. [despawn] takes it
 * out again.
 */
class SpawnedNode internal constructor(
    private val runtime: SceneAppLifecycleRuntime,
    private val scene: Scene,
    unattached: List<SceneRenderableRequest> = emptyList(),
) {
    /** The spawned node's own entity. */
    val root: Entity get() = scene.roots.single().entity

    /** The node's entity and every child's, root first. */
    val entities: List<Entity> get() = buildList { scene.roots.forEach { addTree(it) } }

    private val waiting = unattached.toMutableList()

    /**
     * The node's renderable requests that have no renderer yet: those the [spawn] that takes
     * `attachNow` left off, less those [attachRenderers] has attached since. Empty once despawned.
     */
    val unattachedRenderables: List<SceneRenderableRequest> get() = waiting.toList()

    private var despawned = false

    /**
     * Gives each of [unattachedRenderables] that [which] accepts its renderer, through the scene's
     * renderable factory as [spawn] gives the rest, once what it draws is ready, such as a model that
     * has loaded. A request whose entity is gone is dropped. Returns the entities that got a renderer:
     * none once the node is despawned.
     */
    fun attachRenderers(which: (SceneRenderableRequest) -> Boolean): List<Entity> {
        if (despawned) return emptyList()
        val attached = mutableListOf<Entity>()
        waiting.filter(which).forEach { request ->
            waiting -= request
            if (runtime.world.isAlive(request.entity)) {
                runtime.world.attachRenderable(request, runtime.spec.renderableFactory(runtime, request))
                attached += request.entity
            }
        }
        return attached
    }

    /**
     * Destroys the node's entities and releases what its renderers held in the scene's asset library,
     * as unloading a scene does: a mesh another entity still draws stays. Despawning twice is harmless.
     */
    fun despawn() {
        if (despawned) return
        despawned = true
        waiting.clear()
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
): SpawnedNode = spawn(node, componentRegistry) { true }

/**
 * [spawn], leaving off the renderer of each request [attachNow] turns down: the node and all its
 * components are in the world at once, and [SpawnedNode.attachRenderers] gives those requests their
 * renderers once what they draw is ready, such as a model still loading. The other requests get theirs
 * here, as [spawn] gives them.
 */
fun SceneAppLifecycleRuntime.spawn(
    node: SceneNode,
    componentRegistry: SceneComponentRegistry,
    attachNow: (SceneRenderableRequest) -> Boolean,
): SpawnedNode {
    val scene = SceneDocument(nodes = listOf(node)).instantiate(world = world, componentRegistry = componentRegistry)
    val (now, later) = scene.renderableRequests.partition(attachNow)
    now.forEach { request -> world.attachRenderable(request, spec.renderableFactory(this, request)) }
    return SpawnedNode(this, scene, later)
}
