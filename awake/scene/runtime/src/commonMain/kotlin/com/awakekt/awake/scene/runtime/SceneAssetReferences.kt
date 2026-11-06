/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.audio.SceneAudioSource
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.scene.document.ScenePrefabLink
import com.awakekt.awake.scene.particles.SceneParticleEmitter
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.scene2d.SceneParticleEmitter2D
import com.awakekt.awake.scene.scene2d.SceneSprite
import com.awakekt.awake.scene.scene2d.SceneTilemap

/**
 * The assets a scene tree draws and plays, by name, read from its components without putting
 * anything into a world: what [prewarm] gets ready before the tree is spawned.
 *
 * @property renderables Each `mesh_renderer`'s mesh and material, as [SceneAssetLibrary.resolve] builds them.
 * @property textures The textures `sprite`, `tilemap` and `particle_emitter_2d` draw through
 *   [SceneAssetLibrary.texture].
 * @property particleTextures The images 3D `particle_emitter`s show, by project path, which load through
 *   [SceneAssetLibrary.particleImages] rather than [SceneAssetLibrary.texture].
 * @property audioClips The clips `audio_source`s name, by project path, which decode through
 *   [SceneAssetLibrary.audioClips].
 * @property prefabLinks The prefab files `prefab_link`s name that were not expanded, so their assets
 *   are not listed here: expand the tree with `SceneDocument.withPrefabs` first to include them.
 */
data class SceneAssetReferences(
    val renderables: Set<SceneRenderableKey> = emptySet(),
    val textures: Set<String> = emptySet(),
    val particleTextures: Set<String> = emptySet(),
    val audioClips: Set<String> = emptySet(),
    val prefabLinks: Set<String> = emptySet(),
) {
    /** Whether this names no asset and no unexpanded prefab. */
    val isEmpty: Boolean
        get() = renderables.isEmpty() && textures.isEmpty() && particleTextures.isEmpty() && audioClips.isEmpty() && prefabLinks.isEmpty()

    /** The assets either this or [other] names. */
    operator fun plus(other: SceneAssetReferences): SceneAssetReferences = SceneAssetReferences(
        renderables = renderables + other.renderables,
        textures = textures + other.textures,
        particleTextures = particleTextures + other.particleTextures,
        audioClips = audioClips + other.audioClips,
        prefabLinks = prefabLinks + other.prefabLinks,
    )
}

/** The assets this node and its children name; see [SceneAssetReferences]. */
fun SceneNode.assetReferences(): SceneAssetReferences = SceneAssetCollector().apply { collect(this@assetReferences) }.build()

/** The assets every node of this document names; see [SceneAssetReferences]. */
fun SceneDocument.assetReferences(): SceneAssetReferences = SceneAssetCollector().apply { nodes.forEach(::collect) }.build()

/** The assets this prefab's root and its children name; see [SceneAssetReferences]. */
fun ScenePrefab.assetReferences(): SceneAssetReferences = root.assetReferences()

private class SceneAssetCollector {
    private val renderables = linkedSetOf<SceneRenderableKey>()
    private val textures = linkedSetOf<String>()
    private val particleTextures = linkedSetOf<String>()
    private val audioClips = linkedSetOf<String>()
    private val prefabLinks = linkedSetOf<String>()

    fun collect(node: SceneNode) {
        node.components.forEach { component ->
            when (component) {
                is SceneMeshRenderer -> renderables += SceneRenderableKey(component.mesh, component.material)
                is SceneSprite -> textures += component.texture
                is SceneTilemap -> textures += component.texture
                is SceneParticleEmitter2D -> if (component.texture.isNotEmpty()) textures += component.texture
                is SceneParticleEmitter -> if (component.texture.isNotBlank()) particleTextures += component.texture
                is SceneAudioSource -> if (component.clip != SceneAudioSource.UNASSIGNED) audioClips += component.clip
                // An expanded link has the prefab's root as its only child, so only a bare link is unexpanded.
                is ScenePrefabLink -> if (node.children.isEmpty()) prefabLinks += component.path
                else -> Unit
            }
        }
        node.children.forEach(::collect)
    }

    fun build() = SceneAssetReferences(renderables, textures, particleTextures, audioClips, prefabLinks)
}
