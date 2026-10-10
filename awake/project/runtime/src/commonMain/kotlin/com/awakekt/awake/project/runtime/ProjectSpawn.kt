/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.rendering.mesh.SceneRenderableRequest
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SpawnedNode
import com.awakekt.awake.scene.runtime.spawn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Puts [node] into the running scene of [project], as [spawn] does, and starts its skinned glTF models
 * playing their first clip on a loop, as [runProject] starts the scene's own.
 *
 * A glTF model [project] hasn't loaded, such as an item picked at runtime that its scene never places,
 * loads from the project's files off the frame thread. The node is in the scene at once, without that
 * model's renderer, which is attached, and its clip started, on the first frame after the model has
 * loaded. A model loads once, however many nodes wait for it, and stays loaded while the project
 * plays, so the next node to draw it draws at once and shares its mesh, as a node drawing a model the
 * scene loaded does. A model that can't be loaded is logged as an error naming it, and the nodes that
 * waited for it stay in the scene without it.
 *
 * Call it on the frame thread of a scene that plays [project] with [runProject], which attaches what
 * has loaded. [SpawnedNode.despawn] takes the node out, whether its models have loaded or not.
 */
fun SceneAppLifecycleRuntime.spawn(project: LoadedProject, node: SceneNode): SpawnedNode {
    val models = project.spawnedModels
    val spawned = spawn(node, SceneComponentRegistry()) { request -> !models.mustLoad(request) }
    startSkinnedAnimations(project.models, spawned.entities.toSet())
    models.load(spawned)
    return spawned
}

/**
 * Gives spawned nodes the models [project] loads for them, in a frame system that runs before the
 * scene's own, so a model that has loaded draws, and animates, that frame. Loading stops when the scene
 * does.
 */
internal fun SceneAppDsl.attachSpawnedModels(project: LoadedProject) {
    frameSystem("spawned-models") { SpawnedModelSystem(this, project.spawnedModels) }
    onDispose { project.spawnedModels.close() }
}

private class SpawnedModelSystem(
    private val runtime: SceneAppLifecycleRuntime,
    private val models: SpawnedModels,
) : System {
    override fun update(world: World, delta: Float) = models.attachLoaded(runtime)
}

/**
 * The glTF models of spawned nodes that a project's scene didn't load. Each loads once, off the frame
 * thread, into a resolver of its own that nothing else reads; [attachLoaded] adopts it into the
 * project's resolver on the frame thread, where meshes resolve, and attaches the renderers that waited
 * for it. [close] stops what is still loading.
 */
internal class SpawnedModels(
    private val models: GltfAssetResolver,
    private val files: AssetSource,
) : AutoCloseable {
    private val loads = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val finished = Channel<FinishedLoad>(Channel.UNLIMITED)

    // Read and written on the frame thread only.
    private val loading = mutableSetOf<String>()
    private val waiting = mutableListOf<SpawnedNode>()
    private var closed = false

    /** Whether [request] draws a glTF model that isn't loaded yet, so its renderer has to wait for it. */
    fun mustLoad(request: SceneRenderableRequest): Boolean {
        val mesh = request.meshRenderer.mesh
        return models.canResolveMesh(mesh) && !models.isPreloaded(models.modelPath(mesh))
    }

    /** Waits for the models [spawned] has no renderer for, starting each load not already under way. */
    fun load(spawned: SpawnedNode) {
        val paths = spawned.unattachedRenderables.map(::modelOf).toSet()
        if (closed || paths.isEmpty()) return
        waiting += spawned
        paths.filter(loading::add).forEach { path ->
            loads.launch { finished.trySend(FinishedLoad(path, read(path))) }
        }
    }

    /**
     * On the frame thread: adopts each model that has loaded since the last call, attaches the
     * renderers that waited for it and starts their clips, and logs each model that failed.
     */
    fun attachLoaded(runtime: SceneAppLifecycleRuntime) {
        if (closed) return
        while (true) {
            val load = finished.tryReceive().getOrNull() ?: break
            loading -= load.path
            load.result.fold(
                onSuccess = { staged ->
                    models.adopt(load.path, staged)
                    attach(runtime, load.path)
                },
                onFailure = { failure -> giveUp(runtime, load.path, failure) },
            )
        }
        // A node despawned, or one left waiting only for models that failed, waits no longer.
        waiting.removeAll { node -> node.unattachedRenderables.none { modelOf(it) in loading } }
    }

    override fun close() {
        if (closed) return
        closed = true
        loads.cancel()
        finished.close()
        loading.clear()
        waiting.clear()
    }

    // TooGenericExceptionCaught: an asset source or the parser reports a bad model in its own exception type.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun read(path: String): Result<GltfAssetResolver> = try {
        val staged = GltfAssetResolver()
        staged.setAssetSource(files)
        staged.preload(path)
        Result.success(staged)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
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

    private class FinishedLoad(val path: String, val result: Result<GltfAssetResolver>)
}

private val log = Logger("project.spawn")
