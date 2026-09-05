/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.fixture

import io.github.awakelab.awake.asset.gltf.firstSkinnedAsset
import io.github.awakelab.awake.asset.gltf.toAnimationLibrary
import io.github.awakelab.awake.core.animation.AnimationPlayer
import io.github.awakelab.awake.core.math.Aabb
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.render.renderer.CullMode
import io.github.awakelab.awake.scene.document.SceneDocument
import io.github.awakelab.awake.scene.document.SceneLoader
import io.github.awakelab.awake.scene.document.SceneMeshRenderer
import io.github.awakelab.awake.scene.document.fromWorld
import io.github.awakelab.awake.scene.rendering.animation.Animator
import io.github.awakelab.awake.scene.rendering.animation.SkinnedPose
import io.github.awakelab.awake.scene.rendering.mesh.MeshBounds
import io.github.awakelab.awake.scene.rendering.mesh.MeshRenderer
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.studio.plugins.GltfAssetResolver

/**
 * Manages authored scenes in Studio. Dynamically discovers scenes from [StudioSceneRegistry]
 * and binds skeletal animation for any entity whose mesh resolves to a skinned glTF model.
 */
internal class StudioFixture {
    private val loadedDocuments = mutableMapOf<String, SceneDocument>()
    private var activeDescriptor: StudioSceneDescriptor = StudioSceneRegistry.defaultScene
    private val boundsByEntity = mutableMapOf<Entity, Aabb>()
    private val authoredRenderers = mutableMapOf<Entity, SceneMeshRenderer>()

    val currentDocument: SceneDocument
        get() = loadedDocuments[activeDescriptor.id]
            ?: loadedDocuments.values.firstOrNull()
            ?: error("No scenes preloaded")

    val currentSceneTitle: String get() = activeDescriptor.title

    val currentDescriptor: StudioSceneDescriptor get() = activeDescriptor

    fun boundsOf(entity: Entity): Aabb? = boundsByEntity[entity]

    fun copyEntityMetadata(source: Entity, target: Entity) {
        authoredRenderers[source]?.let { authoredRenderers[target] = it }
        boundsByEntity[source]?.let { boundsByEntity[target] = it }
    }

    fun authoredRendererOf(entity: Entity, renderer: MeshRenderer): SceneMeshRenderer {
        val authored = checkNotNull(authoredRenderers[entity]) {
            "Cannot save $entity: it has a MeshRenderer this fixture never instantiated."
        }
        return authored.copy(cullMode = renderer.cullMode.toSceneCullMode())
    }

    suspend fun preload() {
        preloadRegistry()
        if (loadedDocuments.isEmpty()) {
            StudioSceneRegistry.resetToDefaults()
            preloadRegistry()
        }
    }

    private suspend fun preloadRegistry() {
        StudioSceneRegistry.all.forEach { desc ->
            runCatching {
                val doc = SceneLoader.loadFromResource(desc.path)
                loadedDocuments[desc.id] = doc
                doc.nodes.forEach { node ->
                    node.components.forEach { comp ->
                        if (comp is SceneMeshRenderer && GltfAssetResolver.instance.canResolveMesh(
                                comp.mesh,
                            )
                        ) {
                            GltfAssetResolver.instance.preload(comp.mesh)
                        }
                    }
                }
            }
        }
    }

    fun load(runtime: SceneAppLifecycleRuntime) {
        val doc = loadedDocuments[activeDescriptor.id]
            ?: loadedDocuments.values.firstOrNull()
            ?: error("No scenes preloaded")
        load(runtime, doc)
    }

    fun selectScene(runtime: SceneAppLifecycleRuntime, descriptor: StudioSceneDescriptor) {
        activeDescriptor = descriptor
        val doc = loadedDocuments[descriptor.id]
        if (doc != null) {
            load(runtime, doc)
        }
    }

    fun load(runtime: SceneAppLifecycleRuntime, source: SceneDocument) {
        runtime.sceneManager.close()
        val instance = runtime.sceneManager.switchTo(source)
        val library = runtime.requireAssetLibrary()
        boundsByEntity.clear()
        authoredRenderers.clear()

        instance.renderableRequests.forEach { request ->
            val resolvedRenderer = library.resolve(runtime, request)
            runtime.world.add(request.entity, resolvedRenderer)
            resolvedRenderer.mesh.localBounds?.let {
                runtime.world.add(
                    request.entity,
                    MeshBounds(it),
                )
            }
            authoredRenderers[request.entity] = request.meshRenderer
            StudioFixtureBounds[request.meshRenderer.mesh]?.let {
                boundsByEntity[request.entity] = it
            }

            // Automatically bind skeletal animation for ANY entity referencing a skinned glTF mesh
            val gltfScene = GltfAssetResolver.instance.getLoadedScene(request.meshRenderer.mesh)
            val skinnedAsset = gltfScene?.firstSkinnedAsset()
            if (skinnedAsset != null) {
                val animLib = gltfScene.toAnimationLibrary()
                val player = AnimationPlayer(animLib)
                val firstClip = animLib.clips.keys.firstOrNull()
                if (firstClip != null) player.play(firstClip)

                runtime.world.add(request.entity, Animator(player, skinnedAsset.skin))
                runtime.world.add(
                    request.entity,
                    SkinnedPose(player.update(0f).jointPalette(skinnedAsset.skin)),
                )
            }
        }
    }

    fun exportFrom(runtime: SceneAppLifecycleRuntime): SceneDocument =
        SceneLoader.fromWorld(runtime.world, name = currentDocument.name)
}

private fun CullMode.toSceneCullMode(): SceneMeshRenderer.CullMode = when (this) {
    CullMode.None -> SceneMeshRenderer.CullMode.None
    CullMode.Back -> SceneMeshRenderer.CullMode.Back
    CullMode.Front -> SceneMeshRenderer.CullMode.Front
}
