/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.rendering.mesh.SceneRenderableRequest
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneMeshLoad
import com.awakekt.awake.scene.runtime.SpawnedNode
import com.awakekt.awake.scene.runtime.spawn
import kotlinx.coroutines.CoroutineScope

/**
 * Puts [node] into the running scene of [project], as [spawn] does, and starts its skinned glTF models
 * playing their first clip on a loop, as [runProject] starts the scene's own.
 *
 * A glTF model [project] has loaded, or a prewarm of the node (`prewarm(node)`) has, draws on the frame
 * the node spawns. One that hasn't loaded, such as an item picked at runtime that its scene never places
 * and nothing prewarmed, loads from the project's files off the frame thread, in the scene session's
 * coroutine scope, sharing a prewarm's load of it that is under way. The node is in the scene at once,
 * without that model's renderer, which is attached, and its clip started, on the first frame after the
 * model has loaded. A model loads once, however many nodes wait for it, and stays loaded while the
 * project plays, so the next node to draw it draws at once and shares its mesh, as a node drawing a model
 * the scene loaded does. A model that can't be loaded is logged as an error naming it, and the nodes
 * that waited for it stay in the scene without it.
 *
 * Call it on the frame thread of a scene that plays [project] with [runProject], which attaches what
 * has loaded. [SpawnedNode.despawn] takes the node out, whether its models have loaded or not, and a
 * switch to another of the project's scenes despawns it with the scene it joined.
 */
fun SceneAppLifecycleRuntime.spawn(project: LoadedProject, node: SceneNode): SpawnedNode {
    val models = project.spawnedModels
    val spawned = spawn(node, SceneComponentRegistry()) { request -> !models.mustLoad(request) }
    startSkinnedAnimations(project.models, spawned.entities.toSet())
    models.load(spawned, session.coroutineScope)
    project.playing.track(spawned, world)
    return spawned
}

/**
 * The glTF models of spawned nodes that a project's scene didn't load. Each loads once, through
 * [GltfAssetResolver.loadModel], which reads it off the frame thread and shares a load already under way,
 * such as a prewarm's; [attachLoaded] polls those loads on the frame thread, where a finished one is
 * adopted into the project's resolver, and attaches the renderers that waited for it. [close] stops
 * waiting, which cancels a load nothing else waits for.
 */
internal class SpawnedModels(private val models: GltfAssetResolver) : AutoCloseable {
    // Read and written on the frame thread only.
    private val loading = mutableMapOf<String, SceneMeshLoad>()
    private val waiting = mutableListOf<SpawnedNode>()
    private var closed = false

    /** Whether [request] draws a glTF model that isn't loaded yet, so its renderer has to wait for it. */
    fun mustLoad(request: SceneRenderableRequest): Boolean {
        val mesh = request.meshRenderer.mesh
        return models.canResolveMesh(mesh) && !models.isPreloaded(models.modelPath(mesh))
    }

    /** Waits for the models [spawned] has no renderer for, starting, in [scope], each load not already under way. */
    fun load(spawned: SpawnedNode, scope: CoroutineScope) {
        val paths = spawned.unattachedRenderables.map(::modelOf).toSet()
        if (closed || paths.isEmpty()) return
        waiting += spawned
        paths.filter { it !in loading }.forEach { path -> loading[path] = models.loadModel(path, scope) }
    }

    /**
     * On the frame thread: adopts each model that has loaded since the last call, attaches the
     * renderers that waited for it and starts their clips, and logs each model that failed.
     */
    fun attachLoaded(runtime: SceneAppLifecycleRuntime) {
        // Only while something loads, so a frame with nothing to adopt polls nothing.
        if (closed || loading.isEmpty()) return
        // Pairs, not entries: an entry reads through to the map, and on Kotlin/Wasm and Native reading one after
        // `loading -= path` has changed the map throws ConcurrentModificationException.
        for ((path, load) in loading.toList()) {
            if (!load.poll()) continue
            loading -= path
            load.close()
            when (val failure = load.failure) {
                null -> attach(runtime, path)
                else -> giveUp(runtime, path, failure)
            }
        }
        // A node despawned, or one left waiting only for models that failed, waits no longer.
        if (waiting.isNotEmpty()) waiting.removeAll { node -> node.unattachedRenderables.none { modelOf(it) in loading } }
    }

    override fun close() {
        if (closed) return
        closed = true
        loading.values.forEach(SceneMeshLoad::close)
        loading.clear()
        waiting.clear()
    }

    // TooGenericExceptionCaught: one node that can't draw the model it loaded must not stop the frame.
    @Suppress("TooGenericExceptionCaught")
    private fun attach(runtime: SceneAppLifecycleRuntime, path: String) {
        val attached = mutableSetOf<Entity>()
        waiting.forEach { node ->
            try {
                attached += node.attachRenderers { modelOf(it) == path }
            } catch (failure: Exception) {
                log.error(failure) { "Spawned node ${node.label(runtime)} can't draw $path: ${failure.message}" }
            }
        }
        if (attached.isNotEmpty()) runtime.startSkinnedAnimations(models, attached)
    }

    private fun giveUp(runtime: SceneAppLifecycleRuntime, path: String, failure: Throwable) {
        val nodes = waiting.filter { node -> node.unattachedRenderables.any { modelOf(it) == path } }
        log.error(failure) {
            val names = nodes.joinToString { it.label(runtime) }.ifEmpty { "none" }
            "Can't load $path from the project (${failure.message}), so the spawned nodes that wait for it draw without it: $names"
        }
    }

    private fun modelOf(request: SceneRenderableRequest): String = models.modelPath(request.meshRenderer.mesh)

    private fun SpawnedNode.label(runtime: SceneAppLifecycleRuntime): String =
        runtime.world.get<Name>(root)?.let { "'${it.value}'" } ?: "unnamed"
}

private val log = Logger("project.spawn")
