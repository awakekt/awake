/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.project.AwakeProjectManifest
import com.awakekt.awake.project.AwakeProjectValidator
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.runtime.SpawnedNode

/**
 * One of a project's scenes as it loaded to play: the document at [path], the glTF models its
 * `mesh_renderer`s draw, what its capabilities read, and its physics world when it has bodies or
 * characters, which [close] destroys.
 */
internal class ProjectScene(
    val path: String,
    val document: SceneDocument,
    val models: GltfAssetResolver,
    val physics: PhysicsWorld?,
    val content: SceneContent,
    files: AssetSource,
) : AutoCloseable {
    /** The models spawned nodes draw that the scene didn't load, loaded as they are spawned. */
    val spawnedModels = SpawnedModels(models, files)

    // The nodes spawned into the scene, despawned when a switch leaves it. Read and written on the frame thread.
    private val spawned = mutableListOf<SpawnedNode>()
    private var pruneAt = SPAWNED_PRUNE_MIN
    private var closed = false

    /** Keeps [node] to despawn when a switch leaves this scene, forgetting the nodes already gone from [world]. */
    fun track(node: SpawnedNode, world: World) {
        if (spawned.size >= pruneAt) {
            spawned.removeAll { !world.isAlive(it.root) }
            pruneAt = maxOf(SPAWNED_PRUNE_MIN, spawned.size * 2)
        }
        spawned += node
    }

    /** Despawns every node spawned into this scene, giving back what their renderers held. */
    fun despawnAll() {
        spawned.forEach(SpawnedNode::despawn)
        spawned.clear()
    }

    /** Destroys the physics world, if the scene had one, and stops loading models. Closing twice is harmless. */
    override fun close() {
        if (closed) return
        closed = true
        physics?.destroy()
        spawnedModels.close()
    }
}

/**
 * Reads the project's scene at [path], as [loadProject] reads its entry scene, for a switch to play
 * it: decoded and checked with the project's registry, its models and its capabilities' content
 * loaded, and its physics world made. Throws [IllegalArgumentException] for a path that leaves the
 * project and for everything [loadProject] refuses in a scene.
 */
internal suspend fun LoadedProject.loadScene(path: String): ProjectScene {
    require(AwakeProjectValidator.isSafeProjectPath(path)) { "'$path' is not a project-relative path" }
    return loadProjectScene(path, manifest, files, componentRegistry, installedCapabilities(capabilities), hasRenderer, physicsWorld)
}

/** The scene at [path], read with what its project was loaded with, as [loadProject] reads the entry scene. */
@Suppress("LongParameterList") // Each is one thing the project was loaded with, which every scene of it loads with too.
internal suspend fun loadProjectScene(
    path: String,
    manifest: AwakeProjectManifest,
    files: AssetSource,
    registry: SceneComponentRegistry?,
    installed: List<SceneCapability>,
    hasRenderer: Boolean,
    physicsWorld: (suspend () -> PhysicsWorld)?,
): ProjectScene {
    val scene = decodeScene(path, files, registry)
    warnUnlistedTags(manifest, scene, path)
    val models = GltfAssetResolver().apply { setAssetSource(files) }
    if (hasRenderer) {
        scene.nodes.flatMap { it.meshNames() }
            .filter(models::canResolveMesh)
            .map(models::modelPath)
            .distinct()
            .forEach { models.preload(it) }
    }
    // Every file is read before the physics world exists, so a missing model, or a load cancelled
    // part way, leaves no world behind that nothing would destroy.
    val content = loadContent(scene, files, installed, path, SceneContent.Builder(emptyMap(), hasRenderer))
    val physics = if (PhysicsCapability.needsPhysics(scene)) {
        requireNotNull(physicsWorld) { "$path has physics bodies or characters; pass a physicsWorld factory" }()
    } else {
        null
    }
    return ProjectScene(path, scene, models, physics, content, files)
}

private fun SceneNode.meshNames(): List<String> =
    components.filterIsInstance<SceneMeshRenderer>().map { it.mesh } + children.flatMap { it.meshNames() }

/** How many spawned nodes a scene keeps before it first forgets the ones already despawned. */
private const val SPAWNED_PRUNE_MIN = 32
